package org.yanbwe.colortooltips.tooltip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.yanbwe.colortooltips.RenderingContext;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;

public class ColorHeaderComponent implements net.minecraft.world.inventory.tooltip.TooltipComponent {
    private static final int ITEM_OFFSET = 24;
    private static final int SPACING = 4;

    private final ItemStack itemStack;
    private final Component nameText;
    private final Component rarityText;
    private final int rarityColor;

    public ColorHeaderComponent(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.nameText = Component.literal(itemStack.getItem().getName(itemStack).getString());
        int rarity = RarityRegistry.getNormalizedRarity(itemStack);
        this.rarityColor = RarityColorUtil.getRarityArgbColor(rarity);
        String rarityName = RarityRegistry.getLocalizedRarityTooltip(itemStack.getItem());
        this.rarityText = Component.literal(rarityName);
    }

    public int getHeight() {
        return 24;
    }

    public int getWidth(Font textRenderer) {
        int nameWidth = textRenderer.width(this.nameText);
        int rarityWidth = textRenderer.width(this.rarityText);
        return Math.max(nameWidth, rarityWidth) + ITEM_OFFSET + SPACING;
    }

    public int getTitleOffset() {
        return ITEM_OFFSET;
    }

    public void drawText(Font textRenderer, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource vertexConsumers) {
        float startDrawX = (float) x + ITEM_OFFSET;
        float startDrawY = y + 1;
        textRenderer.drawInBatch(this.nameText.getVisualOrderText(), startDrawX, startDrawY, -1, true, matrix, vertexConsumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        startDrawY += textRenderer.lineHeight + 2;
        textRenderer.drawInBatch(this.rarityText.getVisualOrderText(), startDrawX, startDrawY, this.rarityColor | 0xFF000000, true, matrix, vertexConsumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }

    public void drawItems(Font textRenderer, int x, int y, GuiGraphics context) {
        RenderingContext.startTooltipItemRendering();
        int startDrawX = x + 2;
        int startDrawY = y + 3;
        context.renderItem(this.itemStack, startDrawX, startDrawY);
        context.renderItemDecorations(textRenderer, this.itemStack, startDrawX, startDrawY);
        RenderingContext.endTooltipItemRendering();
    }
}