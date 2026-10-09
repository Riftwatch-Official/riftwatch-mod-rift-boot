package net.riftwatch.boot.update;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class SettingsMerge {
    enum Kind {
        UNCHANGED,
        CREATED,
        KEYS,
        REPLACED,
        KEPT
    }

    record Result(Kind kind, byte[] content, List<String> taken, List<String> kept, List<String> added, List<String> removed) {
        boolean changes(byte[] disk) {
            return !Arrays.equals(content, disk);
        }

        String summary() {
            return switch (kind) {
                case UNCHANGED -> "unchanged";
                case CREATED -> "created from the pack";
                case REPLACED -> "replaced (never changed by the player)";
                case KEPT -> "kept as a whole (changed by the player, not readable per key)";
                case KEYS -> "pack keys taken " + taken.size() + " " + taken
                        + ", added " + added.size() + " " + added
                        + ", removed " + removed.size() + " " + removed
                        + ", player keys kept " + kept.size() + " " + kept;
            };
        }
    }

    private SettingsMerge() {
    }

    static Result merge(String path, byte[] base, byte[] local, byte[] next) {
        if (local == null) {
            return new Result(Kind.CREATED, next, List.of(), List.of(), List.of(), List.of());
        }
        Optional<KeyDocument> nextDocument = parse(path, next);
        Optional<KeyDocument> localDocument = parse(path, local);
        if (nextDocument.isEmpty() || localDocument.isEmpty()) {
            return wholeFile(base, local, next);
        }
        Map<String, String> baseValues = base == null ? null : parse(path, base).map(KeyDocument::values).orElse(null);
        KeyDocument document = localDocument.get();
        KeyDocument incoming = nextDocument.get();
        Map<String, String> localValues = document.values();
        Map<String, String> nextValues = incoming.values();
        List<String> taken = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, String> entry : nextValues.entrySet()) {
            String key = entry.getKey();
            String nextValue = entry.getValue();
            String localValue = localValues.get(key);
            String baseValue = baseValues == null ? null : baseValues.get(key);
            if (localValue == null) {
                if (document.opaque(key)) {
                    kept.add(key);
                } else if (baseValues == null || baseValue == null || !KeyDocument.same(nextValue, baseValue)) {
                    document.take(key, incoming);
                    added.add(key);
                }
            } else if (!KeyDocument.same(localValue, nextValue)) {
                if (baseValues != null && (baseValue == null || KeyDocument.same(localValue, baseValue))) {
                    document.take(key, incoming);
                    taken.add(key);
                } else {
                    kept.add(key);
                }
            }
        }
        if (baseValues != null) {
            for (Map.Entry<String, String> entry : baseValues.entrySet()) {
                String key = entry.getKey();
                String localValue = localValues.get(key);
                if (!nextValues.containsKey(key) && localValue != null && KeyDocument.same(localValue, entry.getValue())) {
                    document.remove(key);
                    removed.add(key);
                }
            }
        }
        boolean changed = !taken.isEmpty() || !added.isEmpty() || !removed.isEmpty();
        if (!changed && kept.isEmpty()) {
            return new Result(Kind.UNCHANGED, local, List.of(), List.of(), List.of(), List.of());
        }
        return new Result(Kind.KEYS, changed ? document.render() : local, taken, kept, added, removed);
    }

    static Result wholeFile(byte[] base, byte[] local, byte[] next) {
        if (Arrays.equals(local, next)) {
            return new Result(Kind.UNCHANGED, local, List.of(), List.of(), List.of(), List.of());
        }
        if (base != null && Arrays.equals(local, base)) {
            return new Result(Kind.REPLACED, next, List.of(), List.of(), List.of(), List.of());
        }
        return new Result(Kind.KEPT, local, List.of(), List.of(), List.of(), List.of());
    }

    static Optional<KeyDocument> parse(String path, byte[] content) {
        String name = path.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".toml")) {
                return LineSettings.parse(LineSettings.Syntax.TOML, content);
            }
            if (name.endsWith(".properties")) {
                return LineSettings.parse(LineSettings.Syntax.PROPERTIES, content);
            }
            if (name.endsWith(".cfg")) {
                return LineSettings.parse(LineSettings.Syntax.SPACED, content);
            }
            if (name.endsWith(".json")) {
                return JsonSettings.parse(content);
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            UpdateLog.LOG.debug("{} not readable per key: {}", path, UpdateException.describe(e));
            return Optional.empty();
        }
    }
}
