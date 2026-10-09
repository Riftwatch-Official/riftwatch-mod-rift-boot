package net.riftwatch.boot.update;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class TestServer implements AutoCloseable {
    private final HttpServer server;
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    volatile boolean down;

    TestServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    URI base() {
        return URI.create(url("/modpack/"));
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    void put(String path, byte[] content) {
        files.put(path, content);
    }

    byte[] get(String path) {
        return files.get(path);
    }

    int hits(String prefix) {
        return hits.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix)).mapToInt(entry -> entry.getValue().get()).sum();
    }

    void serveFiles(TestPack pack) {
        pack.files.forEach((path, file) -> put(URI.create(file.url()).getPath(), file.content()));
    }

    void publish(TestPack pack, PrivateKey key, boolean latest) throws Exception {
        byte[] archive = pack.build();
        String name = "Riftwatch-" + pack.version + ".mrpack";
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update(archive);
        put("/modpack/" + name, archive);
        put("/modpack/" + name + ".sig", signer.sign());
        serveFiles(pack);
        if (latest) {
            String json = """
                    {"version": "%s", "file": "%s", "url": "%s", "size": %d, "sha512": "%s", "published_at": "2026-10-09T12:00:00Z"}
                    """.formatted(pack.version, name, url("/modpack/" + name), archive.length, FileOps.sha512(archive));
            put("/modpack/latest.json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        hits.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();
        byte[] body = down ? null : files.get(path);
        if (body == null) {
            exchange.sendResponseHeaders(down ? 503 : 404, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
