package net.riftwatch.boot.update;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackUpdaterTest {
    private static final String DH = "config/DistantHorizons.toml";
    private static final String DH_V1 = "[client]\n\tfarFogStart = \"0.0\"\n\tlodRadius = 256\n";
    private static final String DH_V2 = "[client]\n\tfarFogStart = \"0.2\"\n\tlodRadius = 256\n";
    private static final String DH_LIVE = "_version = 4\n[client]\n\t# fog start\n\tfarFogStart = \"0.0\"\n\tlodRadius = 128\n\tthreads = 4\n";
    private static final String DH_MERGED = "_version = 4\n[client]\n\t# fog start\n\tfarFogStart = \"0.2\"\n\tlodRadius = 128\n\tthreads = 4\n";
    private static final Map<String, byte[]> JARS = new ConcurrentHashMap<>();

    @TempDir
    Path root;

    private TestServer server;
    private KeyPair keys;
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());
    private final List<Runnable> exitHooks = new ArrayList<>();
    private final AtomicBoolean exited = new AtomicBoolean();

    @BeforeEach
    void start() throws Exception {
        server = new TestServer();
        keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static byte[] jar(String id) {
        return JARS.computeIfAbsent(id, key -> zip(Map.of(key + ".txt", key)));
    }

    private static byte[] serviceJar() {
        return JARS.computeIfAbsent("service", key -> zip(Map.of(
                "META-INF/services/net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper",
                "net.riftwatch.boot.RiftwatchBootstrapper\n")));
    }

    private static byte[] zip(Map<String, String> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private String url(String name) {
        return server.url("/files/" + name);
    }

    private TestPack modJar(TestPack pack, String path, String id) {
        return pack.file(path, jar(id), url(id + ".jar"), "required");
    }

    private TestPack v1(String mode) {
        TestPack pack = new TestPack("0.7.5").policy(mode);
        modJar(pack, "mods/keep.jar", "keep-1");
        modJar(pack, "mods/old.jar", "old-1");
        modJar(pack, "mods/change-1.jar", "change-1");
        modJar(pack, "mods/disabled.jar", "dis-1");
        return pack.file("mods/server-only.jar", jar("server"), url("server.jar"), "unsupported")
                .file("shaderpacks/pack-a.zip", "shader-a", url("shader-a.zip"))
                .override(DH, DH_V1)
                .override("config/fancymenu/layout.txt", "layout-1")
                .override("kubejs/x.js", "js-1");
    }

    private TestPack v2(String mode) {
        TestPack pack = v1(mode).copy("0.7.6")
                .without("mods/old.jar")
                .without("mods/change-1.jar");
        modJar(pack, "mods/change-2.jar", "change-2");
        modJar(pack, "mods/disabled.jar", "dis-2");
        modJar(pack, "mods/new.jar", "new-1");
        return pack.file("shaderpacks/pack-b.zip", "shader-b", url("shader-b.zip"))
                .override(DH, DH_V2)
                .override("config/fancymenu/layout.txt", "layout-2");
    }

    private void installAsLauncher(TestPack pack) throws Exception {
        pack.installInto(root);
        Files.write(root.resolve("mods/player.jar"), jar("player"));
        Files.move(root.resolve("mods/disabled.jar"), root.resolve("mods/disabled.jar.disabled"));
        Files.delete(root.resolve("shaderpacks/pack-a.zip"));
        write(DH, DH_LIVE);
    }

    private void publish(TestPack first, TestPack second) throws Exception {
        server.publish(first, keys.getPrivate(), false);
        server.publish(second, keys.getPrivate(), true);
    }

    private void write(String path, String content) throws Exception {
        Files.createDirectories(root.resolve(path).getParent());
        Files.writeString(root.resolve(path), content);
    }

    private String read(String path) throws Exception {
        return Files.readString(root.resolve(path));
    }

    private void assertJar(String id, String path) throws Exception {
        assertArrayEquals(jar(id), Files.readAllBytes(root.resolve(path)), path);
    }

    private boolean exists(String path) {
        return Files.exists(root.resolve(path));
    }

    private String marker() {
        return PackMarker.read(root).orElseThrow().text();
    }

    private UpdateState state() {
        return UpdateState.read(root.resolve(InstancePaths.UPDATER)).orElseThrow();
    }

    private PackUpdater updater() {
        Display display = new Display() {
            @Override
            public void message(String text) {
                messages.add(text);
            }

            @Override
            public Bar bar(String label, int steps) {
                return new Bar() {
                    @Override
                    public void label(String text) {
                    }

                    @Override
                    public void progress(int done) {
                    }

                    @Override
                    public void complete() {
                    }
                };
            }

            @Override
            public void pump() {
            }
        };
        return new PackUpdater(root, display, userAgent -> new PackSource(server.base(), true, userAgent),
                new PackSignature(List.of(keys.getPublic())), "0.1.1-test", "1.21.1", "21.1.255",
                () -> exited.set(true), exitHooks::add).restartSeconds(0);
    }

    @Test
    void updateKeepsPlayerFilesAndAppliesThePack() throws Exception {
        installAsLauncher(v1("apply"));
        publish(v1("apply"), v2("apply"));

        updater().run();

        assertJar("keep-1", "mods/keep.jar");
        assertFalse(exists("mods/old.jar"));
        assertFalse(exists("mods/change-1.jar"));
        assertJar("change-2", "mods/change-2.jar");
        assertFalse(exists("mods/disabled.jar"));
        assertJar("dis-2", "mods/disabled.jar.disabled");
        assertJar("new-1", "mods/new.jar");
        assertJar("player", "mods/player.jar");
        assertFalse(exists("mods/server-only.jar"));
        assertFalse(exists("shaderpacks/pack-a.zip"));
        assertEquals("shader-b", read("shaderpacks/pack-b.zip"));
        assertEquals("layout-2", read("config/fancymenu/layout.txt"));
        assertEquals("js-1", read("kubejs/x.js"));
        assertEquals(DH_MERGED, read(DH));
        assertEquals("0.7.6", marker());
        assertJar("old-1", "riftwatch/updater/backup/0.7.5/mods/old.jar");
        assertEquals("layout-1", read("riftwatch/updater/backup/0.7.5/config/fancymenu/layout.txt"));
        assertEquals("0.7.6", state().version().text());
        assertTrue(state().playerRemoved().contains("shaderpacks/pack-a.zip"));
        assertFalse(exists("riftwatch/updater/journal.json"));
        assertFalse(exists("riftwatch/updater/staging"));
        assertTrue(exists("riftwatch/updater/packs/Riftwatch-0.7.6.mrpack"));
        assertTrue(exists("riftwatch/updater/packs/Riftwatch-0.7.6.mrpack.sig"));
        assertFalse(exists("riftwatch/updater/packs/Riftwatch-0.7.5.mrpack"));
        assertFalse(exited.get());
        assertTrue(messages.contains("Riftwatch updated to 0.7.6"), messages::toString);

        int downloads = server.hits("/files/");
        messages.clear();
        updater().run();

        assertEquals(downloads, server.hits("/files/"));
        assertTrue(messages.contains("Riftwatch 0.7.6 is up to date"), messages::toString);
        assertEquals(DH_MERGED, read(DH));
    }

    @Test
    void launcherResetOfSettingsIsRestored() throws Exception {
        installAsLauncher(v1("apply"));
        publish(v1("apply"), v2("apply"));
        updater().run();
        exitHooks.forEach(Runnable::run);

        write(DH, DH_V2);
        updater().run();

        assertEquals(DH_MERGED, read(DH));
    }

    @Test
    void launcherUpdateIsAdoptedWithPackSettingChanges() throws Exception {
        installAsLauncher(v1("apply"));
        server.publish(v1("apply"), keys.getPrivate(), true);
        updater().run();
        assertEquals("0.7.5", state().version().text());

        server.publish(v2("apply"), keys.getPrivate(), true);
        v2("apply").installInto(root);
        updater().run();

        assertEquals(DH_MERGED, read(DH));
        assertFalse(exists("mods/old.jar"));
        assertFalse(exists("mods/change-1.jar"));
        assertJar("player", "mods/player.jar");
        assertEquals("0.7.6", state().version().text());
    }

    @Test
    void observeModeChangesNothing() throws Exception {
        installAsLauncher(v1("observe"));
        publish(v1("observe"), v2("observe"));

        updater().run();

        assertJar("old-1", "mods/old.jar");
        assertFalse(exists("mods/new.jar"));
        assertEquals(DH_LIVE, read(DH));
        assertEquals("0.7.5", marker());
        assertFalse(exists("riftwatch/updater/state.json"));
        assertEquals(0, server.hits("/files/"));
        assertTrue(messages.contains("Riftwatch 0.7.6 found: test mode, nothing changed"), messages::toString);
    }

    @Test
    void offlineStartKeepsTheInstalledPack() throws Exception {
        installAsLauncher(v1("apply"));
        server.down = true;

        updater().run();

        assertJar("old-1", "mods/old.jar");
        assertTrue(messages.contains("Update check failed, starting Riftwatch 0.7.5"), messages::toString);
    }

    @Test
    void offlineStartAfterAnUpdateDoesNotClaimToBeCurrent() throws Exception {
        installAsLauncher(v1("apply"));
        publish(v1("apply"), v2("apply"));
        updater().run();
        server.down = true;
        messages.clear();

        updater().run();

        assertEquals(DH_MERGED, read(DH));
        assertEquals(List.of("Checking for Riftwatch updates", "Update check failed, starting Riftwatch 0.7.6"), messages);
    }

    @Test
    void wrongSignatureStopsTheUpdate() throws Exception {
        installAsLauncher(v1("apply"));
        server.publish(v1("apply"), keys.getPrivate(), false);
        server.publish(v2("apply"), KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate(), true);

        updater().run();

        assertJar("old-1", "mods/old.jar");
        assertEquals("0.7.5", marker());
        assertFalse(exists("riftwatch/updater/packs/Riftwatch-0.7.6.mrpack"));
        assertTrue(messages.contains("Update failed, starting Riftwatch 0.7.5"), messages::toString);
    }

    @Test
    void brokenDownloadChangesNothing() throws Exception {
        installAsLauncher(v1("apply"));
        publish(v1("apply"), v2("apply"));
        server.put("/files/new-1.jar", jar("tampered"));

        updater().run();

        assertJar("old-1", "mods/old.jar");
        assertFalse(exists("mods/new.jar"));
        assertEquals("0.7.5", marker());
        assertFalse(exists("riftwatch/updater/journal.json"));
        assertTrue(messages.contains("Update failed, starting Riftwatch 0.7.5"), messages::toString);
    }

    @Test
    void newerLoaderIsNotInstalled() throws Exception {
        installAsLauncher(v1("apply"));
        publish(v1("apply"), v2("apply"));
        String latest = new String(server.get("/modpack/latest.json"), StandardCharsets.UTF_8);
        server.put("/modpack/latest.json", latest.replace("\"published_at\"", "\"neoforge\": \"21.1.300\", \"published_at\"").getBytes(StandardCharsets.UTF_8));

        updater().run();

        assertJar("old-1", "mods/old.jar");
        assertEquals(0, server.hits("/modpack/Riftwatch-0.7.6.mrpack"));
        assertTrue(messages.contains("Riftwatch 0.7.6 needs NeoForge 21.1.300: update the pack in your launcher"), messages::toString);
    }

    @Test
    void serviceJarChangeEndsTheStart() throws Exception {
        installAsLauncher(v1("apply"));
        publish(v1("apply"), v2("apply").file("mods/boot.jar", serviceJar(), url("boot-2.jar"), "required"));

        updater().run();

        assertTrue(exited.get());
        assertArrayEquals(serviceJar(), Files.readAllBytes(root.resolve("mods/boot.jar")));
        assertEquals("0.7.6", marker());
        assertTrue(messages.contains("Riftwatch 0.7.6 is installed. One more start finishes the update."), messages::toString);
    }

    @Test
    void updatesCanBeSwitchedOff() throws Exception {
        installAsLauncher(v1("apply"));
        write(PlayerSwitch.PATH, "enabled = false\n");
        publish(v1("apply"), v2("apply"));

        updater().run();

        assertJar("old-1", "mods/old.jar");
        assertEquals(0, server.hits("/modpack/"));
        assertTrue(messages.contains("Riftwatch updates are off"), messages::toString);
    }

    @Test
    void firstStartWithoutUpdateRecordsTheInstalledPack() throws Exception {
        installAsLauncher(v1("apply"));
        server.publish(v1("apply"), keys.getPrivate(), true);

        updater().run();

        assertEquals("0.7.5", state().version().text());
        assertTrue(state().playerRemoved().contains("shaderpacks/pack-a.zip"));
        assertFalse(exists("shaderpacks/pack-a.zip"));
        assertJar("dis-1", "mods/disabled.jar.disabled");
        assertEquals(DH_LIVE, read(DH));
        assertEquals(0, server.hits("/files/"));
        assertTrue(messages.contains("Riftwatch 0.7.5 is up to date"), messages::toString);
    }

    @Test
    void interruptedApplyIsFinishedOrRolledBack() throws Exception {
        write("mods/a.jar", "a-1");
        write("riftwatch/updater/staging/mods/a.jar", "a-2");
        new Journal(root, "0.7.5", "0.7.6", List.of(new Journal.Move("mods/a.jar", true, true))).write();

        assertEquals(Journal.Outcome.FINISHED, Journal.recover(root));
        assertEquals("a-2", read("mods/a.jar"));
        assertEquals("a-1", read("riftwatch/updater/backup/0.7.5/mods/a.jar"));
        assertFalse(exists("riftwatch/updater/staging"));

        write("riftwatch/updater/backup/0.7.6/mods/b.jar", "b-1");
        write("mods/c.jar", "c-new");
        new Journal(root, "0.7.6", "0.7.7", List.of(
                new Journal.Move("mods/b.jar", false, true),
                new Journal.Move("mods/c.jar", true, false),
                new Journal.Move("mods/d.jar", true, false))).write();

        assertEquals(Journal.Outcome.ROLLED_BACK, Journal.recover(root));
        assertEquals("b-1", read("mods/b.jar"));
        assertFalse(exists("mods/c.jar"));
        assertFalse(exists("riftwatch/updater/journal.json"));
    }

    @Test
    void missingStagingWithTheOldFileInPlaceRollsBack() throws Exception {
        write("mods/a.jar", "a-1");
        write("mods/b.jar", "b-2");
        write("riftwatch/updater/backup/0.7.5/mods/b.jar", "b-1");
        new Journal(root, "0.7.5", "0.7.6", List.of(
                new Journal.Move("mods/a.jar", true, true),
                new Journal.Move("mods/b.jar", true, true))).write();

        assertEquals(Journal.Outcome.ROLLED_BACK, Journal.recover(root));
        assertEquals("a-1", read("mods/a.jar"));
        assertEquals("b-1", read("mods/b.jar"));
        assertFalse(exists("riftwatch/updater/journal.json"));
    }

    @Test
    void failedInstallPutsThePreviousFilesBack() throws Exception {
        installAsLauncher(v1("apply"));
        publish(v1("apply"), v2("apply"));
        Path shaders = root.resolve("shaderpacks");
        assertTrue(shaders.toFile().setWritable(false));
        try {
            updater().run();
        } finally {
            assertTrue(shaders.toFile().setWritable(true));
        }

        assertJar("keep-1", "mods/keep.jar");
        assertJar("old-1", "mods/old.jar");
        assertJar("change-1", "mods/change-1.jar");
        assertFalse(exists("mods/change-2.jar"));
        assertFalse(exists("mods/new.jar"));
        assertJar("dis-1", "mods/disabled.jar.disabled");
        assertJar("player", "mods/player.jar");
        assertEquals("layout-1", read("config/fancymenu/layout.txt"));
        assertFalse(exists("shaderpacks/pack-b.zip"));
        assertEquals("0.7.5", marker());
        assertFalse(exists("riftwatch/updater/journal.json"));
        assertFalse(exists("riftwatch/updater/staging"));
        assertFalse(exited.get());
        assertTrue(messages.contains("Update failed, starting Riftwatch 0.7.5"), messages::toString);

        messages.clear();
        updater().run();

        assertJar("change-2", "mods/change-2.jar");
        assertEquals("shader-b", read("shaderpacks/pack-b.zip"));
        assertEquals("0.7.6", marker());
        assertTrue(messages.contains("Riftwatch updated to 0.7.6"), messages::toString);
    }

    @Test
    void stuckRecoveryClosesTheGameAndRepairsAtTheNextStart() throws Exception {
        write("mods/a.jar", "a-1");
        write("riftwatch/updater/staging/mods/a.jar", "a-2");
        new Journal(root, "0.7.5", "0.7.6", List.of(new Journal.Move("mods/a.jar", true, true))).write();
        Path mods = root.resolve("mods");
        assertTrue(mods.toFile().setWritable(false));
        try {
            updater().run();
        } finally {
            assertTrue(mods.toFile().setWritable(true));
        }

        assertTrue(exited.get());
        assertTrue(exists("riftwatch/updater/journal.json"));
        assertEquals("a-1", read("mods/a.jar"));
        assertTrue(messages.stream().anyMatch(text -> text.startsWith("The last Riftwatch update could not be finished or undone.")), messages::toString);

        exited.set(false);
        updater().run();

        assertFalse(exited.get());
        assertEquals("a-2", read("mods/a.jar"));
        assertFalse(exists("riftwatch/updater/journal.json"));
    }

    @Test
    void sourceKeysFollowModrinthProjectsAndGitHubRepositories() {
        PackArchive.Entry modrinth = entry("mods/create-6.0.10.jar", "https://cdn.modrinth.com/data/LNytGWDc/versions/abc/create-6.0.10.jar");
        PackArchive.Entry modrinthNext = entry("mods/create-6.0.11.jar", "https://cdn.modrinth.com/data/LNytGWDc/versions/def/create-6.0.11.jar");
        PackArchive.Entry github = entry("mods/rift-core-0.4.5.jar", "https://github.com/Riftwatch-Official/riftwatch-mod-rift-core/releases/download/v0.4.5/rift-core-0.4.5.jar");
        PackArchive.Entry other = entry("mods/x.jar", "https://riftwatch.net/downloads/mods/x.jar");

        assertEquals("modrinth:LNytGWDc", UpdatePlanner.sourceKey(modrinth));
        assertEquals(UpdatePlanner.sourceKey(modrinth), UpdatePlanner.sourceKey(modrinthNext));
        assertEquals("github:Riftwatch-Official/riftwatch-mod-rift-core:mods", UpdatePlanner.sourceKey(github));
        assertNull(UpdatePlanner.sourceKey(other));
    }

    @Test
    void renamedJarOfADisabledModStaysDisabled(@TempDir Path packs) throws Exception {
        TestPack first = new TestPack("0.7.5").policy("apply")
                .file("mods/create-1.jar", jar("create-1"), "https://cdn.modrinth.com/data/LNytGWDc/versions/a/create-1.jar", "required");
        TestPack second = new TestPack("0.7.6").policy("apply")
                .file("mods/create-2.jar", jar("create-2"), "https://cdn.modrinth.com/data/LNytGWDc/versions/b/create-2.jar", "required");
        first.installInto(root);
        Files.move(root.resolve("mods/create-1.jar"), root.resolve("mods/create-1.jar.disabled"));
        Path firstFile = Files.write(packs.resolve("first.mrpack"), first.build());
        Path secondFile = Files.write(packs.resolve("second.mrpack"), second.build());

        try (PackArchive current = PackArchive.open(firstFile); PackArchive target = PackArchive.open(secondFile)) {
            UpdatePlan plan = new UpdatePlanner(root, target, target.policy(), current, null, () -> { }).plan();

            assertEquals(List.of("mods/create-2.jar.disabled"), plan.installs().stream()
                    .map(UpdatePlan.Install::path).filter(path -> path.startsWith("mods/")).toList());
            assertEquals(List.of("mods/create-1.jar.disabled"), plan.removals().stream().map(UpdatePlan.Remove::path).toList());
        }
    }

    private static PackArchive.Entry entry(String path, String url) {
        return new PackArchive.Entry(path, "0", 1, List.of(URI.create(url)), false, null);
    }
}
