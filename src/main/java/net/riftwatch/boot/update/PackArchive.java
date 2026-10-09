package net.riftwatch.boot.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class PackArchive implements AutoCloseable {
    static final String INDEX = "modrinth.index.json";
    private static final List<String> OVERRIDE_ROOTS = List.of("overrides/", "client-overrides/");
    private static final Pattern SHA512 = Pattern.compile("[0-9a-f]{128}");
    private static final long MAX_INDEX_BYTES = 16L << 20;

    record Entry(String path, String sha512, long size, List<URI> downloads, boolean optional, String zipName) {
        boolean override() {
            return zipName != null;
        }
    }

    private final Path file;
    private final ZipFile zip;
    private final PackVersion version;
    private final Map<String, String> dependencies;
    private final Map<String, Entry> entries;

    private PackArchive(Path file, ZipFile zip, PackVersion version, Map<String, String> dependencies, Map<String, Entry> entries) {
        this.file = file;
        this.zip = zip;
        this.version = version;
        this.dependencies = dependencies;
        this.entries = entries;
    }

    static PackArchive open(Path file) throws UpdateException {
        ZipFile zip = null;
        try {
            zip = new ZipFile(file.toFile());
            JsonObject index = JsonParser.parseString(new String(read(zip, INDEX, MAX_INDEX_BYTES), StandardCharsets.UTF_8)).getAsJsonObject();
            if (index.get("formatVersion").getAsInt() != 1) {
                throw new UpdateException("Unknown pack format in " + file.getFileName());
            }
            PackVersion version = PackVersion.parse(index.get("versionId").getAsString())
                    .orElseThrow(() -> new UpdateException("Pack " + file.getFileName() + " has no valid version"));
            Map<String, String> dependencies = new LinkedHashMap<>();
            if (index.has("dependencies")) {
                index.getAsJsonObject("dependencies").entrySet().forEach(dependency -> dependencies.put(dependency.getKey(), dependency.getValue().getAsString()));
            }
            Map<String, Entry> entries = new TreeMap<>();
            for (JsonElement element : index.getAsJsonArray("files")) {
                Entry entry = indexEntry(element.getAsJsonObject());
                if (entry != null) {
                    entries.put(entry.path(), entry);
                }
            }
            for (String root : OVERRIDE_ROOTS) {
                for (Enumeration<? extends ZipEntry> all = zip.entries(); all.hasMoreElements();) {
                    ZipEntry zipEntry = all.nextElement();
                    if (zipEntry.isDirectory() || !zipEntry.getName().startsWith(root)) {
                        continue;
                    }
                    String path = zipEntry.getName().substring(root.length());
                    if (!InstancePaths.safe(path)) {
                        throw new UpdateException("Pack " + file.getFileName() + " holds an unsafe path: " + path);
                    }
                    entries.put(path, new Entry(path, hash(zip, zipEntry), zipEntry.getSize(), List.of(), false, zipEntry.getName()));
                }
            }
            return new PackArchive(file, zip, version, Collections.unmodifiableMap(dependencies), Collections.unmodifiableMap(entries));
        } catch (UpdateException e) {
            closeQuietly(zip);
            throw e;
        } catch (IOException | RuntimeException e) {
            closeQuietly(zip);
            throw new UpdateException("Pack " + file.getFileName() + " unreadable: " + UpdateException.describe(e), e);
        }
    }

    private static Entry indexEntry(JsonObject file) throws UpdateException {
        String path = file.get("path").getAsString();
        if (!InstancePaths.safe(path)) {
            throw new UpdateException("Pack index holds an unsafe path: " + path);
        }
        String client = "required";
        if (file.has("env") && file.getAsJsonObject("env").has("client")) {
            client = file.getAsJsonObject("env").get("client").getAsString();
        }
        if (client.equals("unsupported")) {
            return null;
        }
        String sha512 = file.getAsJsonObject("hashes").get("sha512").getAsString().toLowerCase(Locale.ROOT);
        if (!SHA512.matcher(sha512).matches()) {
            throw new UpdateException("Pack index has no valid sha512 for " + path);
        }
        List<URI> downloads = new ArrayList<>();
        for (JsonElement url : file.getAsJsonArray("downloads")) {
            downloads.add(URI.create(url.getAsString()));
        }
        if (downloads.isEmpty()) {
            throw new UpdateException("Pack index has no download for " + path);
        }
        return new Entry(path, sha512, file.get("fileSize").getAsLong(), List.copyOf(downloads), client.equals("optional"), null);
    }

    private static String hash(ZipFile zip, ZipEntry entry) throws IOException {
        MessageDigest digest = FileOps.sha512Digest();
        byte[] buffer = new byte[1 << 16];
        try (InputStream input = zip.getInputStream(entry)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] read(ZipFile zip, String name, long limit) throws IOException, UpdateException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new UpdateException(name + " is missing in the pack");
        }
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] content = input.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, limit + 1));
            if (content.length > limit) {
                throw new UpdateException(name + " is too large");
            }
            return content;
        }
    }

    Path file() {
        return file;
    }

    PackVersion version() {
        return version;
    }

    Optional<String> dependency(String name) {
        return Optional.ofNullable(dependencies.get(name));
    }

    Map<String, Entry> entries() {
        return entries;
    }

    Optional<Entry> entry(String path) {
        return Optional.ofNullable(entries.get(path));
    }

    byte[] read(String path) throws UpdateException {
        Entry entry = entries.get(path);
        if (entry == null || !entry.override()) {
            return null;
        }
        try {
            byte[] content = read(zip, entry.zipName(), MAX_INDEX_BYTES * 4);
            if (!FileOps.sha512(content).equals(entry.sha512())) {
                throw new UpdateException(path + " changed inside " + file.getFileName());
            }
            return content;
        } catch (IOException e) {
            throw new UpdateException(path + " unreadable in " + file.getFileName(), e);
        }
    }

    UpdatePolicy policy() throws UpdateException {
        byte[] json = read(UpdatePolicy.PATH);
        return json == null ? UpdatePolicy.MISSING : UpdatePolicy.parse(json);
    }

    @Override
    public void close() {
        closeQuietly(zip);
    }

    private static void closeQuietly(ZipFile zip) {
        if (zip == null) {
            return;
        }
        try {
            zip.close();
        } catch (IOException e) {
            UpdateLog.LOG.debug("Pack archive close failed", e);
        }
    }
}
