package org.yanbwe.colortooltips.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.yanbwe.colortooltips.compat.RarityCoreProxy;

/**
 * 动态颜色与文本内容解析器。
 * <p>
 * 根据 token 字符串和物品栈，解析出 ARGB 颜色值或动态文本内容。
 * 支持 {@code @rarityCore}（RarityCore 稀有度颜色）、{@code @vanillaRarity}（原版稀有度映射）
 * 以及直接 RRGGBB 十六进制颜色值。
 * <p>
 * 所有方法均处理 null 和空字符串输入，RarityCore 未加载时安全降级为默认白色。
 */
public final class DynamicColorResolver {

    /** 默认降级颜色（白色） */
    private static final int DEFAULT_COLOR = 0xFFFFFFFF;

    /** 原版稀有度颜色映射 */
    private static final int VANILLA_COMMON_COLOR   = 0xFFFFFFFF; // 白色
    private static final int VANILLA_UNCOMMON_COLOR = 0xFFFFFF44; // 黄色
    private static final int VANILLA_RARE_COLOR     = 0xFF55FFFF; // 青色
    private static final int VANILLA_EPIC_COLOR     = 0xFFFF55FF; // 紫色

    private DynamicColorResolver() {}

    // ══════════════════════════════════════════════════════════
    // 公共 API
    // ══════════════════════════════════════════════════════════

    /**
     * 根据颜色 token 解析 ARGB 颜色值。
     * <ul>
     *   <li>{@code @rarityCore} → 调用 {@link RarityCoreProxy#getRarityArgbColor(int)}
     *       获取 RarityCore 稀有度颜色（内部已处理 v13 RGB→ARGB 转换）</li>
     *   <li>{@code @vanillaRarity} → 根据 {@link ItemStack#getRarity()} 映射到对应 ARGB 颜色</li>
     *   <li>直接 6 位十六进制（RRGGBB）→ 解析为 ARGB（加 {@code 0xFF} alpha 前缀），
     *       支持可选 {@code #} 前缀</li>
     * </ul>
     *
     * @param colorToken 颜色标识符，可为 null
     * @param itemStack  目标物品栈，可为 null
     * @return ARGB 颜色值，无法解析时返回 {@value #DEFAULT_COLOR}
     */
    public static int resolve(String colorToken, ItemStack itemStack) {
        if (colorToken == null || colorToken.isEmpty()) {
            return DEFAULT_COLOR;
        }

        String trimmed = colorToken.trim();

        if ("@rarityCore".equals(trimmed)) {
            return resolveRarityCoreColor(itemStack);
        }

        if ("@vanillaRarity".equals(trimmed)) {
            return resolveVanillaRarityColor(itemStack);
        }

        return parseHexColor(trimmed);
    }

    /**
     * 解析 FillColorEntry 中的颜色并应用其 colorModifier。
     * <p>
     * 先通过 {@link #resolve(String, ItemStack)} 获取基础颜色，
     * 再通过 {@link ColorModifier#apply(int, double, double)}
     * 应用 brightness/saturation 偏移。若 entry 为 null 则返回默认白色。
     *
     * @param entry     FillColorEntry，包含颜色 token 和可选的修饰参数
     * @param itemStack 目标物品栈，可为 null
     * @return ARGB 颜色值（已应用 colorModifier），无法解析时返回默认白色
     */
    public static int resolve(StyleDefinition.FillColorEntry entry, ItemStack itemStack) {
        if (entry == null) {
            return DEFAULT_COLOR;
        }
        int baseColor = resolve(entry.getColor(), itemStack);
        StyleDefinition.FillColorEntry.ColorModifier modifier = entry.getColorModifier();
        return ColorModifier.apply(baseColor, modifier.getBrightness(), modifier.getSaturation());
    }

    /**
     * 解析动态文本内容 token。
     * <ul>
     *   <li>{@code @rarityCore} → {@link RarityCoreProxy#getLocalizedRarityTooltip(ItemStack)}</li>
     *   <li>{@code @vanillaRarity} → 原版稀有度名称（"Common"/"Uncommon"/"Rare"/"Epic"）</li>
     *   <li>{@code @itemID} → 物品注册名（"namespace:path"）</li>
     *   <li>{@code @modID} → 物品所属模组 ID</li>
     * </ul>
     *
     * @param textToken 文本标识符，可为 null
     * @param itemStack 目标物品栈，可为 null
     * @return 解析后的文本内容，无法解析时返回空字符串
     */
    public static String resolveDynamicContent(String textToken, ItemStack itemStack) {
        if (textToken == null || textToken.isEmpty()) {
            return "";
        }
        if (itemStack == null || itemStack.isEmpty()) {
            return "";
        }

        String trimmed = textToken.trim();

        switch (trimmed) {
            case "@rarityCore":
                return RarityCoreProxy.getLocalizedRarityTooltip(itemStack);
            case "@vanillaRarity":
                return getVanillaRarityName(itemStack);
            case "@itemID":
                return getItemRegistryName(itemStack);
            case "@modID":
                return getModId(itemStack);
            default:
                return "";
        }
    }

    /**
     * 获取物品的原版稀有度本地化名称。
     *
     * @param itemStack 物品栈
     * @return 稀有度名称字符串，无效物品时返回空字符串
     */
    public static String getVanillaRarityName(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return "";
        }
        Rarity rarity = itemStack.getRarity();
        if (rarity == null) {
            return "";
        }
        switch (rarity) {
            case COMMON:   return "Common";
            case UNCOMMON: return "Uncommon";
            case RARE:     return "Rare";
            case EPIC:     return "Epic";
            default:       return rarity.name();
        }
    }

    // ══════════════════════════════════════════════════════════
    // 内部解析方法
    // ══════════════════════════════════════════════════════════

    /**
     * 解析 RarityCore 稀有度颜色。
     * <p>
     * 通过 {@code getRarity(itemStack)} 获取稀有度等级，再通过
     * {@code getRarityArgbColor(rarity)} 获取对应 ARGB 颜色。
     * RarityCoreProxy 内部已处理 v13 的 RGB→ARGB 转换及降级逻辑。
     */
    private static int resolveRarityCoreColor(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return DEFAULT_COLOR;
        }
        return RarityCoreProxy.getRarityArgbColor(RarityCoreProxy.getRarity(itemStack));
    }

    /** 解析原版稀有度颜色（Common=白, Uncommon=黄, Rare=青, Epic=紫）。 */
    private static int resolveVanillaRarityColor(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return DEFAULT_COLOR;
        }
        Rarity rarity = itemStack.getRarity();
        if (rarity == null) {
            return DEFAULT_COLOR;
        }
        switch (rarity) {
            case COMMON:   return VANILLA_COMMON_COLOR;
            case UNCOMMON: return VANILLA_UNCOMMON_COLOR;
            case RARE:     return VANILLA_RARE_COLOR;
            case EPIC:     return VANILLA_EPIC_COLOR;
            default:       return DEFAULT_COLOR;
        }
    }

    /**
     * 解析 RRGGBB 十六进制颜色字符串为 ARGB int。
     * 支持可选 {@code #} 前缀，解析失败返回默认白色。
     */
    private static int parseHexColor(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return DEFAULT_COLOR;
        }
        try {
            int rgb = Integer.parseInt(hex, 16);
            return 0xFF000000 | rgb;
        } catch (NumberFormatException e) {
            return DEFAULT_COLOR;
        }
    }

    /** 获取物品的注册名（格式："namespace:path"）。 */
    private static String getItemRegistryName(ItemStack itemStack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return key != null ? key.toString() : "";
    }

    /** 获取物品注册名的命名空间部分（即模组 ID）。 */
    private static String getModId(ItemStack itemStack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return key != null ? key.getNamespace() : "";
    }
}
