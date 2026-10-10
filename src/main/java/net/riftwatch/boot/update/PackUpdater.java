package net.riftwatch.boot.update;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;

final class PackUpdater {
    static final String PACKS = "packs";
    private static final Logger LOG = UpdateLog.LOG;
    private static final long CHECK_STALL_MILLIS = 3_000;
    private static final long WORK_STALL_MILLIS = 30_000;
    private static final long FREE_SPACE_MARGIN = 50L << 20;
    private static final int PARALLEL_DOWNLOADS = 4;
    private static final int RESTART_SECONDS = 8;

    private final Path root;
    private final Path updaterDir;
    private final Display display;
    private final Background background;
    private final Function<String, PackSource> sources;
    private final PackSignature signature;
    private final String bootVersion;
    private final String minecraft;
    private final String neoforge;
    private final Runnable exit;
    private final Consumer<Runnable> atExit;
    private final List<PackArchive> opened = new ArrayList<>();
    private PackSource source;
    private PackVersion installed;
    private int restartSeconds = RESTART_SECONDS;

    PackUpdater(Path root, Display display, Function<String, PackSource> sources, PackSignature signature, String bootVersion,
            String minecraft, String neoforge, Runnable exit, Consumer<Runnable> atExit) {
        this.root = root;
        this.updaterDir = root.resolve(InstancePaths.UPDATER);
        this.display = display;
        this.background = new Background(display);
        this.sources = sources;
        this.signature = signature;
        this.bootVersion = bootVersion;
        this.minecraft = minecraft;
        this.neoforge = neoforge;
        this.exit = exit;
        this.atExit = atExit;
    }

    PackUpdater restartSeconds(int seconds) {
        restartSeconds = seconds;
        return this;
    }

    void run() {
        long started = System.nanoTime();
        try {
            runChecked();
        } catch (UpdateException e) {
            LOG.warn("Riftwatch update stopped: {}", e.getMessage(), e.getCause());
            display.message("Update failed, starting Riftwatch " + (installed == null ? "as installed" : installed));
        } catch (IOException | RuntimeException e) {
            LOG.error("Riftwatch updater failed", e);
            display.message("Update failed, starting Riftwatch " + (installed == null ? "as installed" : installed));
        } finally {
            opened.forEach(PackArchive::close);
            opened.clear();
            LOG.info("Riftwatch updater done in {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    private void runChecked() throws UpdateException, IOException {
        Journal.Outcome recovery;
        try {
            recovery = Journal.recover(root);
        } catch (Journal.Stuck stuck) {
            LOG.error("Riftwatch files are between two versions", stuck);
            closeGame("The last Riftwatch update could not be finished or undone.");
            throw new UpdateException(stuck.getMessage(), stuck);
        }
        if (recovery != Journal.Outcome.NONE) {
            LOG.info("Update journal: {}", recovery.name().toLowerCase(Locale.ROOT));
        }
        if (!PlayerSwitch.enabled(root)) {
            LOG.info("Riftwatch updates are off in {}", PlayerSwitch.PATH);
            display.message("Riftwatch updates are off");
            return;
        }
        Optional<UpdateState> state = UpdateState.read(updaterDir);
        Optional<PackVersion> marker = PackMarker.read(root);
        installed = marker.or(() -> state.map(UpdateState::version)).orElse(null);
        if (installed == null) {
            LOG.info("No Riftwatch pack marker ({}), the updater stays idle", PackMarker.PATH);
            return;
        }
        source = sources.apply("Riftwatch-Updater/" + bootVersion + " (pack " + installed + ")");
        display.message("Checking for Riftwatch updates");
        Optional<PackSource.Latest> latest = check();
        boolean online = latest.isPresent();
        PackSource.Latest update = latest.filter(found -> found.version().newerThan(installed)).orElse(null);
        if (update != null && !compatible(update.minecraft(), update.neoforge(), update.version())) {
            return;
        }
        PackArchive target = update != null ? targetArchive(update) : archive(installed, online);
        if (target == null) {
            LOG.info("Riftwatch {} is not cached and cannot be fetched, settings stay as they are", installed);
            return;
        }
        if (update != null && !compatible(target.dependency("minecraft"), target.dependency("neoforge"), target.version())) {
            return;
        }
        UpdatePolicy policy = target.policy();
        if (policy.mode() == UpdatePolicy.Mode.OFF) {
            LOG.info("Updates are off in the policy of Riftwatch {}", target.version());
            return;
        }
        boolean apply = policy.mode() == UpdatePolicy.Mode.APPLY;
        UpdateState current = state.orElse(null);
        PackArchive installedArchive = update == null ? target : archive(installed, online);
        boolean launcherChanged = current != null && !current.version().text().equals(installed.text());
        PackArchive previousArchive = launcherChanged ? archive(current.version(), online) : installedArchive;
        PackArchive settingsBase = previousArchive != null ? previousArchive : installedArchive;
        List<String> settings = settingsPaths(target, policy);
        SettingsStep settingsStep = new SettingsStep(root, apply);
        atExit.accept(() -> settingsStep.snapshotAll(settings));
        if (update == null && current != null && !launcherChanged) {
            settingsStep.run(settings, settingsBase, installedArchive, target);
            upToDate(installed, online);
            return;
        }
        checkHosts(target);
        PackArchive owner = update != null ? installedArchive : launcherChanged ? previousArchive : target;
        UpdatePlan plan = background.run("planning", WORK_STALL_MILLIS,
                () -> new UpdatePlanner(root, target, policy, owner, current, background::touch).plan());
        log(plan, target.version(), update != null);
        if (!apply) {
            settingsStep.run(settings, settingsBase, installedArchive, target);
            keepPacks(new HashSet<>(List.of(installed.text(), target.version().text(), current == null ? installed.text() : current.version().text())));
            if (update != null) {
                display.message("Riftwatch " + target.version() + " found: test mode, nothing changed");
            }
            return;
        }
        boolean serviceJars = plan.serviceJarTouched();
        if (!plan.empty()) {
            ensureSpace(plan, target);
            stage(plan, target);
            serviceJars |= stagedServiceJar(plan);
            install(journal(plan, target.version()));
        }
        settingsStep.run(settings, settingsBase, installedArchive, target);
        new UpdateState(target.version(), bootVersion, plan.files(), plan.playerRemoved()).write(updaterDir);
        keepPacks(Set.of(target.version().text()));
        keepBackup(installed.text());
        if (serviceJars) {
            restartForServiceJars(target.version());
            return;
        }
        if (update != null) {
            LOG.info("Riftwatch updated from {} to {}", installed, target.version());
            display.message("Riftwatch updated to " + target.version());
        } else {
            upToDate(target.version(), online);
        }
    }

    private void upToDate(PackVersion version, boolean online) {
        if (online) {
            LOG.info("Riftwatch {} is up to date", version);
            display.message("Riftwatch " + version + " is up to date");
        } else {
            LOG.info("Riftwatch {} starts without an update check", version);
        }
    }

    private Optional<PackSource.Latest> check() {
        try {
            PackSource.Latest latest = background.run("check", CHECK_STALL_MILLIS, source::latest);
            LOG.info("Newest Riftwatch pack {}, installed {}", latest.version(), installed);
            return Optional.of(latest);
        } catch (UpdateException e) {
            LOG.warn("Update check failed: {}", e.getMessage());
            display.message("Update check failed, starting Riftwatch " + installed);
            return Optional.empty();
        }
    }

    private boolean compatible(Optional<String> wantedMinecraft, Optional<String> wantedNeoForge, PackVersion version) {
        if (minecraft != null && wantedMinecraft.isPresent() && !wantedMinecraft.get().equals(minecraft)) {
            LOG.warn("Riftwatch {} needs Minecraft {}, this instance runs {}", version, wantedMinecraft.get(), minecraft);
            display.message("Riftwatch " + version + " needs Minecraft " + wantedMinecraft.get() + ": update the pack in your launcher");
            return false;
        }
        if (neoforge != null && wantedNeoForge.isPresent() && !wantedNeoForge.get().equals(neoforge)) {
            LOG.warn("Riftwatch {} needs NeoForge {}, this instance runs {}", version, wantedNeoForge.get(), neoforge);
            display.message("Riftwatch " + version + " needs NeoForge " + wantedNeoForge.get() + ": update the pack in your launcher");
            return false;
        }
        if (minecraft == null || neoforge == null) {
            LOG.warn("Running Minecraft or NeoForge version unknown, the loader check is skipped");
        }
        return true;
    }

    private List<String> settingsPaths(PackArchive target, UpdatePolicy policy) {
        return target.entries().keySet().stream()
                .filter(path -> policy.classify(path) == UpdatePolicy.FileClass.SETTINGS)
                .toList();
    }

    private void checkHosts(PackArchive target) throws UpdateException {
        for (PackArchive.Entry entry : target.entries().values()) {
            for (URI uri : entry.downloads()) {
                if (!source.allowed(uri)) {
                    throw new UpdateException("Riftwatch " + target.version() + " names a download host outside the allowlist: " + uri.getHost());
                }
            }
        }
    }

    private Path packFile(PackVersion version) {
        return updaterDir.resolve(PACKS).resolve("Riftwatch-" + version.text() + ".mrpack");
    }

    private static Path signatureFile(Path pack) {
        return pack.resolveSibling(pack.getFileName() + ".sig");
    }

    private PackArchive open(Path file) throws UpdateException {
        PackArchive archive = PackArchive.open(file);
        opened.add(archive);
        return archive;
    }

    private PackArchive archive(PackVersion version, boolean online) {
        Path file = packFile(version);
        if (Files.isRegularFile(file)) {
            try {
                return open(file);
            } catch (UpdateException e) {
                LOG.warn("Cached pack {} unreadable: {}", file.getFileName(), e.getMessage());
            }
        }
        if (!online) {
            return null;
        }
        try {
            URI pack = source.packUri(version);
            background.run("pack " + version, WORK_STALL_MILLIS, () -> {
                fetchSigned(pack, URI.create(pack + ".sig"), file, null, -1);
                return null;
            });
            PackArchive archive = open(file);
            if (!archive.version().text().equals(version.text())) {
                throw new UpdateException(file.getFileName() + " holds Riftwatch " + archive.version());
            }
            return archive;
        } catch (UpdateException e) {
            LOG.warn("Riftwatch {} not available as a reference: {}", version, e.getMessage());
            return null;
        }
    }

    private PackArchive targetArchive(PackSource.Latest latest) throws UpdateException {
        Path file = packFile(latest.version());
        if (cachedTarget(file, latest)) {
            LOG.info("Riftwatch {} is already downloaded", latest.version());
        } else {
            display.message("Downloading Riftwatch " + latest.version());
            background.run("pack " + latest.version(), WORK_STALL_MILLIS, () -> {
                fetchSigned(latest.pack(), latest.signature(), file, latest.sha512(), latest.size());
                return null;
            });
        }
        PackArchive archive = open(file);
        if (!archive.version().text().equals(latest.version().text())) {
            throw new UpdateException(file.getFileName() + " holds Riftwatch " + archive.version() + ", latest.json says " + latest.version());
        }
        return archive;
    }

    private boolean cachedTarget(Path file, PackSource.Latest latest) {
        try {
            if (!Files.isRegularFile(file) || !FileOps.sha512(file).equals(latest.sha512())) {
                return false;
            }
            return signature.verify(file, FileOps.readIfExists(signatureFile(file)));
        } catch (IOException e) {
            return false;
        }
    }

    private void fetchSigned(URI pack, URI signatureUri, Path file, String sha512, long size) throws UpdateException {
        Path partial = file.resolveSibling(file.getFileName() + ".new");
        try {
            source.download(List.of(pack), partial, sha512, size, background::touch);
            byte[] signatureBytes = source.signature(signatureUri);
            if (!signature.verify(partial, signatureBytes)) {
                throw new UpdateException("Signature check failed for " + file.getFileName());
            }
            FileOps.writeAtomically(signatureFile(file), signatureBytes);
            FileOps.move(partial, file);
        } catch (IOException e) {
            throw new UpdateException(file.getFileName() + " could not be stored: " + UpdateException.describe(e), e);
        } finally {
            try {
                Files.deleteIfExists(partial);
            } catch (IOException e) {
                LOG.debug("Partial pack {} not deleted", partial, e);
            }
        }
    }

    private void log(UpdatePlan plan, PackVersion target, boolean update) {
        LOG.info("{} Riftwatch {} (installed {}): {} downloads ({} KB), {} files from the pack, {} removals, {} settings files",
                update ? "Update to" : "Check of",
                target,
                installed,
                plan.downloads().size(),
                plan.downloadBytes() / 1024,
                plan.installs().size() - plan.downloads().size(),
                plan.removals().size(),
                plan.settings().size());
        for (UpdatePlan.Install install : plan.installs()) {
            LOG.info("  install {} ({}){}", install.path(), install.reason(), install.download() ? " from " + install.entry().downloads().get(0).getHost() : "");
        }
        for (UpdatePlan.Remove remove : plan.removals()) {
            LOG.info("  remove {} ({})", remove.path(), remove.reason());
        }
        for (String note : plan.notes()) {
            LOG.info("  {}", note);
        }
        if (plan.serviceJarTouched()) {
            LOG.info("  a service jar changes, the update needs one more start");
        }
    }

    private void ensureSpace(UpdatePlan plan, PackArchive target) throws UpdateException, IOException {
        long needed = plan.installs().stream().mapToLong(install -> Math.max(0, install.entry().size())).sum() + FREE_SPACE_MARGIN;
        long free = Files.getFileStore(root).getUsableSpace();
        if (free < needed) {
            throw new UpdateException("Not enough free disk space for Riftwatch " + target.version() + ": " + (needed >> 20) + " MB needed, " + (free >> 20) + " MB free");
        }
    }

    private void stage(UpdatePlan plan, PackArchive target) throws UpdateException, IOException {
        Path staging = updaterDir.resolve(Journal.STAGING);
        FileOps.deleteTree(staging);
        for (UpdatePlan.Install install : plan.installs()) {
            if (!install.download()) {
                FileOps.writeAtomically(InstancePaths.resolve(staging, install.path()), target.read(install.entry().path()));
            }
        }
        List<UpdatePlan.Install> downloads = plan.downloads();
        if (downloads.isEmpty()) {
            return;
        }
        Display.Bar bar = display.bar("Riftwatch " + target.version(), downloads.size());
        try {
            display.message("Riftwatch " + target.version() + ": downloading " + downloads.size() + " files");
            background.run("download", WORK_STALL_MILLIS, () -> {
                downloadAll(downloads, staging, target.version(), bar);
                return null;
            });
        } finally {
            bar.complete();
        }
    }

    private void downloadAll(List<UpdatePlan.Install> downloads, Path staging, PackVersion version, Display.Bar bar) throws UpdateException {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_DOWNLOADS, Thread.ofPlatform().daemon().name("Riftwatch-Download-", 0).factory());
        AtomicInteger done = new AtomicInteger();
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (UpdatePlan.Install install : downloads) {
                futures.add(pool.submit(() -> {
                    PackArchive.Entry entry = install.entry();
                    source.download(entry.downloads(), InstancePaths.resolve(staging, install.path()), entry.sha512(), entry.size(), background::touch);
                    int finished = done.incrementAndGet();
                    bar.progress(finished);
                    display.message("Riftwatch " + version + ": downloaded " + finished + " of " + downloads.size() + " files");
                    return null;
                }));
            }
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UpdateException update) {
                throw update;
            }
            throw new UpdateException("Download failed: " + UpdateException.describe(e.getCause()), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpdateException("Download interrupted", e);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean stagedServiceJar(UpdatePlan plan) {
        Path staging = updaterDir.resolve(Journal.STAGING);
        return plan.installs().stream()
                .filter(install -> InstancePaths.isModJar(install.path()))
                .anyMatch(install -> ServiceJars.isServiceJar(InstancePaths.resolve(staging, install.path())));
    }

    private Journal journal(UpdatePlan plan, PackVersion target) throws IOException {
        List<Journal.Move> moves = new ArrayList<>();
        for (UpdatePlan.Remove remove : plan.removals()) {
            moves.add(new Journal.Move(remove.path(), false, true));
        }
        plan.installs().stream()
                .sorted(Comparator.comparing((UpdatePlan.Install install) -> install.path().equals(PackMarker.PATH)).thenComparing(UpdatePlan.Install::path))
                .forEach(install -> moves.add(new Journal.Move(install.path(), true, Files.exists(InstancePaths.resolve(root, install.path())))));
        Journal journal = new Journal(root, installed.text(), target.text(), moves);
        FileOps.deleteTree(journal.backupRoot());
        return journal;
    }

    private void keepPacks(Set<String> versions) {
        Set<String> keep = new HashSet<>();
        for (String version : versions) {
            keep.add("Riftwatch-" + version + ".mrpack");
            keep.add("Riftwatch-" + version + ".mrpack.sig");
        }
        deleteExcept(updaterDir.resolve(PACKS), keep);
    }

    private void keepBackup(String version) {
        deleteExcept(updaterDir.resolve(Journal.BACKUP), Set.of(version));
    }

    private void deleteExcept(Path directory, Set<String> keep) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> children = Files.list(directory)) {
            for (Path child : children.toList()) {
                if (!keep.contains(child.getFileName().toString())) {
                    FileOps.deleteTree(child);
                }
            }
        } catch (IOException e) {
            LOG.warn("Cleanup of {} failed: {}", directory, UpdateException.describe(e));
        }
    }

    private void install(Journal journal) throws UpdateException, IOException {
        journal.write();
        try {
            journal.execute();
        } catch (IOException | RuntimeException failure) {
            undo(journal, failure);
            throw new UpdateException("Installing Riftwatch " + journal.to() + " failed, the files of " + journal.from() + " are back: " + UpdateException.describe(failure), failure);
        }
        journal.finish();
    }

    private void undo(Journal journal, Exception failure) throws UpdateException {
        try {
            journal.rollBack();
            journal.finish();
        } catch (IOException | RuntimeException rollback) {
            failure.addSuppressed(rollback);
            LOG.error("Installing Riftwatch {} failed and could not be undone", journal.to(), failure);
            closeGame("Riftwatch " + journal.to() + " could not be installed or undone.");
            throw new UpdateException("Installing Riftwatch " + journal.to() + " failed and could not be undone: " + UpdateException.describe(failure), failure);
        }
    }

    private void closeGame(String reason) {
        display.message(reason + " The game closes so it never starts with mixed files. Close other programs that may hold Riftwatch files, then press Play again.");
        countDownAndExit();
    }

    private void restartForServiceJars(PackVersion version) {
        LOG.warn("Riftwatch {} changed a service jar, the game closes and needs one more start", version);
        display.message("Riftwatch " + version + " is installed. One more start finishes the update.");
        countDownAndExit();
    }

    private void countDownAndExit() {
        Display.Bar bar = display.bar("Closing in " + restartSeconds + " s, then press Play again", Math.max(1, restartSeconds));
        for (int left = restartSeconds; left > 0; left--) {
            bar.label("Closing in " + left + " s, then press Play again");
            bar.progress(restartSeconds - left);
            background.sleep(1000);
        }
        bar.complete();
        exit.run();
    }
}
