package org.yanbwe.colortooltips.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.yanbwe.colortooltips.compat.RarityCoreProxy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 样式选择器引擎 — 根据物品栈和提示框模式选择最匹配的样式名。
 * <p>
 * 将选择器逻辑从 ConfigManager 中提取为独立类，遵循单一职责原则。
 * 构造函数接收已解析的选择器配置数据（{@link SelectorBlock}），
 * 由 ConfigManager 在加载 common.json 时传入。
 * <p>
 * 匹配优先级: 物品精确匹配 &gt; 稀有度级别 &gt; "*" 通配 &gt; 降级 "Vanilla"。
 * <p>
 * Style selector engine — selects the best matching style name for an ItemStack.
 * Extracted from ConfigManager for separation of concerns.
 */
public final class StyleSelector {

    private final SelectorBlock commonBlock;
    private final SelectorBlock rarityCoreBlock;

    /**
     * 创建选择器实例。
     *
     * @param commonBlock     未安装 RarityCore 时使用的选择器配置
     * @param rarityCoreBlock 已安装 RarityCore 时使用的选择器配置
     */
    public StyleSelector(SelectorBlock commonBlock, SelectorBlock rarityCoreBlock) {
        this.commonBlock = commonBlock;
        this.rarityCoreBlock = rarityCoreBlock;
    }

    /**
     * 为指定的物品栈选择最匹配的样式名。
     * <p>
     * 内部决策流程：
     * <ol>
     *   <li>RarityCore 已加载 → 使用 {@code rarityCore} 选择器块</li>
     *   <li>RarityCore 未加载 → 使用 {@code common} 选择器块</li>
     *   <li>纯文本提示框 → 返回 {@code onlyText} 配置的样式名</li>
     *   <li>物品注册名精确匹配 → 返回 {@code items} 映射中的样式名</li>
     *   <li>RarityCore 稀有度编号匹配 → 返回对应编号的样式名</li>
     *   <li>原版稀有度名称匹配 → 返回对应稀有度的样式名</li>
     *   <li>"*" 通配默认 → 返回块级 fallback</li>
     *   <li>最终降级 → 返回 {@code "Vanilla"}</li>
     * </ol>
     *
     * @param itemStack 物品栈，为 null 或空时直接返回降级值
     * @param isTextOnly 是否为纯文本提示框模式
     * @return 匹配的样式名字符串，永不为 null（保证返回 "Vanilla"）
     */
    public String selectStyleName(ItemStack itemStack, boolean isTextOnly) {
        // 选择使用哪个选择器块
        SelectorBlock block = RarityCoreProxy.isLoaded() ? rarityCoreBlock : commonBlock;

        // 纯文本提示框 → onlyText 指定的样式名（优先级最高，空物品也适用）
        if (isTextOnly && block != null && block.onlyText != null) {
            return block.onlyText;
        }

        // 空物品降级（仅非纯文本情况下适用）
        if (itemStack == null || itemStack.isEmpty()) {
            return "Vanilla";
        }

        if (block == null) {
            return "Vanilla";
        }

        // 1) 物品注册名精确匹配（最高优先级）
        //    使用 BuiltInRegistries 获取完整注册名如 "minecraft:diamond_sword"
        String registryName = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
        String matched = block.items.get(registryName);
        if (matched != null) {
            return matched;
        }

        // 2) RarityCore 稀有度编号匹配（仅 rarityCore 块有效）
        if (RarityCoreProxy.isLoaded()) {
            int rarity = RarityCoreProxy.getRarity(itemStack);
            matched = block.rarity.get(String.valueOf(rarity));
            if (matched != null) {
                return matched;
            }
        }

        // 3) 原版稀有度名称匹配
        //    Rarity.name() 返回全大写（如 "COMMON"），通过 rarityToJsonKey 转为首字母大写格式
        Rarity rarity = itemStack.getRarity();
        if (rarity != null) {
            matched = block.rarity.get(rarityToJsonKey(rarity));
            if (matched != null) {
                return matched;
            }
        }

        // 4) "*" 通配默认
        if (block.fallback != null) {
            return block.fallback;
        }

        // 5) 最终降级：所有匹配失败时返回预设默认样式名
        return "Vanilla";
    }

    /**
     * 将 {@link Rarity} 枚举值转换为 JSON 配置中的键名（首字母大写）。
     * <p>
     * {@code Rarity.name()} 返回全大写（如 "COMMON"），但 JSON 中使用首字母大写格式（如 "Common"）。
     * <p>
     * Converts a {@link Rarity} enum value to its JSON key name (title case).
     * e.g. {@code Rarity.COMMON} → {@code "Common"}.
     *
     * @param rarity 原版稀有度枚举值
     * @return JSON 配置中对应的键名，如 "Common"、"Uncommon" 等
     */
    private static String rarityToJsonKey(Rarity rarity) {
        if (rarity == Rarity.COMMON) return "Common";
        if (rarity == Rarity.UNCOMMON) return "Uncommon";
        if (rarity == Rarity.RARE) return "Rare";
        if (rarity == Rarity.EPIC) return "Epic";
        // 未知稀有度的降级处理：首字母大写
        String name = rarity.name().toLowerCase();
        if (name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // ══════════════════════════════════════════════════════════
    // 选择器块数据结构
    // ══════════════════════════════════════════════════════════

    /**
     * 一个选择器块的数据结构，对应 common.json 中 {@code styleSelector.common}
     * 或 {@code styleSelector.rarityCore} 的解析结果。
     * <p>
     * 存储 onlyText 样式名、稀有度→样式名映射、物品→样式名映射以及 "*" 通配 fallback。
     * <p>
     * Data structure for a single selector block, parsed from common.json.
     */
    public static class SelectorBlock {
        /** 纯文本提示框模式下使用的样式名 */
        public String onlyText;

        /** "*" 通配符对应的默认样式名，所有稀有度均未匹配时使用 */
        public String fallback;

        /** 稀有度名称或编号 → 样式名，LinkedHashMap 保持插入顺序 */
        public final Map<String, String> rarity = new LinkedHashMap<>();

        /** 物品注册名（如 "minecraft:diamond_sword"）→ 样式名 */
        public final Map<String, String> items = new LinkedHashMap<>();
    }
}
