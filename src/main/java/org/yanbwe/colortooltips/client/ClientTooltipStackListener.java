package org.yanbwe.colortooltips.client;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import org.yanbwe.colortooltips.events.ColorTooltipPostEvent;
import org.yanbwe.colortooltips.tooltip.TooltipRenderState;

/**
 * 监听主线程监听器：从游戏自身的 {@link RenderTooltipEvent} 中捕获当前悬停的物品栈。
 * <p>
 * {@code GuiGraphics} 可能在提示框渲染之前每帧重建，仅靠 Mixin 内的
 * {@code @Shadow tooltipStack} 捕获容易拿到空/过期的物品。此监听器直接订阅
 * Forge 事件获取权威悬停物品，并通过 {@link TooltipRenderState} 暴露给自定义渲染器，
 * 保证与 Durability101 等模组共同使用时渲染层级/内容一致。
 */
public final class ClientTooltipStackListener {

    private ClientTooltipStackListener() {
    }

    /**
     * 捕获 {@link RenderTooltipEvent} 携带的悬停物品栈。
     *
     * @param event 渲染提示框事件
     */
    public static void onRenderTooltip(RenderTooltipEvent event) {
        if (event == null) {
            return;
        }
        ItemStack stack = event.getStack();
        TooltipRenderState.setTooltipStack(stack != null ? stack : ItemStack.EMPTY);
    }

    /**
     * 捕获 ColorTooltips 自定义渲染完成事件携带的悬停物品栈。
     *
     * @param event ColorTooltips 提示框后置事件
     */
    public static void onColorTooltipPost(ColorTooltipPostEvent event) {
        if (event == null) {
            return;
        }
        ItemStack stack = event.getStack();
        TooltipRenderState.setTooltipStack(stack != null ? stack : ItemStack.EMPTY);
    }
}
