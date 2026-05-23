package org.yanbwe.colortooltips.tooltip;

import net.minecraft.world.item.ItemStack;
import org.yanbwe.colortooltips.compat.RarityCoreProxy;
import org.yanbwe.colortooltips.config.ConfigManager;
import org.yanbwe.colortooltips.config.StyleDefinition;

/**
 * 智能判断是否渲染提示框的各个组件。
 * <p>
 * v2 更新：将旧版 {@code Config} 引用全部替换为 {@link ConfigManager}；
 * 渐变边框由样式 {@code border.colorFlowSpeed > 0} 判断，
 * 渐变标题栏由样式 {@code titleBar.enabled} 判断。
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
     * 是否应该显示渐变边框。
     * 由样式配置 {@code border.colorFlowSpeed > 0} 决定。
     *
     * @param stack 物品栈
     * @param style 当前应用的样式定义，为 null 时返回 false
     * @return 当 colorFlowSpeed > 0 且物品有效时返回 true
     */
    public static boolean shouldShowGradientBorder(ItemStack stack, StyleDefinition style) {
        if (style == null || !hasValidItemStack(stack)) {
            return false;
        }
        return style.getBorder().getColorFlowSpeed() > 0;
    }

    /**
     * 是否应该显示渐变标题栏。
     * 由样式配置 {@code titleBar.enabled} 决定。
     *
     * @param stack 物品栈
     * @param style 当前应用的样式定义，为 null 时返回 false
     * @return 当 titleBar.enabled 为 true 且物品有效时返回 true
     */
    public static boolean shouldShowGradientTitleBar(ItemStack stack, StyleDefinition style) {
        if (style == null || !hasValidItemStack(stack)) {
            return false;
        }
        return style.getTitleBar().isEnabled();
    }

    /**
     * 是否应该显示稀有度文本
     */
    public static boolean shouldShowRarity(ItemStack stack) {
        // 没有 RarityCore 时隐藏稀有度提示
        if (!RarityCoreProxy.isLoaded()) {
            return false;
        }
        if (!hasValidItemStack(stack)) {
            return false;
        }
        // 检查实际稀有度，不是默认稀有度
        int rarity = RarityCoreProxy.getRarity(stack);
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
     * 这个方法决定了整个提示框样式是否被接管。
     * <p>
     * v2 更新：全局启用开关迁移至 {@link ConfigManager#isEnabled()}。
     */
    public static boolean shouldUseCustomRendering(ItemStack stack) {
        if (!ConfigManager.getInstance().isEnabled()) {
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
     * 没有 RarityCore 时返回白色
     */
    public static int getRarityColor(ItemStack stack) {
        if (!hasValidItemStack(stack)) {
            return 0xFF808080; // 默认灰色
        }
        if (!RarityCoreProxy.isLoaded()) {
            return RarityCoreProxy.FALLBACK_BORDER_COLOR;
        }
        int rarity = RarityCoreProxy.getRarity(stack);
        return RarityCoreProxy.getRarityArgbColor(rarity);
    }

    /**
     * 获取用于渲染的稀有度
     * 无物品时返回稀有度1（普通）
     */
    public static int getRarity(ItemStack stack) {
        if (!hasValidItemStack(stack)) {
            return 1; // 普通稀有度
        }
        return RarityCoreProxy.getRarity(stack);
    }

    /**
     * 获取用于渲染的颜色
     * 无物品时返回稀有度1（普通）的颜色
     */
    public static int getRenderingColor(ItemStack stack) {
        if (!RarityCoreProxy.isLoaded()) {
            return RarityCoreProxy.FALLBACK_BORDER_COLOR;
        }
        int rarity = getRarity(stack);
        return RarityCoreProxy.getRarityArgbColor(rarity);
    }

    /**
     * 检查是否应该显示颜色标题栏（包含稀有度文本和物品模型）
     * 只有有有效物品时才显示
     */
    public static boolean shouldShowColorTitleBar(ItemStack stack) {
        return hasValidItemStack(stack);
    }

    /**
     * 检查是否应该显示彩色边框。
     * 始终显示（即使是纯文本 tooltip）。
     * <p>
     * v2 更新：全局启用开关迁移至 {@link ConfigManager#isEnabled()}。
     */
    public static boolean shouldShowColoredBorder(ItemStack stack) {
        return ConfigManager.getInstance().isEnabled();
    }
}
