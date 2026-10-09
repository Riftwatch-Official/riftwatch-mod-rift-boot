package net.riftwatch.boot.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class TestPack {
    record File(byte[] content, String url, String client) {
    }

    final String version;
    final Map<String, byte[]> overrides = new LinkedHashMap<>();
    final Map<String, File> files = new LinkedHashMap<>();

    TestPack(String version) {
        this.version = version;
        override(PackMarker.PATH, "{\"version\": \"" + version + "\"}\n");
    }

    TestPack override(String path, String content) {
        overrides.put(path, content.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    TestPack override(String path, byte[] content) {
        overrides.put(path, content);
        return this;
    }

    TestPack policy(String mode) {
        return override(UpdatePolicy.PATH, """
                {"mode": "%s", "classes": [
                  {"path": "config/fancymenu/**", "class": "pack"},
                  {"path": "config/DistantHorizons.toml", "class": "settings"}
                ]}
                """.formatted(mode));
    }

    TestPack file(String path, String content, String url) {
        return file(path, content.getBytes(StandardCharsets.UTF_8), url, "required");
    }

    TestPack file(String path, byte[] content, String url, String client) {
        files.put(path, new File(content, url, client));
        return this;
    }

    TestPack without(String path) {
        files.remove(path);
        overrides.remove(path);
        return this;
    }

    TestPack copy(String newVersion) {
        TestPack copy = new TestPack(newVersion);
        overrides.forEach((path, content) -> {
            if (!path.equals(PackMarker.PATH)) {
                copy.overrides.put(path, content);
            }
        });
        copy.files.putAll(files);
        return copy;
    }

    byte[] build() {
        JsonObject index = new JsonObject();
        index.addProperty("formatVersion", 1);
        index.addProperty("game", "minecraft");
        index.addProperty("versionId", version);
        index.addProperty("name", "Riftwatch");
        JsonObject dependencies = new JsonObject();
        dependencies.addProperty("minecraft", "1.21.1");
        dependencies.addProperty("neoforge", "21.1.255");
        index.add("dependencies", dependencies);
        JsonArray entries = new JsonArray();
        files.forEach((path, file) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("path", path);
            JsonObject hashes = new JsonObject();
            hashes.addProperty("sha1", "0");
            hashes.addProperty("sha512", FileOps.sha512(file.content()));
            entry.add("hashes", hashes);
            JsonObject env = new JsonObject();
            env.addProperty("client", file.client());
            env.addProperty("server", "required");
            entry.add("env", env);
            JsonArray downloads = new JsonArray();
            downloads.add(file.url());
            entry.add("downloads", downloads);
            entry.addProperty("fileSize", file.content().length);
            entries.add(entry);
        });
        index.add("files", entries);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(PackArchive.INDEX));
            zip.write(index.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Map.Entry<String, byte[]> override : overrides.entrySet()) {
                zip.putNextEntry(new ZipEntry("overrides/" + override.getKey()));
                zip.write(override.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    void installInto(Path root) throws IOException {
        for (Map.Entry<String, File> file : files.entrySet()) {
            if (!file.getValue().client().equals("unsupported")) {
                write(root.resolve(file.getKey()), file.getValue().content());
            }
        }
        for (Map.Entry<String, byte[]> override : overrides.entrySet()) {
            write(root.resolve(override.getKey()), override.getValue());
        }
    }

    private static void write(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }
}
