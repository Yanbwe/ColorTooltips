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
 * 当 RarityCore 已加载时，通过反射调用 {@code RarityCoreAPI} 获取稀有度颜色等信息。
 * 当 RarityCore 未加载时，返回固定的降级颜色值：
 * <ul>
 *   <li>边框 → 白色 (0xFFFFFFFF)</li>
 *   <li>内外描边、背景 → 黑色 (0xFF000000)</li>
 *   <li>渐变栏 → 灰色 (0xFF808080)</li>
 * </ul>
 * <p>
 * 所有公共方法均可安全地在 RarityCore 缺失时调用，不会抛出异常。
 *
 * <h3>v13 适配说明</h3>
 * 从 v13 开始，RarityCore 提供了正式公共 API {@code RarityCoreAPI}，所有反射调用已统一迁移到该入口。
 * 颜色系统从 ARGB 改为 RGB，获取颜色后需要手动添加 alpha 通道 (0xFF000000 | rgb)。
 * {@link #getRarity(ItemStack)} 返回原始稀有度值（不限于 1-7），
 * {@link #getNormalizedRarity(ItemStack)} 仍钳制到 1-7 范围。
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

    /** 反射缓存：RarityCoreAPI.getRarity(ItemStack) */
    private static Method methodGetRarity;
    private static boolean methodGetRarityInit;

    /** 反射缓存：RarityCoreAPI.getNormalizedRarity(ItemStack) */
    private static Method methodGetNormalizedRarity;
    private static boolean methodGetNormalizedRarityInit;

    /** 反射缓存：RarityCoreAPI.getRarityColor(int) */
    private static Method methodGetRarityColor;
    private static boolean methodGetRarityColorInit;

    /** 反射缓存：RarityCoreAPI.getLocalizedTooltip(ItemStack) */
    private static Method methodGetLocalizedTooltipStack;
    private static boolean methodGetLocalizedTooltipStackInit;

    /** 反射缓存：RarityCoreAPI.getLocalizedTooltip(Item) */
    private static Method methodGetLocalizedTooltipItem;
    private static boolean methodGetLocalizedTooltipItemInit;

    /**
     * 获取物品栈的原始稀有度等级（不限于 1-7）。
     * <p>
     * v13 新增：通过 {@code RarityCoreAPI.getRarity(ItemStack)} 获取，
     * 返回实际配置的稀有度值（可能 >7）。
     * 颜色获取请配合 {@link #getRarityArgbColor(int)} 使用，该方法内部会正确处理 >7 的情况。
     *
     * @param stack 物品栈
     * @return 原始稀有度等级（未标准化），RarityCore 未加载时返回 1
     */
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

    /**
     * 获取物品栈标准化稀有度等级 (1-7)。
     * <p>
     * 内部通过 {@code RarityCoreAPI.getNormalizedRarity(ItemStack)} 获取。
     * 如果不需要 1-7 的范围限制，请使用 {@link #getRarity(ItemStack)}。
     * RarityCore 未加载时返回 1（普通）。
     */
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

    // ══════════════════════════════════════════════════════════
    // 颜色 — 正式 API (RarityCoreAPI)
    // ══════════════════════════════════════════════════════════

    /**
     * 根据稀有度等级获取 ARGB 颜色值。
     * <p>
     * v13 变化：内部通过 {@code RarityCoreAPI.getRarityColor(int)} 获取 RGB 颜色
     * （通过 RarityClientConfig，支持 >7 等级沿用等级 7 配置），
     * 然后手动添加全不透明 alpha 通道 (0xFF000000 | rgb)。
     *
     * @param rarity 稀有度等级（不限于 1-7，>7 会沿用等级 7 的颜色）
     * @return ARGB 颜色值 (0xAARRGGBB)
     */
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

    // ══════════════════════════════════════════════════════════
    // 本地化稀有度文本 — 正式 API (RarityCoreAPI)
    // ══════════════════════════════════════════════════════════

    /**
     * 获取物品栈的本地化稀有度提示文本。
     * <p>
     * v13 新增：通过 {@code RarityCoreAPI.getLocalizedTooltip(ItemStack)} 获取，
     * 支持 >7 特殊稀有度文本（如 "[8级稀有度-⭐⭐⭐⭐⭐⭐⭐⭐]"）。
     *
     * @param itemStack 物品栈
     * @return 本地化的稀有度提示字符串，RarityCore 未加载或失败时返回空字符串
     */
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

    /**
     * 获取物品的本地化稀有度提示文本。
     * <p>
     * 委托给 {@link #getLocalizedRarityTooltip(ItemStack)} 实现。
     * 如果已有 ItemStack 实例，推荐使用 {@link #getLocalizedRarityTooltip(ItemStack)}，
     * 因为它能检测 NBT 稀有度（如神化模组）。
     *
     * @param item 物品
     * @return 本地化的稀有度提示字符串，RarityCore 未加载或失败时返回空字符串
     */
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
    // 渲染排除管理（保留原反射实现，RarityCoreAPI 未暴露此方法）
    // ══════════════════════════════════════════════════════════

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
