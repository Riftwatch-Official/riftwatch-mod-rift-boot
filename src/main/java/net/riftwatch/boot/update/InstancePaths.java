package net.riftwatch.boot.update;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class InstancePaths {
    static final String UPDATER = "riftwatch/updater";
    static final String DISABLED = ".disabled";
    private static final int MAX_LENGTH = 512;
    private static final List<String> FORBIDDEN = List.of("saves", UPDATER);

    private InstancePaths() {
    }

    static boolean safe(String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_LENGTH) {
            return false;
        }
        if (path.startsWith("/") || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0 || path.indexOf('\0') >= 0) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        String lower = path.toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN) {
            if (lower.equals(forbidden) || lower.startsWith(forbidden + "/")) {
                return false;
            }
        }
        return true;
    }

    static Path resolve(Path root, String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path leaves the instance: " + path);
        }
        return resolved;
    }

    static boolean isModJar(String path) {
        String plain = enabled(path);
        return plain.startsWith("mods/") && plain.indexOf('/', 5) < 0 && plain.endsWith(".jar");
    }

    static String enabled(String path) {
        return path.endsWith(DISABLED) ? path.substring(0, path.length() - DISABLED.length()) : path;
    }

    static boolean playerRemovable(String path) {
        return path.startsWith("shaderpacks/") || path.startsWith("resourcepacks/");
    }
}
