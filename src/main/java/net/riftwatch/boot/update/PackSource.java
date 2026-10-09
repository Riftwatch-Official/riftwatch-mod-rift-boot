package net.riftwatch.boot.update;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class PackSource {
    static final URI DEFAULT_BASE = URI.create("https://riftwatch.net/downloads/modpack/");
    static final String SOURCE_PROPERTY = "riftwatch.updater.source";
    private static final Set<String> HOSTS = Set.of(
            "riftwatch.net",
            "cdn.modrinth.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com");
    private static final int MAX_REDIRECTS = 5;
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration CHECK_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DOWNLOAD_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SMALL_TIMEOUT = Duration.ofSeconds(20);
    private static final long LATEST_LIMIT = 64 * 1024;
    private static final long SIGNATURE_LIMIT = 4096;
    private static final String TEMP_SUFFIX = ".part";
    private static final long UNKNOWN_SIZE_LIMIT = 256L << 20;

    record Latest(PackVersion version, URI pack, String sha512, long size, URI signature, Optional<String> minecraft, Optional<String> neoforge) {
    }

    private final URI base;
    private final Set<String> hosts;
    private final boolean overridden;
    private final String userAgent;
    private final HttpClient checkClient;
    private final HttpClient downloadClient;

    PackSource(URI base, boolean overridden, String userAgent) {
        this.base = base;
        this.overridden = overridden;
        this.userAgent = userAgent;
        Set<String> allowed = new HashSet<>(HOSTS);
        if (overridden && base.getHost() != null) {
            allowed.add(base.getHost().toLowerCase(Locale.ROOT));
        }
        this.hosts = Set.copyOf(allowed);
        this.checkClient = client(CHECK_CONNECT_TIMEOUT);
        this.downloadClient = client(DOWNLOAD_CONNECT_TIMEOUT);
    }

    static PackSource fromSystem(String userAgent) {
        String override = System.getProperty(SOURCE_PROPERTY);
        if (override == null || override.isBlank()) {
            return new PackSource(DEFAULT_BASE, false, userAgent);
        }
        URI base = URI.create(override.trim().endsWith("/") ? override.trim() : override.trim() + "/");
        UpdateLog.LOG.warn("Update source overridden by -D{}: {}", SOURCE_PROPERTY, base);
        return new PackSource(base, true, userAgent);
    }

    private static HttpClient client(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .build();
    }

    URI base() {
        return base;
    }

    Latest latest() throws UpdateException {
        URI uri = base.resolve("latest.json");
        byte[] body = fetch(checkClient, uri, LATEST_LIMIT, CHECK_TIMEOUT);
        try {
            JsonObject json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            PackVersion version = PackVersion.parse(json.get("version").getAsString())
                    .orElseThrow(() -> new UpdateException("latest.json has no valid version"));
            URI pack = base.resolve(json.get("url").getAsString());
            URI signature = json.has("signature") ? base.resolve(json.get("signature").getAsString()) : URI.create(pack + ".sig");
            return new Latest(
                    version,
                    pack,
                    json.get("sha512").getAsString().toLowerCase(Locale.ROOT),
                    json.get("size").getAsLong(),
                    signature,
                    optional(json, "minecraft"),
                    optional(json, "neoforge"));
        } catch (RuntimeException e) {
            throw new UpdateException("latest.json unreadable: " + UpdateException.describe(e), e);
        }
    }

    private static Optional<String> optional(JsonObject json, String name) {
        return json.has(name) && json.get(name).isJsonPrimitive() ? Optional.of(json.get(name).getAsString()) : Optional.empty();
    }

    URI packUri(PackVersion version) {
        return base.resolve("Riftwatch-" + version.text() + ".mrpack");
    }

    byte[] signature(URI uri) throws UpdateException {
        return fetch(downloadClient, uri, SIGNATURE_LIMIT, SMALL_TIMEOUT);
    }

    boolean allowed(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!hosts.contains(host)) {
            return false;
        }
        if (scheme.equals("https")) {
            return true;
        }
        return scheme.equals("http") && overridden && host.equalsIgnoreCase(base.getHost());
    }

    void download(List<URI> candidates, Path target, String sha512, long size, Runnable activity) throws UpdateException {
        UpdateException last = null;
        for (URI uri : candidates) {
            try {
                downloadOne(uri, target, sha512, size, activity);
                return;
            } catch (UpdateException e) {
                last = e;
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        }
        throw last != null ? last : new UpdateException("No download for " + target.getFileName());
    }

    private void downloadOne(URI uri, Path target, String sha512, long size, Runnable activity) throws UpdateException {
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        HttpResponse<InputStream> response = send(downloadClient, uri, null);
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = FileOps.sha512Digest();
            long total = 0;
            try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new UpdateException("Download of " + target.getFileName() + " cancelled");
                    }
                    total += read;
                    if (total > (size >= 0 ? size : UNKNOWN_SIZE_LIMIT)) {
                        throw new UpdateException(target.getFileName() + " is larger than the pack says");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                    activity.run();
                }
            }
            if (size >= 0 && total != size) {
                throw new UpdateException(target.getFileName() + " has " + total + " bytes, the pack says " + size);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (sha512 != null && !actual.equals(sha512)) {
                throw new UpdateException(target.getFileName() + " has a wrong sha512");
            }
            FileOps.move(temp, target);
        } catch (IOException e) {
            throw new UpdateException("Download of " + target.getFileName() + " from " + uri.getHost() + " failed: " + UpdateException.describe(e), e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                UpdateLog.LOG.debug("Temporary download {} not deleted", temp, e);
            }
        }
    }

    private byte[] fetch(HttpClient client, URI uri, long limit, Duration timeout) throws UpdateException {
        HttpResponse<InputStream> response = send(client, uri, timeout);
        try (InputStream input = response.body()) {
            byte[] content = input.readNBytes((int) limit + 1);
            if (content.length > limit) {
                throw new UpdateException(uri.getPath() + " is too large");
            }
            return content;
        } catch (IOException e) {
            throw new UpdateException(uri.getHost() + " broke off: " + UpdateException.describe(e), e);
        }
    }

    private HttpResponse<InputStream> send(HttpClient client, URI uri, Duration timeout) throws UpdateException {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!allowed(current)) {
                throw new UpdateException("Download host not allowed: " + current.getScheme() + "://" + current.getHost());
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(current).GET().header("User-Agent", userAgent);
            if (timeout != null) {
                request.timeout(timeout);
            }
            HttpResponse<InputStream> response;
            try {
                response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException e) {
                throw new UpdateException(current.getHost() + " unreachable: " + UpdateException.describe(e), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UpdateException("Request to " + current.getHost() + " interrupted", e);
            }
            int status = response.statusCode();
            if (status >= 300 && status < 400 && status != 304) {
                Optional<String> location = response.headers().firstValue("location");
                close(response);
                if (location.isEmpty()) {
                    throw new UpdateException(current.getHost() + " redirected without a target");
                }
                try {
                    current = current.resolve(location.get());
                } catch (IllegalArgumentException e) {
                    throw new UpdateException(current.getHost() + " redirected to an invalid address", e);
                }
                continue;
            }
            if (status != 200) {
                close(response);
                throw new UpdateException(current.getHost() + " answered " + status + " for " + current.getPath());
            }
            return response;
        }
        throw new UpdateException("Too many redirects for " + uri.getHost());
    }

    private static void close(HttpResponse<InputStream> response) {
        try {
            response.body().close();
        } catch (IOException e) {
            UpdateLog.LOG.debug("Response body close failed", e);
        }
    }
}
