package org.yanbwe.colortooltips.mixin;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.yanbwe.colortooltips.client.TooltipEventHandler;

@Mixin(value = GuiGraphics.class, priority = 1001)
public abstract class GuiGraphicsMixin
{
    @Shadow
    @Final
    private net.minecraft.client.Minecraft minecraft;

    @Shadow(remap = false)
    private ItemStack tooltipStack = ItemStack.EMPTY;

    private static final String SEARCH_TIME_REMAINING = "SearchTimeRemaining";

    private boolean isItemSearching(ItemStack stack) {
        return stack != null && !stack.isEmpty() &&
               stack.hasTag() &&
               stack.getTag().contains(SEARCH_TIME_REMAINING);
    }

    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void onRenderTooltipInternalHead(Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, CallbackInfo info)
    {
        if (tooltipStack.isEmpty() && minecraft.screen != null && minecraft.screen instanceof AbstractContainerScreen<?> containerScreen && containerScreen.getSlotUnderMouse() != null)
        {
            tooltipStack = containerScreen.getSlotUnderMouse().getItem();
        }

        ItemStack stack = !tooltipStack.isEmpty() ? tooltipStack : (components.isEmpty() ? ItemStack.EMPTY : null);
        if (stack != null) {
            if (isItemSearching(stack)) {
                return;
            }
            if (TooltipEventHandler.renderCustomTooltip((GuiGraphics)(Object)this, font, components, x, y, positioner, stack)) {
                info.cancel();
            }
        }
    }

    @Inject(method = "renderTooltipInternal", at = @At(value = "TAIL"))
    private void onRenderTooltipInternalTail(Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, CallbackInfo info)
    {
        tooltipStack = ItemStack.EMPTY;
    }
}