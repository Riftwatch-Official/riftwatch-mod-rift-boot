package net.riftwatch.boot.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

record UpdateState(PackVersion version, String boot, Map<String, String> files, Set<String> playerRemoved) {
    static final String FILE = "state.json";
    private static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    UpdateState {
        files = Map.copyOf(files);
        playerRemoved = Set.copyOf(playerRemoved);
    }

    static Optional<UpdateState> read(Path updaterDir) {
        Path file = updaterDir.resolve(FILE);
        try {
            byte[] content = FileOps.readIfExists(file);
            if (content == null) {
                return Optional.empty();
            }
            JsonObject json = JsonParser.parseString(new String(content, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.get("format").getAsInt() != FORMAT) {
                return Optional.empty();
            }
            Optional<PackVersion> version = PackVersion.parse(json.get("version").getAsString());
            if (version.isEmpty()) {
                return Optional.empty();
            }
            Map<String, String> files = new TreeMap<>();
            json.getAsJsonObject("files").entrySet().forEach(entry -> files.put(entry.getKey(), entry.getValue().getAsString()));
            Set<String> removed = new TreeSet<>();
            for (JsonElement path : json.getAsJsonArray("playerRemoved")) {
                removed.add(path.getAsString());
            }
            String boot = json.has("boot") ? json.get("boot").getAsString() : "";
            return Optional.of(new UpdateState(version.get(), boot, files, removed));
        } catch (IOException | RuntimeException e) {
            UpdateLog.LOG.warn("Updater state {} unreadable, starting without it: {}", file, UpdateException.describe(e));
            return Optional.empty();
        }
    }

    void write(Path updaterDir) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("version", version.text());
        json.addProperty("boot", boot);
        JsonObject fileHashes = new JsonObject();
        new TreeMap<>(files).forEach(fileHashes::addProperty);
        json.add("files", fileHashes);
        JsonArray removed = new JsonArray();
        new TreeSet<>(playerRemoved).forEach(removed::add);
        json.add("playerRemoved", removed);
        FileOps.writeAtomically(updaterDir.resolve(FILE), (GSON.toJson(json) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
