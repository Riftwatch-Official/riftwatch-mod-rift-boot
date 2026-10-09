package net.riftwatch.boot.update;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

final class PackMarker {
    static final String PATH = "config/riftwatch/pack.json";

    private PackMarker() {
    }

    static Optional<PackVersion> read(Path root) {
        try {
            return parse(FileOps.readIfExists(root.resolve(PATH)));
        } catch (IOException e) {
            UpdateLog.LOG.warn("Pack marker {} unreadable: {}", PATH, UpdateException.describe(e));
            return Optional.empty();
        }
    }

    static Optional<PackVersion> parse(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        try {
            return PackVersion.parse(JsonParser.parseString(new String(content, StandardCharsets.UTF_8)).getAsJsonObject().get("version").getAsString());
        } catch (RuntimeException e) {
            UpdateLog.LOG.warn("Pack marker {} unreadable: {}", PATH, UpdateException.describe(e));
            return Optional.empty();
        }
    }
}
