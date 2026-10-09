package net.riftwatch.boot.update;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RulesTest {
    @TempDir
    Path temp;

    @Test
    void versionsCompareNumerically() {
        PackVersion v075 = PackVersion.parse("0.7.5").orElseThrow();
        PackVersion v0710 = PackVersion.parse("0.7.10").orElseThrow();
        PackVersion v076beta = PackVersion.parse("0.7.6-beta").orElseThrow();
        PackVersion v076 = PackVersion.parse("0.7.6").orElseThrow();

        assertTrue(v0710.newerThan(v075));
        assertTrue(v076.newerThan(v076beta));
        assertTrue(v076beta.newerThan(v075));
        assertFalse(v075.newerThan(PackVersion.parse("0.7.5").orElseThrow()));
        assertTrue(PackVersion.parse("../0.7.5").isEmpty());
        assertTrue(PackVersion.parse("0.7.5/x").isEmpty());
        assertTrue(PackVersion.parse(null).isEmpty());
    }

    @Test
    void pathsStayInsideTheInstance() {
        assertTrue(InstancePaths.safe("mods/create.jar"));
        assertTrue(InstancePaths.safe("config/fancymenu/layouts/title.txt"));
        assertFalse(InstancePaths.safe("../evil.jar"));
        assertFalse(InstancePaths.safe("mods/../../evil.jar"));
        assertFalse(InstancePaths.safe("/etc/passwd"));
        assertFalse(InstancePaths.safe("C:/Windows/x"));
        assertFalse(InstancePaths.safe("mods\\x.jar"));
        assertFalse(InstancePaths.safe("saves/world/level.dat"));
        assertFalse(InstancePaths.safe("Saves/world/level.dat"));
        assertFalse(InstancePaths.safe("riftwatch/updater/state.json"));
        assertFalse(InstancePaths.safe("mods//x.jar"));
        assertTrue(InstancePaths.isModJar("mods/a.jar"));
        assertTrue(InstancePaths.isModJar("mods/a.jar.disabled"));
        assertFalse(InstancePaths.isModJar("mods/sub/a.jar"));
        assertThrows(IllegalArgumentException.class, () -> InstancePaths.resolve(temp, "../x"));
    }

    @Test
    void globsMatchPaths() {
        assertTrue(PathGlob.of("config/fancymenu/**").matches("config/fancymenu/a/b.txt"));
        assertTrue(PathGlob.of("config/**/x.toml").matches("config/x.toml"));
        assertTrue(PathGlob.of("config/**/x.toml").matches("config/a/b/x.toml"));
        assertTrue(PathGlob.of("shaderpacks/*.txt").matches("shaderpacks/BSL.zip.txt"));
        assertFalse(PathGlob.of("shaderpacks/*.txt").matches("shaderpacks/a/b.txt"));
        assertFalse(PathGlob.of("config/a.json").matches("config/aXjson"));
    }

    @Test
    void policyClassifiesWithBuiltInRulesFirst() throws UpdateException {
        String json = """
                {"mode": "apply", "classes": [
                  {"path": "options.txt", "class": "pack"},
                  {"path": "config/riftwatch/**", "class": "settings"},
                  {"path": "config/fancymenu/**", "class": "pack"},
                  {"path": "config/DistantHorizons.toml", "class": "settings"}
                ]}""";
        UpdatePolicy policy = UpdatePolicy.parse(json.getBytes(StandardCharsets.UTF_8));

        assertEquals(UpdatePolicy.Mode.APPLY, policy.mode());
        assertEquals(UpdatePolicy.FileClass.PLAYER, policy.classify("options.txt"));
        assertEquals(UpdatePolicy.FileClass.PLAYER, policy.classify("config/fancymenu/user_variables.db"));
        assertEquals(UpdatePolicy.FileClass.PACK, policy.classify("config/fancymenu/layouts/title.txt"));
        assertEquals(UpdatePolicy.FileClass.PACK, policy.classify(PackMarker.PATH));
        assertEquals(UpdatePolicy.FileClass.PACK, policy.classify(UpdatePolicy.PATH));
        assertEquals(UpdatePolicy.FileClass.SETTINGS, policy.classify("config/DistantHorizons.toml"));
        assertEquals(UpdatePolicy.FileClass.SETTINGS, policy.classify("config/unknown.json"));
        assertEquals(UpdatePolicy.FileClass.PACK, policy.classify("mods/create.jar"));
        assertEquals(UpdatePolicy.FileClass.PACK, policy.classify("kubejs/startup_scripts/a.js"));
        assertEquals(UpdatePolicy.Mode.OBSERVE, UpdatePolicy.MISSING.mode());
        assertThrows(UpdateException.class, () -> UpdatePolicy.parse("{\"mode\": \"later\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void launchArgumentsReadBothForms() {
        List<String> arguments = List.of("--launchTarget", "forgeclient", "--fml.neoForgeVersion", "21.1.255", "--fml.mcVersion=1.21.1");

        assertEquals("21.1.255", LaunchArguments.value(arguments, "fml.neoForgeVersion").orElseThrow());
        assertEquals("1.21.1", LaunchArguments.value(arguments, "fml.mcVersion").orElseThrow());
        assertTrue(LaunchArguments.value(arguments, "fml.fmlVersion").isEmpty());
    }

    @Test
    void signaturesVerifyOnlyWithTheRightKey() throws Exception {
        KeyPair right = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair wrong = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path pack = temp.resolve("pack.mrpack");
        Files.writeString(pack, "riftwatch pack bytes");
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(right.getPrivate());
        signer.update(Files.readAllBytes(pack));
        byte[] raw = signer.sign();

        PackSignature verifier = new PackSignature(List.of(wrong.getPublic(), right.getPublic()));
        assertTrue(verifier.verify(pack, raw));
        assertTrue(verifier.verify(pack, (Base64.getEncoder().encodeToString(raw) + "\n").getBytes(StandardCharsets.US_ASCII)));
        assertFalse(new PackSignature(List.of(wrong.getPublic())).verify(pack, raw));
        Files.writeString(pack, "riftwatch pack bytes changed");
        assertFalse(verifier.verify(pack, raw));
        assertFalse(verifier.verify(pack, new byte[10]));
        assertNull(PackSignature.decodeSignature(null));
    }

    @Test
    void embeddedKeysLoad() {
        PackSignature.embedded();
        byte[] encoded = PackSignature.decodeKey("MCowBQYDK2VwAyEARhxpZctbaeVhyhLTDHnX1Ycvqllr2+6ke5wNHp88+Us=").getEncoded();
        assertArrayEquals(Base64.getDecoder().decode("MCowBQYDK2VwAyEARhxpZctbaeVhyhLTDHnX1Ycvqllr2+6ke5wNHp88+Us="), encoded);
    }

    @Test
    void playerSwitchIsCreatedOnAndCanBeTurnedOff() throws Exception {
        assertTrue(PlayerSwitch.enabled(temp));
        assertEquals("enabled = true\n", Files.readString(temp.resolve(PlayerSwitch.PATH)));
        Files.writeString(temp.resolve(PlayerSwitch.PATH), "enabled = false\n");
        assertFalse(PlayerSwitch.enabled(temp));
    }
}
