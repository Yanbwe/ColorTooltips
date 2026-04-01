package org.yanbwe.colortooltips.client;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.yanbwe.colortooltips.Config;
import org.yanbwe.colortooltips.tooltip.ColorHeaderComponent;
import org.yanbwe.colortooltips.util.ColorUtils;
import org.yanbwe.colortooltips.util.TooltipRenderer;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;

import java.util.List;

public class TooltipEventHandler {

    private static int currentBorderColor = 0;
    private static int currentBgColor = 0;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        if (!Config.ENABLED.get()) {
            return;
        }

        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        var tooltipElements = event.getTooltipElements();

        boolean hasTextComponent = !tooltipElements.isEmpty() && tooltipElements.get(0).left().isPresent();

        if (hasTextComponent) {
            tooltipElements.remove(0);
        }

        tooltipElements.add(0, Either.right(net.minecraft.world.inventory.tooltip.TooltipComponent.class.cast(new ColorHeaderComponent(itemStack))));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderTooltipColor(RenderTooltipEvent.Color event) {
        if (!Config.ENABLED.get()) {
            return;
        }

        var itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        int rarity = RarityRegistry.getNormalizedRarity(itemStack);
        int borderColor = RarityColorUtil.getRarityArgbColor(rarity);

        int bgColor = ColorUtils.withAlpha(
            ColorUtils.darkenColor(borderColor, 1.0f - Config.BG_DARKEN.get().floatValue()),
            Config.BG_ALPHA.get().floatValue()
        );

        currentBorderColor = borderColor;
        currentBgColor = bgColor;

        event.setBorderStart(borderColor);
        event.setBorderEnd(borderColor);
        event.setBackgroundStart(bgColor);
        event.setBackgroundEnd(bgColor);
    }

    public static boolean renderCustomTooltip(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, ItemStack stack) {
        if (!Config.ENABLED.get()) {
            return false;
        }

        if (components.isEmpty()) {
            return false;
        }

        int rarity = RarityRegistry.getNormalizedRarity(stack);
        int borderColor = RarityColorUtil.getRarityArgbColor(rarity);
        int darkenedBorderColor = ColorUtils.darkenColor(borderColor, 0.5f);
        int bgColor = ColorUtils.withAlpha(
            ColorUtils.darkenColor(borderColor, 1.0f - Config.BG_DARKEN.get().floatValue()),
            Config.BG_ALPHA.get().floatValue()
        );

        currentBorderColor = borderColor;
        currentBgColor = bgColor;

        int contentWidth = 0;
        int contentHeight = 0;
        for (ClientTooltipComponent component : components) {
            contentWidth = Math.max(contentWidth, component.getWidth(font));
            contentHeight += component.getHeight();
        }

        boolean hasExtraContent = components.size() > 1;
        int titleBarExtraHeight = hasExtraContent ? 1 : 0;
        int heightAdjust = hasExtraContent ? 0 : -2;

        int borderSize = 1;
        int innerPadding = 2;
        int width = contentWidth + borderSize * 2 + innerPadding * 2;
        int height = contentHeight + borderSize * 2 + innerPadding * 2 + heightAdjust;

        var pos = positioner.positionTooltip(graphics.guiWidth(), graphics.guiHeight(), x, y, width, height);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 400.0);

        int innerX = pos.x() + borderSize + innerPadding;
        int innerY = pos.y() + borderSize + innerPadding;

        TooltipRenderer.drawOuterBorder(graphics, pos.x(), pos.y(), width, height, darkenedBorderColor);
        TooltipRenderer.drawRarityBorder(graphics, pos.x(), pos.y(), width, height, borderColor);
        TooltipRenderer.drawInnerBorder(graphics, pos.x(), pos.y(), width, height, darkenedBorderColor);

         graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, pos.x(), pos.y(), width, height, bgColor));
         graphics.drawManaged(() -> TooltipRenderer.drawGradientTitleBar(graphics, pos.x(), pos.y(), width, height, borderColor));

        int componentX = innerX;
        int componentY = innerY;

        for (int i = 0; i < components.size(); i++) {
            if (i == 1) {
                componentY += titleBarExtraHeight;
            }
            ClientTooltipComponent component = components.get(i);
            component.renderText(font, componentX, componentY, graphics.pose().last().pose(), graphics.bufferSource());
            component.renderImage(font, componentX, componentY, graphics);
            componentY += component.getHeight();
        }

        graphics.flush();
        graphics.pose().popPose();

        return true;
    }
}