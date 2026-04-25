package org.yanbwe.colortooltips.tooltip;

import net.minecraft.world.item.ItemStack;
import org.yanbwe.colortooltips.Config;
import org.yanbwe.raritycore.registry.RarityRegistry;

/**
 * 智能判断是否渲染提示框的各个组件
 */
public final class TooltipRenderPolicy {
    private TooltipRenderPolicy() {}

    /**
     * 检查是否有有效的物品堆用于渲染
     */
    public static boolean hasValidItemStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !isVirtualItem(stack);
    }

    /**
     * 检查是否是虚拟物品（如搜索物品）
     */
    public static boolean isVirtualItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getTooltipImage().isPresent();
    }

    /**
     * 是否应该显示渐变边框
     */
    public static boolean shouldShowGradientBorder(ItemStack stack) {
        if (!Config.BORDER_GRADIENT_ENABLED.get()) {
            return false;
        }
        return hasValidItemStack(stack);
    }

    /**
     * 是否应该显示渐变标题栏
     */
    public static boolean shouldShowGradientTitleBar(ItemStack stack) {
        if (!Config.TITLEBAR_GRADIENT_ENABLED.get()) {
            return false;
        }
        return hasValidItemStack(stack);
    }

    /**
     * 是否应该显示稀有度文本
     */
    public static boolean shouldShowRarity(ItemStack stack) {
        if (!hasValidItemStack(stack)) {
            return false;
        }
        // 检查实际稀有度，不是默认稀有度
        int rarity = RarityRegistry.getNormalizedRarity(stack);
        return rarity > 0;
    }

    /**
     * 是否应该显示物品模型
     * 只有有有效物品时才显示物品模型
     */
    public static boolean shouldShowItemModel(ItemStack stack) {
        return hasValidItemStack(stack);
    }

    /**
     * 是否应该显示自定义渲染的彩色提示框
     * 这个方法决定了整个提示框样式是否被接管
     */
    public static boolean shouldUseCustomRendering(ItemStack stack) {
        if (!Config.ENABLED.get()) {
            return false;
        }
        // 虚拟物品不使用自定义渲染
        if (isVirtualItem(stack)) {
            return false;
        }
        return true;
    }

    /**
     * 获取用于渲染的物品堆
     * 如果没有有效的物品堆，返回空物品堆
     */
    public static ItemStack getEffectiveItemStack(ItemStack stack) {
        if (hasValidItemStack(stack)) {
            return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 获取稀有度颜色
     * 如果没有有效物品，返回默认灰色
     */
    public static int getRarityColor(ItemStack stack) {
        if (!hasValidItemStack(stack)) {
            return 0xFF808080; // 默认灰色
        }
        int rarity = RarityRegistry.getNormalizedRarity(stack);
        return org.yanbwe.raritycore.util.RarityColorUtil.getRarityArgbColor(rarity);
    }

    /**
     * 获取用于渲染的稀有度
     * 无物品时返回稀有度1（普通）
     */
    public static int getRarity(ItemStack stack) {
        if (!hasValidItemStack(stack)) {
            return 1; // 普通稀有度
        }
        return RarityRegistry.getNormalizedRarity(stack);
    }

    /**
     * 获取用于渲染的颜色
     * 无物品时返回稀有度1（普通）的颜色
     */
    public static int getRenderingColor(ItemStack stack) {
        int rarity = getRarity(stack);
        return org.yanbwe.raritycore.util.RarityColorUtil.getRarityArgbColor(rarity);
    }

    /**
     * 检查是否应该显示颜色标题栏（包含稀有度文本和物品模型）
     * 只有有有效物品时才显示
     */
    public static boolean shouldShowColorTitleBar(ItemStack stack) {
        return hasValidItemStack(stack);
    }

    /**
     * 检查是否应该显示彩色边框
     * 始终显示（即使是纯文本tooltip）
     */
    public static boolean shouldShowColoredBorder(ItemStack stack) {
        return Config.ENABLED.get();
    }
}