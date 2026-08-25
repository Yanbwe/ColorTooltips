package org.yanbwe.colortooltips.tooltip;

import net.minecraft.world.item.ItemStack;

/**
 * 提示框渲染状态持有器。
 * <p>
 * 保存当前帧用于自定义渲染的物品栈与是否启用自定义渲染的标记，
 * 供 {@code TooltipEventHandler} 等渲染逻辑读取，确保与
 * Durability101 等模组叠加渲染时层级正确。
 */
public final class TooltipRenderState {

    /** 当前帧用于渲染提示框的物品栈（悬停物品），{@code EMPTY} 表示纯文本提示框。 */
    private static ItemStack tooltipStack = ItemStack.EMPTY;

    private TooltipRenderState() {
    }

    /**
     * @return 当前帧用于渲染提示框的物品栈
     */
    public static ItemStack getTooltipStack() {
        return tooltipStack;
    }

    /**
     * 设置当前帧用于渲染提示框的物品栈。
     *
     * @param stack 悬停物品栈，{@code null} 视为 {@link ItemStack#EMPTY}
     */
    public static void setTooltipStack(ItemStack stack) {
        tooltipStack = stack != null ? stack : ItemStack.EMPTY;
    }

    /**
     * 重置渲染状态（切换屏幕/物品丢失时调用）。
     */
    public static void reset() {
        tooltipStack = ItemStack.EMPTY;
    }
}
