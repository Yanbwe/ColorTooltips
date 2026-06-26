package org.yanbwe.colortooltips.compat;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.lang.reflect.Method;

/**
 * RarityCore 兼容代理层。
 * <p>
 * 当 RarityCore 已加载时，通过反射调用 {@code RarityCoreAPI} 获取稀有度颜色等信息。
 * 当 RarityCore 未加载时，返回固定的降级颜色值。
 * <p>
 * 所有公共方法均可安全地在 RarityCore 缺失时调用，不会抛出异常。
 */
public final class RarityCoreProxy {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RARITY_CORE_MOD_ID = "raritycore";
    private static final String API_CLASS_NAME = "org.yanbwe.raritycore.api.RarityCoreAPI";
    private static Boolean loaded = null;

    // 降级颜色常量
    public static final int FALLBACK_BORDER_COLOR = 0xFFFFFFFF;       // 白色
    public static final int FALLBACK_INNER_BORDER_COLOR = 0xFF000000;  // 黑色
    public static final int FALLBACK_BG_COLOR = 0xFF000000;            // 黑色
    public static final int FALLBACK_GRADIENT_BAR_COLOR = 0xFF808080; // 灰色

    private static Class<?> apiClass;

    private RarityCoreProxy() {}

    /**
     * 检查 RarityCore 模组是否已加载。
     */
    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                loaded = ModList.get() != null && ModList.get().isLoaded(RARITY_CORE_MOD_ID);
                if (loaded) {
                    apiClass = Class.forName(API_CLASS_NAME);
                }
            } catch (Exception e) {
                loaded = false;
            }
        }
        return loaded;
    }

    // ══════════════════════════════════════════════════════════
    // 稀有度查询 — 正式 API (RarityCoreAPI)
    // ══════════════════════════════════════════════════════════

    private static Method methodGetRarity;
    private static boolean methodGetRarityInit;
    private static Method methodGetNormalizedRarity;
    private static boolean methodGetNormalizedRarityInit;
    private static Method methodGetRarityColor;
    private static boolean methodGetRarityColorInit;
    private static Method methodGetLocalizedTooltipStack;
    private static boolean methodGetLocalizedTooltipStackInit;
    private static Method methodGetLocalizedTooltipItem;
    private static boolean methodGetLocalizedTooltipItemInit;

    public static int getRarity(ItemStack stack) {
        if (!isLoaded()) return 1;
        try {
            if (!methodGetRarityInit) {
                methodGetRarityInit = true;
                methodGetRarity = apiClass.getMethod("getRarity", ItemStack.class);
            }
            if (methodGetRarity != null) {
                return (int) methodGetRarity.invoke(null, stack);
            }
        } catch (Exception e) {
            LOGGER.debug("RarityCoreProxy: failed to call getRarity(ItemStack)", e);
        }
        return 1;
    }

    public static int getNormalizedRarity(ItemStack stack) {
        if (!isLoaded()) return 1;
        try {
            if (!methodGetNormalizedRarityInit) {
                methodGetNormalizedRarityInit = true;
                methodGetNormalizedRarity = apiClass.getMethod("getNormalizedRarity", ItemStack.class);
            }
            if (methodGetNormalizedRarity != null) {
                return (int) methodGetNormalizedRarity.invoke(null, stack);
            }
        } catch (Exception e) {
            LOGGER.debug("RarityCoreProxy: failed to call getNormalizedRarity", e);
        }
        return 1;
    }

    public static int getRarityArgbColor(int rarity) {
        if (!isLoaded()) return FALLBACK_BORDER_COLOR;
        try {
            if (!methodGetRarityColorInit) {
                methodGetRarityColorInit = true;
                methodGetRarityColor = apiClass.getMethod("getRarityColor", int.class);
            }
            if (methodGetRarityColor != null) {
                int rgb = (int) methodGetRarityColor.invoke(null, rarity);
                return 0xFF000000 | (rgb & 0xFFFFFF);
            }
        } catch (Exception e) {
            LOGGER.debug("RarityCoreProxy: failed to call getRarityColor", e);
        }
        return FALLBACK_BORDER_COLOR;
    }

    public static String getLocalizedRarityTooltip(ItemStack itemStack) {
        if (!isLoaded() || itemStack == null || itemStack.isEmpty()) return "";
        try {
            if (!methodGetLocalizedTooltipStackInit) {
                methodGetLocalizedTooltipStackInit = true;
                methodGetLocalizedTooltipStack = apiClass.getMethod("getLocalizedTooltip", ItemStack.class);
            }
            if (methodGetLocalizedTooltipStack != null) {
                return (String) methodGetLocalizedTooltipStack.invoke(null, itemStack);
            }
        } catch (Exception e) {
            LOGGER.debug("RarityCoreProxy: failed to call getLocalizedTooltip(ItemStack)", e);
        }
        return "";
    }

    public static String getLocalizedRarityTooltip(Item item) {
        if (!isLoaded() || item == null) return "";
        try {
            if (!methodGetLocalizedTooltipItemInit) {
                methodGetLocalizedTooltipItemInit = true;
                methodGetLocalizedTooltipItem = apiClass.getMethod("getLocalizedTooltip", Item.class);
            }
            if (methodGetLocalizedTooltipItem != null) {
                return (String) methodGetLocalizedTooltipItem.invoke(null, item);
            }
        } catch (Exception e) {
            LOGGER.debug("RarityCoreProxy: failed to call getLocalizedTooltip(Item)", e);
        }
        return "";
    }

    // ══════════════════════════════════════════════════════════
    // 渲染排除管理
    // ══════════════════════════════════════════════════════════

    private static Method methodSetRenderingTooltipItem;
    private static boolean methodSetRenderingTooltipItemInit;
    private static Method methodClearExclusion;
    private static boolean methodClearExclusionInit;

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
