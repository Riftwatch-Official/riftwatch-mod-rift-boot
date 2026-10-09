package net.riftwatch.boot.update;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

final class UpdatePlanner {
    private final Path root;
    private final PackArchive target;
    private final UpdatePolicy policy;
    private final Runnable activity;
    private final Map<String, String> previous = new TreeMap<>();
    private final Map<String, PackArchive.Entry> previousEntries = new TreeMap<>();
    private final Set<String> owned = new HashSet<>();
    private final Set<String> playerRemoved = new TreeSet<>();
    private final Map<String, String> hashes = new HashMap<>();
    private final List<UpdatePlan.Install> installs = new ArrayList<>();
    private final List<UpdatePlan.Remove> removals = new ArrayList<>();
    private final List<String> settings = new ArrayList<>();
    private final Map<String, String> files = new TreeMap<>();
    private final List<String> notes = new ArrayList<>();
    private boolean serviceJarTouched;

    UpdatePlanner(Path root, PackArchive target, UpdatePolicy policy, PackArchive current, UpdateState state, Runnable activity) {
        this.root = root;
        this.target = target;
        this.policy = policy;
        this.activity = activity;
        if (state != null) {
            previous.putAll(state.files());
            playerRemoved.addAll(state.playerRemoved());
        }
        if (current != null) {
            for (PackArchive.Entry entry : current.entries().values()) {
                previous.put(entry.path(), entry.sha512());
                previousEntries.put(entry.path(), entry);
            }
        }
        owned.addAll(previous.values());
        playerRemoved.retainAll(target.entries().keySet());
    }

    UpdatePlan plan() throws IOException {
        for (PackArchive.Entry entry : target.entries().values()) {
            switch (policy.classify(entry.path())) {
                case SETTINGS -> settings.add(entry.path());
                case PLAYER -> planPlayerFile(entry);
                case PACK -> planPackFile(entry);
            }
        }
        planRemovals();
        return new UpdatePlan(installs, removals, settings, files, playerRemoved, notes, serviceJarTouched);
    }

    private void planPlayerFile(PackArchive.Entry entry) {
        String path = entry.path();
        if (!exists(path) && !exists(path + InstancePaths.DISABLED)) {
            installs.add(new UpdatePlan.Install(path, entry, "player file missing, created once"));
        }
    }

    private void planPackFile(PackArchive.Entry entry) throws IOException {
        String path = entry.path();
        files.put(path, entry.sha512());
        String disk = exists(path) ? path : exists(path + InstancePaths.DISABLED) ? path + InstancePaths.DISABLED : null;
        if (disk != null) {
            if (matches(disk, entry)) {
                return;
            }
            String before = previous.get(path);
            if (before != null && before.equals(entry.sha512()) && !InstancePaths.isModJar(path)) {
                notes.add("kept " + disk + ": changed on disk, unchanged in the pack");
                return;
            }
            if (InstancePaths.isModJar(path) && ServiceJars.isServiceJar(resolve(disk))) {
                serviceJarTouched = true;
            }
            installs.add(new UpdatePlan.Install(disk, entry, "changed in the pack"));
            return;
        }
        if (playerRemoved.contains(path)) {
            notes.add("left out " + path + ": removed by the player");
            return;
        }
        if ((entry.optional() || InstancePaths.playerRemovable(path)) && previous.containsKey(path)) {
            playerRemoved.add(path);
            notes.add("left out " + path + ": removed by the player");
            return;
        }
        String installPath = disabledTwin(entry) ? path + InstancePaths.DISABLED : path;
        installs.add(new UpdatePlan.Install(installPath, entry, previous.containsKey(path) ? "missing, installed again" : "new in the pack"));
    }

    private void planRemovals() throws IOException {
        Set<String> removing = new TreeSet<>();
        for (String old : previous.keySet()) {
            if (target.entries().containsKey(old) || policy.classify(old) != UpdatePolicy.FileClass.PACK) {
                continue;
            }
            for (String candidate : List.of(old, old + InstancePaths.DISABLED)) {
                if (exists(candidate) && removing.add(candidate)) {
                    remove(candidate, "dropped by the pack");
                }
            }
        }
        for (String jar : modJars()) {
            if (target.entries().containsKey(InstancePaths.enabled(jar)) || removing.contains(jar)) {
                continue;
            }
            if (owned.contains(hash(jar))) {
                removing.add(jar);
                remove(jar, "older pack jar");
            }
        }
    }

    private void remove(String path, String reason) {
        if (InstancePaths.isModJar(path) && ServiceJars.isServiceJar(resolve(path))) {
            serviceJarTouched = true;
        }
        removals.add(new UpdatePlan.Remove(path, reason));
    }

    private boolean disabledTwin(PackArchive.Entry entry) {
        String key = sourceKey(entry);
        if (key == null) {
            return false;
        }
        for (PackArchive.Entry old : previousEntries.values()) {
            if (!old.path().equals(entry.path()) && key.equals(sourceKey(old))
                    && exists(old.path() + InstancePaths.DISABLED) && !exists(old.path())) {
                return true;
            }
        }
        return false;
    }

    static String sourceKey(PackArchive.Entry entry) {
        if (entry.downloads().isEmpty()) {
            return null;
        }
        URI uri = entry.downloads().get(0);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String[] parts = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
        if (host.equals("cdn.modrinth.com") && parts.length > 2 && parts[1].equals("data")) {
            return "modrinth:" + parts[2];
        }
        if (host.equals("github.com") && parts.length > 2) {
            int slash = entry.path().lastIndexOf('/');
            return "github:" + parts[1] + "/" + parts[2] + ":" + (slash < 0 ? "" : entry.path().substring(0, slash));
        }
        return null;
    }

    private boolean matches(String disk, PackArchive.Entry entry) throws IOException {
        Path file = resolve(disk);
        if (entry.size() >= 0 && Files.size(file) != entry.size()) {
            return false;
        }
        return hash(disk).equals(entry.sha512());
    }

    private String hash(String path) throws IOException {
        String known = hashes.get(path);
        if (known != null) {
            return known;
        }
        String computed = FileOps.sha512(resolve(path));
        hashes.put(path, computed);
        activity.run();
        return computed;
    }

    private List<String> modJars() throws IOException {
        Path mods = root.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return List.of();
        }
        try (Stream<Path> listing = Files.list(mods)) {
            return listing.filter(Files::isRegularFile)
                    .map(file -> "mods/" + file.getFileName())
                    .filter(InstancePaths::isModJar)
                    .sorted()
                    .toList();
        }
    }

    private boolean exists(String path) {
        return Files.isRegularFile(resolve(path));
    }

    private Path resolve(String path) {
        return InstancePaths.resolve(root, path);
    }
}
