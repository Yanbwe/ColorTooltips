package org.yanbwe.colortooltips.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TooltipBypassMatcher} 单元测试（纯逻辑，不依赖游戏环境）。
 */
class TooltipBypassMatcherTest {

    private static final String SLASHBLADE_NS = "slashblade";
    private static final String SLASHBLADE_ID = "slashblade:slashblade";
    private static final String DIAMOND_NS = "minecraft";
    private static final String DIAMOND_ID = "minecraft:diamond_sword";

    @Test
    @DisplayName("空名单不匹配任何物品")
    void emptyListMatchesNothing() {
        TooltipBypassMatcher matcher = TooltipBypassMatcher.empty();

        assertTrue(matcher.isEmpty());
        assertTrue(matcher.isEnabled());
        assertFalse(matcher.matches(SLASHBLADE_NS, SLASHBLADE_ID));
    }

    @Test
    @DisplayName("命中 mods 列表：该命名空间下所有物品都让位")
    void matchesModNamespace() {
        TooltipBypassMatcher matcher = new TooltipBypassMatcher();
        matcher.setLists(true, List.of(SLASHBLADE_NS), null);

        assertTrue(matcher.matches(SLASHBLADE_NS, SLASHBLADE_ID));
        // 同命名空间的其它物品也命中
        assertTrue(matcher.matches(SLASHBLADE_NS, "slashblade:proudsoul"));
        assertFalse(matcher.matches(DIAMOND_NS, DIAMOND_ID));
    }

    @Test
    @DisplayName("命中 items 列表：仅该物品让位")
    void matchesItemRegistryName() {
        TooltipBypassMatcher matcher = new TooltipBypassMatcher();
        matcher.setLists(true, null, List.of(DIAMOND_ID));

        assertTrue(matcher.matches(DIAMOND_NS, DIAMOND_ID));
        // 同命名空间的其它物品不受影响
        assertFalse(matcher.matches(DIAMOND_NS, "minecraft:iron_sword"));
        assertFalse(matcher.matches(SLASHBLADE_NS, SLASHBLADE_ID));
    }

    @Test
    @DisplayName("总开关关闭时名单整体失效")
    void disabledSwitchIgnoresLists() {
        TooltipBypassMatcher matcher = new TooltipBypassMatcher();
        matcher.setLists(false, List.of(SLASHBLADE_NS), List.of(DIAMOND_ID));

        assertFalse(matcher.isEnabled());
        assertFalse(matcher.matches(SLASHBLADE_NS, SLASHBLADE_ID));
        assertFalse(matcher.matches(DIAMOND_NS, DIAMOND_ID));
        // 名单本身仍保留，便于重新开启
        assertFalse(matcher.isEmpty());
        assertEquals(1, matcher.getMods().size());
        assertEquals(1, matcher.getItems().size());
    }

    @Test
    @DisplayName("空值与空串参数不抛异常：注册名具名时仍按 items 名单命中，全空则不匹配")
    void nullAndBlankArgumentsDoNotMatch() {
        TooltipBypassMatcher matcher = new TooltipBypassMatcher();
        matcher.setLists(true, List.of(SLASHBLADE_NS), List.of(DIAMOND_ID));

        // 没有任何可比对的信息 → 不匹配
        assertFalse(matcher.matches(null, null));
        assertFalse(matcher.matches("", ""));
        // 命名空间缺失但注册名可查 → items 名单依然生效
        assertTrue(matcher.matches(null, DIAMOND_ID));
        // 注册名缺失但命名空间可查 → mods 名单依然生效
        assertTrue(matcher.matches(SLASHBLADE_NS, null));
        // 注册名缺失且命名空间不在 mods 名单 → 不匹配
        assertFalse(matcher.matches(DIAMOND_NS, null));
    }

    @Test
    @DisplayName("列表项首尾空白被裁剪，空串条目被忽略")
    void trimsEntriesAndSkipsBlank() {
        TooltipBypassMatcher matcher = new TooltipBypassMatcher();
        matcher.setLists(true, List.of("  " + SLASHBLADE_NS + "  ", ""), List.of("  ", DIAMOND_ID + " "));

        assertEquals(1, matcher.getMods().size());
        assertEquals(1, matcher.getItems().size());
        assertTrue(matcher.matches(SLASHBLADE_NS, SLASHBLADE_ID));
        assertTrue(matcher.matches(DIAMOND_NS, DIAMOND_ID));
    }

    @Test
    @DisplayName("setLists 对 null 列表容错，并可重复调用覆盖旧名单")
    void setListsIsNullSafeAndIdempotent() {
        TooltipBypassMatcher matcher = new TooltipBypassMatcher();
        matcher.setLists(true, null, null);
        assertTrue(matcher.isEmpty());

        matcher.setLists(true, List.of(SLASHBLADE_NS), null);
        assertTrue(matcher.matches(SLASHBLADE_NS, SLASHBLADE_ID));

        // 覆盖后旧条目必须消失，避免热重载后旧名单残留
        matcher.setLists(true, null, List.of(DIAMOND_ID));
        assertFalse(matcher.matches(SLASHBLADE_NS, SLASHBLADE_ID));
        assertTrue(matcher.matches(DIAMOND_NS, DIAMOND_ID));
    }

    @Test
    @DisplayName("getMods/getItems 返回副本，外部修改不影响内部状态")
    void gettersReturnCopies() {
        TooltipBypassMatcher matcher = new TooltipBypassMatcher();
        matcher.setLists(true, List.of(SLASHBLADE_NS), List.of(DIAMOND_ID));

        matcher.getMods().clear();
        matcher.getItems().clear();

        assertTrue(matcher.matches(SLASHBLADE_NS, SLASHBLADE_ID));
        assertTrue(matcher.matches(DIAMOND_NS, DIAMOND_ID));
    }

    @Test
    @DisplayName("匹配区分大小写：注册名本身是小写，大写条目不应命中")
    void matchingIsCaseSensitive() {
        TooltipBypassMatcher matcher = new TooltipBypassMatcher();
        matcher.setLists(true, List.of("SlashBlade"), List.of("minecraft:Diamond_Sword"));

        assertFalse(matcher.matches(SLASHBLADE_NS, SLASHBLADE_ID));
        assertFalse(matcher.matches(DIAMOND_NS, DIAMOND_ID));
    }
}
