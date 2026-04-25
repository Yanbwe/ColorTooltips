package org.yanbwe.colortooltips.compat;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;

/**
 * Apotheosis（神化）模组兼容层。
 * <p>
 * 通过类名字符串比较检测神化模组的特殊屏幕和槽位，
 * 无需在编译期依赖 Apotheosis。
 * <p>
 * 当前兼容的屏幕和槽位：
 * <ul>
 *   <li>重铸台（ReforgingScreen）— 重铸结果槽（ReforgingResultSlot）</li>
 *   <li>回收台（SalvagingScreen）— drawOnLeft 渲染回收结果提示</li>
 *   <li>宝石切割台（GemCuttingScreen）— drawOnLeft 渲染升级费用提示</li>
 * </ul>
 * 检测到这些场景时，ColorTooltips 完全放行，两套提示框都由原版处理。
 */
public final class ApotheosisCompat {

    // ========== 槽位类名 ==========

    /**
     * ReforgingResultSlot (重铸结果槽) 全限定名
     */
    private static final String REFORGING_RESULT_SLOT_CLASS_NAME =
        "dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingMenu$ReforgingResultSlot";

    // ========== 屏幕类名 ==========

    /**
     * SalvagingScreen (回收台) 全限定名
     */
    private static final String SALVAGING_SCREEN_CLASS_NAME =
        "dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingScreen";

    /**
     * ReforgingScreen (重铸台) 全限定名
     */
    private static final String REFORGING_SCREEN_CLASS_NAME =
        "dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingScreen";

    /**
     * GemCuttingScreen (宝石切割台) 全限定名
     */
    private static final String GEM_CUTTING_SCREEN_CLASS_NAME =
        "dev.shadowsoffire.apotheosis.adventure.socket.gem.cutting.GemCuttingScreen";

    private ApotheosisCompat() {}

    /**
     * 判断指定槽位是否为神化重铸结果槽。
     */
    public static boolean isReforgingResultSlot(Slot slot) {
        if (slot == null) return false;
        return REFORGING_RESULT_SLOT_CLASS_NAME.equals(slot.getClass().getName());
    }

    /**
     * 判断当前屏幕是否为神化回收台。
     */
    public static boolean isSalvagingScreen(Screen screen) {
        if (screen == null) return false;
        return SALVAGING_SCREEN_CLASS_NAME.equals(screen.getClass().getName());
    }

    /**
     * 判断当前屏幕是否为神化重铸台。
     */
    public static boolean isReforgingScreen(Screen screen) {
        if (screen == null) return false;
        return REFORGING_SCREEN_CLASS_NAME.equals(screen.getClass().getName());
    }

    /**
     * 综合检测：当鼠标位于重铸结果槽 或 当前为回收台时，应跳过 ColorTooltips 处理。
     * 这两种场景下 Apotheosis 都会通过 drawOnLeft 自行渲染一套提示框，
     * ColorTooltips 不应干涉，避免叠加。
     */
    public static boolean shouldSkipTooltip(Screen screen, Slot slot) {
        if (screen == null) return false;
        String screenClassName = screen.getClass().getName();

        // 回收台 / 宝石切割台：所有槽位都跳过
        if (SALVAGING_SCREEN_CLASS_NAME.equals(screenClassName) || GEM_CUTTING_SCREEN_CLASS_NAME.equals(screenClassName)) {
            return true;
        }

        // 重铸台：仅在重铸结果槽上跳过（输入槽仍需 ColorTooltips）
        if (REFORGING_SCREEN_CLASS_NAME.equals(screenClassName)) {
            return isReforgingResultSlot(slot);
        }

        return false;
    }

    /**
     * 对指定屏幕类全限定名做 equals 比较（不存在继承关系，全名匹配即可）。
     */
    private static boolean isScreen(Screen screen, String className) {
        return screen != null && className.equals(screen.getClass().getName());
    }
}
