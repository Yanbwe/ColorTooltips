package org.yanbwe.colortooltips.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BypassListEditor} 单元测试（纯逻辑 + 真实临时文件，不依赖游戏环境）。
 * <p>
 * 断言使用纯文本/正则，刻意不引入 Gson 之外的依赖：测试源集只保证有 JUnit，
 * 这样同一份测试在 Forge 与 NeoForge 两个项目里都能直接跑。
 */
class BypassListEditorTest {

    @TempDir
    Path tempDir;

    /** 写入一份典型的 common.json。 */
    private Path writeCommon(String json) throws IOException {
        Path file = tempDir.resolve("common.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** 断言文本中恰好出现 count 次该条目（值用 JSON 字符串转义后匹配）。 */
    private static void assertEntryCount(String content, String entry, int count) {
        String quoted = "\"" + entry.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        int actual = 0;
        int index = content.indexOf(quoted);
        while (index >= 0) {
            actual++;
            index = content.indexOf(quoted, index + quoted.length());
        }
        assertEquals(count, actual, "条目 " + entry + " 在文件中的出现次数不符：\n" + content);
    }

    /** 断言文本中不存在该条目。 */
    private static void assertNoEntry(String content, String entry) {
        assertEntryCount(content, entry, 0);
    }

    private static final String COMMON_JSON = """
            {
              "styleSelector": {
                "common": { "onlyText": "Vanilla", "items": {} }
              },
              "tooltipLock": { "enabled": true, "sensitivity": 10.0 },
              "bypass": { "enabled": true, "mods": [], "items": [] },
              "smoothColor": true
            }
            """;

    // ══════════════════════════════════════════════════════════
    // parseEntry
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("parseEntry：完整注册名 → 进 items")
    void parseFullRegistryName() {
        String[] entry = BypassListEditor.parseEntry("slashblade:slashblade");
        assertEquals(null, entry[0]);
        assertEquals("slashblade:slashblade", entry[1]);
    }

    @Test
    @DisplayName("parseEntry：只有模组 ID → 进 mods")
    void parseModIdOnly() {
        String[] entry = BypassListEditor.parseEntry("slashblade");
        assertEquals("slashblade", entry[0]);
        assertEquals(null, entry[1]);
    }

    @Test
    @DisplayName("parseEntry：namespace:* 视为整个模组")
    void parseWildcardPath() {
        String[] entry = BypassListEditor.parseEntry("slashblade:*");
        assertEquals("slashblade", entry[0]);
        assertEquals(null, entry[1]);
    }

    @Test
    @DisplayName("parseEntry：容忍首尾空白")
    void parseTrimsWhitespace() {
        String[] entry = BypassListEditor.parseEntry("  minecraft:diamond_sword  ");
        assertEquals("minecraft:diamond_sword", entry[1]);
    }

    @Test
    @DisplayName("parseEntry：接受从日志复制的原始 JSON")
    void parseRawJson() {
        String[] object = BypassListEditor.parseEntry("{\"id\":\"slashblade:slashblade\",\"Count\":1b}");
        assertEquals("slashblade:slashblade", object[1]);

        String[] array = BypassListEditor.parseEntry("[{\"id\":\"minecraft:diamond_sword\"}]");
        assertEquals("minecraft:diamond_sword", array[1]);
    }

    @Test
    @DisplayName("parseEntry：非法输入抛 IllegalArgumentException")
    void parseRejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> BypassListEditor.parseEntry(null));
        assertThrows(IllegalArgumentException.class, () -> BypassListEditor.parseEntry("   "));
        assertThrows(IllegalArgumentException.class, () -> BypassListEditor.parseEntry(":diamond"));
        assertThrows(IllegalArgumentException.class, () -> BypassListEditor.parseEntry("Minecraft:Diamond"));
        assertThrows(IllegalArgumentException.class, () -> BypassListEditor.parseEntry("minecraft:"));
        assertThrows(IllegalArgumentException.class, () -> BypassListEditor.parseEntry("!minecraft:diamond"));
        assertThrows(IllegalArgumentException.class, () -> BypassListEditor.parseEntry("{\"count\":1}"));
    }

    // ══════════════════════════════════════════════════════════
    // add / remove
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("add：按注册名加入 items 并写回文件")
    void addItemWritesFile() throws IOException {
        Path file = writeCommon(COMMON_JSON);

        BypassListEditor.Result result = BypassListEditor.add(file, "minecraft:diamond_sword");

        assertEquals(BypassListEditor.Status.ADDED, result.getStatus());
        assertTrue(result.getItems().contains("minecraft:diamond_sword"));
        assertTrue(result.isEnabled());
        assertEntryCount(read(file), "minecraft:diamond_sword", 1);
    }

    @Test
    @DisplayName("add：按模组 ID 加入 mods")
    void addModNamespace() throws IOException {
        Path file = writeCommon(COMMON_JSON);

        BypassListEditor.Result result = BypassListEditor.add(file, "slashblade");

        assertEquals(BypassListEditor.Status.ADDED, result.getStatus());
        assertTrue(result.getMods().contains("slashblade"));
        assertEntryCount(read(file), "slashblade", 1);
    }

    @Test
    @DisplayName("add：重复添加是幂等的，不重复写条目")
    void addIsIdempotent() throws IOException {
        Path file = writeCommon(COMMON_JSON);

        assertEquals(BypassListEditor.Status.ADDED, BypassListEditor.add(file, "slashblade").getStatus());
        BypassListEditor.Result second = BypassListEditor.add(file, "slashblade");

        assertEquals(BypassListEditor.Status.ALREADY_PRESENT, second.getStatus());
        assertTrue(second.isEnabled());
        assertEntryCount(read(file), "slashblade", 1);
    }

    @Test
    @DisplayName("remove：移出条目；不存在时报告 NOT_PRESENT")
    void removeEntry() throws IOException {
        Path file = writeCommon(COMMON_JSON);
        BypassListEditor.add(file, "minecraft:diamond_sword");

        BypassListEditor.Result removed = BypassListEditor.remove(file, "minecraft:diamond_sword");
        assertEquals(BypassListEditor.Status.REMOVED, removed.getStatus());
        assertFalse(removed.getItems().contains("minecraft:diamond_sword"));
        assertNoEntry(read(file), "minecraft:diamond_sword");

        assertEquals(BypassListEditor.Status.NOT_PRESENT, BypassListEditor.remove(file, "minecraft:diamond_sword").getStatus());
    }

    @Test
    @DisplayName("写回只改 bypass 块，其它配置与字段原样保留")
    void writePreservesOtherConfig() throws IOException {
        Path file = writeCommon(COMMON_JSON);

        BypassListEditor.add(file, "slashblade");

        String content = read(file);
        assertTrue(content.contains("\"styleSelector\""));
        assertTrue(content.contains("\"tooltipLock\""));
        assertTrue(content.contains("\"smoothColor\": true"));
        assertTrue(content.contains("\"onlyText\": \"Vanilla\""));
        assertTrue(content.contains("\"sensitivity\": 10.0"));
    }

    @Test
    @DisplayName("bypass 块缺失时自动补建，并保留 enabled=true 默认值")
    void createsMissingBypassBlock() throws IOException {
        Path file = writeCommon("""
                { "smoothColor": true }
                """);

        BypassListEditor.Result result = BypassListEditor.add(file, "slashblade");

        assertEquals(BypassListEditor.Status.ADDED, result.getStatus());
        assertTrue(result.isEnabled());
        String content = read(file);
        assertTrue(content.contains("\"bypass\""));
        assertTrue(content.contains("\"enabled\": true"));
        assertEntryCount(content, "slashblade", 1);
    }

    @Test
    @DisplayName("bypass.enabled=false 时写回不会把它偷偷改成 true")
    void keepsDisabledSwitch() throws IOException {
        Path file = writeCommon("""
                { "bypass": { "enabled": false, "mods": [], "items": [] } }
                """);

        BypassListEditor.Result result = BypassListEditor.add(file, "slashblade");

        assertFalse(result.isEnabled());
        assertTrue(read(file).contains("\"enabled\": false"));
    }

    @Test
    @DisplayName("已有条目里的空白项被忽略，写回后不残留空串")
    void ignoresBlankExistingEntries() throws IOException {
        Path file = writeCommon("""
                { "bypass": { "enabled": true, "mods": ["  ", "slashblade"], "items": [] } }
                """);

        BypassListEditor.Result result = BypassListEditor.add(file, "minecraft:diamond");

        assertEquals(1, result.getMods().size());
        assertEntryCount(read(file), "slashblade", 1);
    }

    @Test
    @DisplayName("addQuietly：文件不存在时不抛异常，返回 WRITE_FAILED")
    void quietlyHandlesMissingFile() {
        Path missing = tempDir.resolve("does-not-exist").resolve("common.json");

        assertEquals(BypassListEditor.Status.WRITE_FAILED, BypassListEditor.addQuietly(missing, "slashblade").getStatus());
        assertEquals(BypassListEditor.Status.WRITE_FAILED, BypassListEditor.removeQuietly(missing, "slashblade").getStatus());
    }

    @Test
    @DisplayName("addQuietly：非法条目仍抛 IllegalArgumentException（由命令层转成提示）")
    void quietlyStillRejectsInvalidEntry() throws IOException {
        Path file = writeCommon(COMMON_JSON);

        assertThrows(IllegalArgumentException.class, () -> BypassListEditor.addQuietly(file, "!!!"));
    }

    @Test
    @DisplayName("损坏的 JSON 不被静默覆盖")
    void corruptedJsonIsReported() throws IOException {
        Path file = writeCommon("{ this is not json");

        assertThrows(RuntimeException.class, () -> BypassListEditor.add(file, "slashblade"));
    }
}
