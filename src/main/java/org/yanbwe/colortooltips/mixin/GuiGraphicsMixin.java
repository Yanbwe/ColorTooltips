package org.yanbwe.colortooltips.mixin;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.HoverEvent.Action;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.colortooltips.client.TooltipEventHandler;
import org.yanbwe.colortooltips.tooltip.TooltipRenderPolicy;

@Mixin(value = GuiGraphics.class, priority = 1001)
public abstract class GuiGraphicsMixin
{
    @Shadow
    @Final
    private net.minecraft.client.Minecraft minecraft;

    @Shadow(remap = false)
    private ItemStack tooltipStack = ItemStack.EMPTY;

    private boolean isItemSearching(ItemStack stack) {
        return false; // 1.21.1: hasTag/getTag removed, virtual item check handled by TooltipEventHandler.isVirtualItem()
    }

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void onRenderTooltipInternalHead(Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, CallbackInfo info)
    {
        if (tooltipStack.isEmpty() && minecraft.screen != null && minecraft.screen instanceof AbstractContainerScreen<?> containerScreen && containerScreen.getSlotUnderMouse() != null)
        {
            tooltipStack = containerScreen.getSlotUnderMouse().getItem();
        }

        ItemStack stack = !tooltipStack.isEmpty() ? tooltipStack : ItemStack.EMPTY;

        if (stack != null && !stack.isEmpty() && isItemSearching(stack)) {
            return;
        }

        if (TooltipEventHandler.renderCustomTooltip((GuiGraphics)(Object)this, font, components, x, y, positioner, stack)) {
            info.cancel();
        }
    }

    @Inject(method = "renderTooltipInternal", at = @At(value = "TAIL"))
    private void onRenderTooltipInternalTail(Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, CallbackInfo info)
    {
        tooltipStack = ItemStack.EMPTY;
    }

    /**
     * 接管的渲染组件悬停效果（SHOW_TEXT, SHOW_ENTITY, SHOW_ITEM）
     */
    @Inject(method = "renderComponentHoverEffect", at = @At("HEAD"), cancellable = true)
    private void onRenderComponentHoverEffectHead(Font font, net.minecraft.network.chat.Style style, int mouseX, int mouseY, CallbackInfo info)
    {
        if (style == null || style.getHoverEvent() == null) return;

        HoverEvent hoverEvent = style.getHoverEvent();
        HoverEvent.ItemStackInfo showItem = hoverEvent.getValue(Action.SHOW_ITEM);

        if (showItem != null) {
            ItemStack stack = showItem.getItemStack();
            if (TooltipRenderPolicy.hasValidItemStack(stack) && !isItemSearching(stack)) {
                List<Component> tooltipLines = Screen.getTooltipFromItem(this.minecraft, stack);
                List<ClientTooltipComponent> components = tooltipLines.stream()
                        .map(text -> ClientTooltipComponent.create(text.getVisualOrderText()))
                        .collect(java.util.stream.Collectors.toList());

                java.util.Optional<TooltipComponent> tooltipImage = stack.getTooltipImage();
                tooltipImage.ifPresent(component -> components.add(1, ClientTooltipComponent.create(component)));

                if (TooltipEventHandler.renderCustomTooltip((GuiGraphics)(Object)this, font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, stack)) {
                    info.cancel();
                }
            }
        }
    }
}
