package net.riftwatch.boot.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class UpdatePolicy {
    static final String PATH = "config/riftwatch/updater.json";
    static final UpdatePolicy MISSING = new UpdatePolicy(Mode.OBSERVE, List.of());
    private static final List<Rule> PLAYER_RULES = Stream.of(
            "options.txt",
            "servers.dat",
            "config/fml.toml",
            PlayerSwitch.PATH,
            "config/fancymenu/user_variables.db",
            "shaderpacks/*.txt")
            .map(glob -> new Rule(PathGlob.of(glob), FileClass.PLAYER))
            .toList();
    private static final List<Rule> PACK_RULES = Stream.of(PATH, PackMarker.PATH)
            .map(glob -> new Rule(PathGlob.of(glob), FileClass.PACK))
            .toList();

    enum Mode {
        OBSERVE,
        APPLY,
        OFF
    }

    enum FileClass {
        PACK,
        SETTINGS,
        PLAYER
    }

    record Rule(PathGlob glob, FileClass fileClass) {
    }

    private final Mode mode;
    private final List<Rule> rules;

    UpdatePolicy(Mode mode, List<Rule> rules) {
        this.mode = mode;
        this.rules = List.copyOf(rules);
    }

    static UpdatePolicy parse(byte[] json) throws UpdateException {
        try {
            JsonObject root = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            Mode mode = Mode.valueOf(root.get("mode").getAsString().trim().toUpperCase(Locale.ROOT));
            List<Rule> rules = new ArrayList<>();
            JsonArray classes = root.has("classes") ? root.getAsJsonArray("classes") : new JsonArray();
            for (JsonElement element : classes) {
                JsonObject rule = element.getAsJsonObject();
                FileClass fileClass = FileClass.valueOf(rule.get("class").getAsString().trim().toUpperCase(Locale.ROOT));
                rules.add(new Rule(PathGlob.of(rule.get("path").getAsString().trim()), fileClass));
            }
            return new UpdatePolicy(mode, rules);
        } catch (RuntimeException e) {
            throw new UpdateException("Update policy unreadable: " + UpdateException.describe(e), e);
        }
    }

    Mode mode() {
        return mode;
    }

    FileClass classify(String path) {
        String plain = InstancePaths.enabled(path);
        for (Rule rule : PLAYER_RULES) {
            if (rule.glob().matches(plain)) {
                return FileClass.PLAYER;
            }
        }
        for (Rule rule : PACK_RULES) {
            if (rule.glob().matches(plain)) {
                return FileClass.PACK;
            }
        }
        for (Rule rule : rules) {
            if (rule.glob().matches(plain)) {
                return rule.fileClass();
            }
        }
        return plain.startsWith("config/") ? FileClass.SETTINGS : FileClass.PACK;
    }
}
