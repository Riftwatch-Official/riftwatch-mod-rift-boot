package net.riftwatch.boot.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

final class SettingsStep {
    static final String SNAPSHOTS = "settings";

    private final Path root;
    private final Path snapshots;
    private final boolean apply;

    SettingsStep(Path root, boolean apply) {
        this.root = root;
        this.snapshots = root.resolve(InstancePaths.UPDATER).resolve(SNAPSHOTS);
        this.apply = apply;
    }

    void run(List<String> paths, PackArchive base, PackArchive installed, PackArchive next) {
        for (String path : paths) {
            try {
                byte[] nextContent = next.read(path);
                if (nextContent == null) {
                    continue;
                }
                byte[] baseContent = base == null ? null : base.read(path);
                byte[] installedContent = installed == null ? null : installed.read(path);
                merge(path, baseContent, installedContent, nextContent);
            } catch (UpdateException | IOException | RuntimeException e) {
                UpdateLog.LOG.warn("Settings file {} left as it is: {}", path, UpdateException.describe(e));
            }
        }
    }

    void merge(String path, byte[] base, byte[] installed, byte[] next) throws IOException {
        Path file = InstancePaths.resolve(root, path);
        byte[] disk = FileOps.readIfExists(file);
        byte[] snapshot = FileOps.readIfExists(snapshot(path));
        boolean shipped = disk == null || Arrays.equals(disk, base) || Arrays.equals(disk, installed) || Arrays.equals(disk, next);
        boolean reset = snapshot != null && !Arrays.equals(snapshot, disk) && shipped;
        if (!reset && disk != null && Arrays.equals(base, next)) {
            if (snapshot == null || !Arrays.equals(snapshot, disk)) {
                FileOps.writeAtomically(snapshot(path), disk);
            }
            return;
        }
        byte[] local = reset ? snapshot : disk;
        SettingsMerge.Result result = SettingsMerge.merge(path, base, local, next);
        String restored = reset ? " (player values restored from the snapshot after a reset)" : "";
        if (!apply) {
            UpdateLog.LOG.info("{} would be {}{}: test mode, nothing written", path, result.summary(), restored);
            if (!reset && disk != null) {
                FileOps.writeAtomically(snapshot(path), disk);
            }
            return;
        }
        if (result.changes(disk)) {
            FileOps.writeAtomically(file, result.content());
            UpdateLog.LOG.info("{} written: {}{}", path, result.summary(), restored);
        } else {
            UpdateLog.LOG.info("{} unchanged: {}", path, result.summary());
        }
        FileOps.writeAtomically(snapshot(path), result.content());
    }

    void snapshotAll(List<String> paths) {
        for (String path : paths) {
            try {
                byte[] disk = FileOps.readIfExists(InstancePaths.resolve(root, path));
                if (disk != null) {
                    FileOps.writeAtomically(snapshot(path), disk);
                }
            } catch (IOException | RuntimeException e) {
                UpdateLog.LOG.warn("Settings snapshot of {} failed: {}", path, UpdateException.describe(e));
            }
        }
    }

    private Path snapshot(String path) {
        return InstancePaths.resolve(snapshots, path);
    }
}
