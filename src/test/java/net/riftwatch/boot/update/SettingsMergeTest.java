package net.riftwatch.boot.update;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsMergeTest {
    private static final String DH = "config/DistantHorizons.toml";

    private static String resource(String name) throws IOException {
        try (InputStream input = SettingsMergeTest.class.getResourceAsStream(name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] content) {
        return new String(content, StandardCharsets.UTF_8);
    }

    private static List<String> changedLines(String before, String after) {
        List<String> old = before.lines().toList();
        return after.lines().filter(line -> !old.contains(line)).toList();
    }

    @Test
    void distantHorizonsKeepsPlayerKeysAndTakesPackChanges() throws IOException {
        String shipped = resource("DistantHorizons.shipped.toml");
        String live = resource("DistantHorizons.live.toml").replace("lodChunkRenderDistanceRadius = 256", "lodChunkRenderDistanceRadius = 128");
        String next = shipped.replace("farFogStart = \"0.0\"", "farFogStart = \"0.2\"")
                .replace("\t\t\t\toverdrawPrevention = \"0.4\"\n", "")
                .replace("lodChunkRenderDistanceRadius = 256", "lodChunkRenderDistanceRadius = 192");

        SettingsMerge.Result result = SettingsMerge.merge(DH, bytes(shipped), bytes(live), bytes(next));
        String merged = text(result.content());

        assertEquals(SettingsMerge.Kind.KEYS, result.kind());
        assertTrue(merged.contains("\t\t\t\tlodChunkRenderDistanceRadius = 128\n"));
        assertTrue(merged.contains("\t\t\t\tfarFogStart = \"0.2\"\n"));
        assertFalse(merged.contains("overdrawPrevention"));
        assertEquals(List.of("client.advanced.graphics.fog.farFogStart"), result.taken());
        assertEquals(List.of("client.advanced.graphics.quality.lodChunkRenderDistanceRadius"), result.kept());
        assertEquals(List.of("client.advanced.graphics.culling.overdrawPrevention"), result.removed());
        assertEquals(live.lines().count() - 1, merged.lines().count());
        assertEquals(List.of("\t\t\t\tfarFogStart = \"0.2\""), changedLines(live, merged));
    }

    @Test
    void distantHorizonsAddsNewPackKeysInTheirSection() throws IOException {
        String shipped = resource("DistantHorizons.shipped.toml");
        String live = resource("DistantHorizons.live.toml");
        String next = shipped.replace("\t\t\t\tfarFogStart = \"0.0\"\n", "\t\t\t\tfarFogStart = \"0.0\"\n\t\t\t\triftwatchTestKey = 7\n")
                + "\n[client.riftwatch]\n\tbrandNewKey = true\n";

        SettingsMerge.Result result = SettingsMerge.merge(DH, bytes(shipped), bytes(live), bytes(next));
        String merged = text(result.content());

        assertEquals(List.of("client.advanced.graphics.fog.riftwatchTestKey", "client.riftwatch.brandNewKey"), result.added());
        int fogHeader = merged.indexOf("[client.advanced.graphics.fog]");
        int added = merged.indexOf("riftwatchTestKey = 7");
        int nextHeader = merged.indexOf("\n\t\t\t[", fogHeader + 1);
        assertTrue(added > fogHeader && (nextHeader < 0 || added < nextHeader));
        assertTrue(merged.endsWith("[client.riftwatch]\n\tbrandNewKey = true\n"));
        assertTrue(SettingsMerge.parse(DH, result.content()).isPresent());
    }

    @Test
    void unchangedSettingsStayByteForByte() throws IOException {
        String shipped = resource("DistantHorizons.shipped.toml");
        String live = resource("DistantHorizons.live.toml");

        SettingsMerge.Result result = SettingsMerge.merge(DH, bytes(shipped), bytes(live), bytes(shipped));

        assertEquals(SettingsMerge.Kind.UNCHANGED, result.kind());
        assertArrayEquals(bytes(live), result.content());
    }

    @Test
    void irisKeepsTheShaderChoice() throws IOException {
        String shipped = resource("iris.properties");
        String local = shipped.replace("shaderPack=Riftwatch Shader 3.4.1a.zip", "shaderPack=BSL_v10.0.zip")
                .replace("#Tue Oct 06 15:35:31 CEST 2026", "#Fri Oct 09 12:00:00 CEST 2026");
        String next = shipped.replace("maxShadowRenderDistance=32", "maxShadowRenderDistance=24")
                .replace("shaderPack=Riftwatch Shader 3.4.1a.zip", "shaderPack=Riftwatch Shader 3.4.2.zip");

        SettingsMerge.Result result = SettingsMerge.merge("config/iris.properties", bytes(shipped), bytes(local), bytes(next));
        String merged = text(result.content());

        assertTrue(merged.contains("shaderPack=BSL_v10.0.zip\n"));
        assertTrue(merged.contains("maxShadowRenderDistance=24\n"));
        assertTrue(merged.contains("#Fri Oct 09 12:00:00 CEST 2026\n"));
        assertEquals(List.of("shaderPack"), result.kept());
    }

    @Test
    void xaeroCfgMergesPerKey() throws IOException {
        String shipped = resource("xaero-default.cfg");
        String local = shipped.replace("minimap_ui_scale = 1", "minimap_ui_scale = 2");
        String next = shipped.replace("radar_name_scale = 1.0", "radar_name_scale = 1.5").replace("minimap_ui_scale = 1", "minimap_ui_scale = 3");

        SettingsMerge.Result result = SettingsMerge.merge("config/xaero/minimap/profiles/default.cfg", bytes(shipped), bytes(local), bytes(next));
        String merged = text(result.content());

        assertTrue(merged.contains("minimap_ui_scale = 2\n"));
        assertTrue(merged.contains("radar_name_scale = 1.5\n"));
        assertTrue(merged.startsWith("############\n## Client Config Profile \"default\""));
    }

    @Test
    void jsonMergesLeavesAndKeepsLayout() throws IOException {
        String shipped = resource("cirrus-client.json");
        String local = shipped.replace("\"renderDistanceChunks\": 64", "\"renderDistanceChunks\": 32");
        String next = shipped.replace("\"rainCloudCoverage\": 0.55", "\"rainCloudCoverage\": 0.6")
                .replace("\"renderDistanceChunks\": 64", "\"renderDistanceChunks\": 48");

        SettingsMerge.Result result = SettingsMerge.merge("config/cirrus-client.json", bytes(shipped), bytes(local), bytes(next));
        String merged = text(result.content());

        assertTrue(merged.contains("\"renderDistanceChunks\": 32,"));
        assertTrue(merged.contains("\"rainCloudCoverage\": 0.6,"));
        assertTrue(merged.contains("\"lightningBoltIntensity\": 1.0,"));
        assertEquals(List.of("clouds/rainCloudCoverage"), result.taken());
        assertEquals(List.of("clouds/renderDistanceChunks"), result.kept());
        assertEquals(changedLines(local, merged), List.of("    \"rainCloudCoverage\": 0.6,"));
    }

    @Test
    void withoutBaseOnlyMissingKeysAreAdded() {
        String local = "a=1\nb=2\n";
        String next = "a=5\nb=2\nc=3\n";

        SettingsMerge.Result result = SettingsMerge.merge("config/x.properties", null, bytes(local), bytes(next));

        assertEquals("a=1\nb=2\nc=3\n", text(result.content()));
        assertEquals(List.of("a"), result.kept());
        assertEquals(List.of("c"), result.added());
    }

    @Test
    void unknownFormatsUseWholeFileRules() {
        byte[] base = bytes("old");
        byte[] next = bytes("new");

        assertEquals(SettingsMerge.Kind.REPLACED, SettingsMerge.merge("config/x.bin", base, base, next).kind());
        assertEquals(SettingsMerge.Kind.KEPT, SettingsMerge.merge("config/x.bin", base, bytes("mine"), next).kind());
        assertEquals(SettingsMerge.Kind.CREATED, SettingsMerge.merge("config/x.bin", base, null, next).kind());
    }

    @Test
    void windowsLineEndingsSurvive() {
        String base = "[a]\r\nx = 1\r\ny = 2\r\n";
        String local = "[a]\r\nx = 1\r\ny = 9\r\n";
        String next = "[a]\r\nx = 4\r\ny = 2\r\n";

        SettingsMerge.Result result = SettingsMerge.merge("config/x.toml", bytes(base), bytes(local), bytes(next));

        assertEquals("[a]\r\nx = 4\r\ny = 9\r\n", text(result.content()));
    }

    @Test
    void multiLineTomlValuesAreLeftAlone() {
        String base = "list = [\n  1,\n  2\n]\nflag = true\n";
        String local = "list = [\n  1,\n  3\n]\nflag = true\n";
        String next = "list = [\n  5\n]\nflag = false\n";

        SettingsMerge.Result result = SettingsMerge.merge("config/x.toml", bytes(base), bytes(local), bytes(next));

        assertEquals("list = [\n  1,\n  3\n]\nflag = false\n", text(result.content()));
        assertEquals(List.of("flag"), result.taken());
        assertEquals(List.of(), result.added());
    }

    @Test
    void valuesCompareByMeaning() {
        assertTrue(KeyDocument.same("\"0.0\"", "0"));
        assertTrue(KeyDocument.same("TRUE", "true"));
        assertTrue(KeyDocument.same("1.0000001", "1.0"));
        assertFalse(KeyDocument.same("1.1", "1.0"));
        assertFalse(KeyDocument.same("abc", "abd"));
    }
}
