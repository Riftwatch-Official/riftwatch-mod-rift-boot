package net.riftwatch.boot.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PlayerSwitch {
    static final String PATH = "config/riftwatch-updater.toml";
    private static final String DEFAULT = "enabled = true\n";
    private static final Pattern ENABLED = Pattern.compile("^\\s*enabled\\s*=\\s*(true|false)\\s*(?:#.*)?$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private PlayerSwitch() {
    }

    static boolean enabled(Path root) {
        Path file = root.resolve(PATH);
        try {
            if (!Files.exists(file)) {
                FileOps.writeAtomically(file, DEFAULT.getBytes(StandardCharsets.UTF_8));
                return true;
            }
            Matcher matcher = ENABLED.matcher(Files.readString(file, StandardCharsets.UTF_8));
            return !matcher.find() || Boolean.parseBoolean(matcher.group(1));
        } catch (IOException | RuntimeException e) {
            UpdateLog.LOG.warn("Updater switch {} unreadable, updates stay on: {}", PATH, UpdateException.describe(e));
            return true;
        }
    }
}
