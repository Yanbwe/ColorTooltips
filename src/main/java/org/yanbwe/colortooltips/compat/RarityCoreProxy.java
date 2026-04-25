package org.yanbwe.colortooltips.compat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Method;

/**
 * RarityCore 兼容代理层。
 * <p>
 * 当 RarityCore 已加载时，通过反射调用其 API 获取稀有度颜色等信息。
 * 当 RarityCore 未加载时，返回固定的降级颜色值：
 * <ul>
 *   <li>边框 → 白色 (0xFFFFFFFF)</li>
 *   <li>内外描边、背景 → 黑色 (0xFF000000)</li>
 *   <li>渐变栏 → 灰色 (0xFF808080)</li>
 * </ul>
 * <p>
 * 所有公共方法均可安全地在 RarityCore 缺失时调用，不会抛出异常。
 */
public final class RarityCoreProxy {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RARITY_CORE_MOD_ID = "raritycore";
    private static Boolean loaded = null;

    // 降级颜色常量
    public static final int FALLBACK_BORDER_COLOR = 0xFFFFFFFF;       // 白色
    public static final int FALLBACK_INNER_BORDER_COLOR = 0xFF000000;  // 黑色
    public static final int FALLBACK_BG_COLOR = 0xFF000000;            // 黑色
    public static final int FALLBACK_GRADIENT_BAR_COLOR = 0xFF808080; // 灰色

    private RarityCoreProxy() {}

    /**
     * 检查 RarityCore 模组是否已加载。
     */
    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                loaded = ModList.get() != null && ModList.get().isLoaded(RARITY_CORE_MOD_ID);
            } catch (Exception e) {
                loaded = false;
            }
        }
        return loaded;
    }

    // ========== RarityRegistry.getNormalizedRarity ==========

    private static Method methodGetNormalizedRarityItemStack;
    private static boolean methodGetNormalizedRarityItemStackInit;

    /**
     * 获取物品栈标准化稀有度等级 (1-7)。
     * RarityCore 未加载时返回 1（普通）。
     */
    public static int getNormalizedRarity(ItemStack stack) {
        if (!isLoaded()) return 1;
        try {
            if (!methodGetNormalizedRarityItemStackInit) {
                methodGetNormalizedRarityItemStackInit = true;
                Class<?> clazz = Class.forName("org.yanbwe.raritycore.registry.RarityRegistry");
                methodGetNormalizedRarityItemStack = clazz.getMethod("getNormalizedRarity", ItemStack.class);
            }
            if (methodGetNormalizedRarityItemStack != null) {
                return (int) methodGetNormalizedRarityItemStack.invoke(null, stack);
            }
        } catch (Exception e) {
            LOGGER.debug("RarityCoreProxy: failed to call getNormalizedRarity", e);
        }
        return 1;
    }

    // ========== RarityColorUtil.getRarityArgbColor ==========

    private static Method methodGetRarityArgbColor;
    private static boolean methodGetRarityArgbColorInit;

    /**
     * 根据稀有度等级获取 ARGB 颜色值。
     * RarityCore 未加载时返回白色 (0xFFFFFFFF)。
     */
    public static int getRarityArgbColor(int rarity) {
        if (!isLoaded()) return FALLBACK_BORDER_COLOR;
        try {
            if (!methodGetRarityArgbColorInit) {
                methodGetRarityArgbColorInit = true;
                Class<?> clazz = Class.forName("org.yanbwe.raritycore.util.RarityColorUtil");
                methodGetRarityArgbColor = clazz.getMethod("getRarityArgbColor", int.class);
            }
            if (methodGetRarityArgbColor != null) {
                return (int) methodGetRarityArgbColor.invoke(null, rarity);
            }
        } catch (Exception e) {
            LOGGER.debug("RarityCoreProxy: failed to call getRarityArgbColor", e);
        }
        return FALLBACK_BORDER_COLOR;
    }

    // ========== RarityRegistry.getLocalizedRarityTooltip ==========

    private static Method methodGetLocalizedRarityTooltip;
    private static boolean methodGetLocalizedRarityTooltipInit;

    /**
     * 获取本地化的稀有度提示文本。
     * RarityCore 未加载时返回空字符串（不显示）。
     */
    public static String getLocalizedRarityTooltip(Item item) {
        if (!isLoaded()) return "";
        try {
            if (!methodGetLocalizedRarityTooltipInit) {
                methodGetLocalizedRarityTooltipInit = true;
                Class<?> clazz = Class.forName("org.yanbwe.raritycore.registry.RarityRegistry");
                methodGetLocalizedRarityTooltip = clazz.getMethod("getLocalizedRarityTooltip", Item.class);
            }
            if (methodGetLocalizedRarityTooltip != null) {
                return (String) methodGetLocalizedRarityTooltip.invoke(null, item);
            }
        } catch (Exception e) {
            LOGGER.debug("RarityCoreProxy: failed to call getLocalizedRarityTooltip", e);
        }
        return "";
    }

    // ========== RarityExclusionManager.setRenderingTooltipItem / clear ==========

    private static Method methodSetRenderingTooltipItem;
    private static boolean methodSetRenderingTooltipItemInit;
    private static Method methodClearExclusion;
    private static boolean methodClearExclusionInit;

    /**
     * 标记当前正在渲染提示框中的物品（用于 RarityCore 排除自身渲染）。
     * RarityCore 未加载时无操作。
     */
    public static void startTooltipItemRendering() {
        if (!isLoaded()) return;
        try {
            if (!methodSetRenderingTooltipItemInit) {
                methodSetRenderingTooltipItemInit = true;
                Class<?> clazz = Class.forName("org.yanbwe.raritycore.client.RarityExclusionManager");
                methodSetRenderingTooltipItem = clazz.getMethod("setRenderingTooltipItem", boolean.class);
            }
            if (methodSetRenderingTooltipItem != null) {
                methodSetRenderingTooltipItem.invoke(null, true);
            }
        } catch (Exception e) {
            LOGGER.debug("RarityCoreProxy: failed to call startTooltipItemRendering", e);
        }
    }

    /**
     * 结束提示框物品渲染标记。
     * RarityCore 未加载时无操作。
     */
    public static void endTooltipItemRendering() {
        if (!isLoaded()) return;
        try {
            if (!methodClearExclusionInit) {
                methodClearExclusionInit = true;
                Class<?> clazz = Class.forName("org.yanbwe.raritycore.client.RarityExclusionManager");
                methodClearExclusion = clazz.getMethod("clear");
            }
            if (methodClearExclusion != null) {
                methodClearExclusion.invoke(null);
            }
        } catch (Exception e) {
            LOGGER.debug("RarityCoreProxy: failed to call endTooltipItemRendering", e);
        }
    }
}
