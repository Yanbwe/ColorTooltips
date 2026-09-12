package org.yanbwe.colortooltips.tooltip;

import net.minecraft.world.item.ItemStack;

/**
 * 提示框渲染判断工具类。
 * <p>
 * 提供物品栈有效性检查，排除搜索物品等虚拟物品栈。
 */
public final class TooltipRenderPolicy {
    private TooltipRenderPolicy() {}

    /**
     * 检查是否有有效的物品栈用于渲染。
     * 排除空栈和虚拟物品（如搜索物品）。
     */
    public static boolean hasValidItemStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !stack.getTooltipImage().isPresent();
    }
}
