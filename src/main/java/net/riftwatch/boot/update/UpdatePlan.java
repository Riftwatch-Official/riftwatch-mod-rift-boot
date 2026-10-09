package net.riftwatch.boot.update;

import java.util.List;
import java.util.Map;
import java.util.Set;

record UpdatePlan(
        List<Install> installs,
        List<Remove> removals,
        List<String> settings,
        Map<String, String> files,
        Set<String> playerRemoved,
        List<String> notes,
        boolean serviceJarTouched) {

    record Install(String path, PackArchive.Entry entry, String reason) {
        boolean download() {
            return !entry.override();
        }
    }

    record Remove(String path, String reason) {
    }

    UpdatePlan {
        installs = List.copyOf(installs);
        removals = List.copyOf(removals);
        settings = List.copyOf(settings);
        files = Map.copyOf(files);
        playerRemoved = Set.copyOf(playerRemoved);
        notes = List.copyOf(notes);
    }

    boolean empty() {
        return installs.isEmpty() && removals.isEmpty();
    }

    List<Install> downloads() {
        return installs.stream().filter(Install::download).toList();
    }

    long downloadBytes() {
        return installs.stream().filter(Install::download).mapToLong(install -> Math.max(0, install.entry().size())).sum();
    }
}
