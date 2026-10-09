package net.riftwatch.boot.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

final class Journal {
    static final String FILE = "journal.json";
    static final String STAGING = "staging";
    static final String BACKUP = "backup";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    record Move(String path, boolean staged, boolean backup) {
    }

    enum Outcome {
        NONE,
        FINISHED,
        ROLLED_BACK
    }

    private final Path root;
    private final Path updaterDir;
    private final String from;
    private final String to;
    private final List<Move> moves;

    Journal(Path root, String from, String to, List<Move> moves) {
        this.root = root;
        this.updaterDir = root.resolve(InstancePaths.UPDATER);
        this.from = from;
        this.to = to;
        this.moves = List.copyOf(moves);
    }

    String from() {
        return from;
    }

    String to() {
        return to;
    }

    List<Move> moves() {
        return moves;
    }

    Path staging() {
        return updaterDir.resolve(STAGING);
    }

    Path backupRoot() {
        return updaterDir.resolve(BACKUP).resolve(from);
    }

    Path staged(String path) {
        return InstancePaths.resolve(staging(), path);
    }

    Path backup(String path) {
        return InstancePaths.resolve(backupRoot(), path);
    }

    Path target(String path) {
        return InstancePaths.resolve(root, path);
    }

    void write() throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("from", from);
        json.addProperty("to", to);
        JsonArray entries = new JsonArray();
        for (Move move : moves) {
            JsonObject entry = new JsonObject();
            entry.addProperty("path", move.path());
            entry.addProperty("staged", move.staged());
            entry.addProperty("backup", move.backup());
            entries.add(entry);
        }
        json.add("moves", entries);
        FileOps.writeAtomically(updaterDir.resolve(FILE), (GSON.toJson(json) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    static Optional<Journal> read(Path root) throws IOException {
        byte[] content = FileOps.readIfExists(root.resolve(InstancePaths.UPDATER).resolve(FILE));
        if (content == null) {
            return Optional.empty();
        }
        JsonObject json = JsonParser.parseString(new String(content, StandardCharsets.UTF_8)).getAsJsonObject();
        String from = json.get("from").getAsString();
        String to = json.get("to").getAsString();
        if (PackVersion.parse(from).isEmpty() || PackVersion.parse(to).isEmpty()) {
            throw new IOException("Update journal names invalid versions");
        }
        List<Move> moves = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("moves")) {
            JsonObject move = element.getAsJsonObject();
            String path = move.get("path").getAsString();
            if (!InstancePaths.safe(path)) {
                throw new IOException("Update journal holds an unsafe path: " + path);
            }
            moves.add(new Move(path, move.get("staged").getAsBoolean(), move.get("backup").getAsBoolean()));
        }
        return Optional.of(new Journal(root, from, to, moves));
    }

    void execute() throws IOException {
        for (Move move : moves) {
            Path target = target(move.path());
            if (move.backup() && Files.exists(target) && !Files.exists(backup(move.path()))) {
                FileOps.move(target, backup(move.path()));
            }
            if (move.staged() && Files.exists(staged(move.path()))) {
                FileOps.move(staged(move.path()), target);
            }
        }
    }

    boolean canFinish() {
        for (Move move : moves) {
            if (move.staged() && !Files.exists(staged(move.path())) && !Files.exists(target(move.path()))) {
                return false;
            }
        }
        return true;
    }

    void rollBack() throws IOException {
        List<Move> reversed = new ArrayList<>(moves);
        Collections.reverse(reversed);
        for (Move move : reversed) {
            Path target = target(move.path());
            Path backup = backup(move.path());
            if (move.backup() && Files.exists(backup)) {
                FileOps.move(backup, target);
            } else if (move.staged() && !move.backup()) {
                Files.deleteIfExists(target);
            }
        }
    }

    void finish() throws IOException {
        Files.deleteIfExists(updaterDir.resolve(FILE));
        FileOps.deleteTree(staging());
    }

    static Outcome recover(Path root) throws IOException {
        Optional<Journal> found;
        try {
            found = read(root);
        } catch (RuntimeException e) {
            throw new IOException("Update journal unreadable: " + UpdateException.describe(e), e);
        }
        if (found.isEmpty()) {
            return Outcome.NONE;
        }
        Journal journal = found.get();
        if (journal.canFinish()) {
            journal.execute();
            journal.finish();
            UpdateLog.LOG.warn("Interrupted update from {} to {} finished", journal.from(), journal.to());
            return Outcome.FINISHED;
        }
        journal.rollBack();
        journal.finish();
        UpdateLog.LOG.warn("Interrupted update from {} to {} rolled back, staging was gone", journal.from(), journal.to());
        return Outcome.ROLLED_BACK;
    }
}
