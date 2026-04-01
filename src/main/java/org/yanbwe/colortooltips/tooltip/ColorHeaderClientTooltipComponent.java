package org.yanbwe.colortooltips.tooltip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

public class ColorHeaderClientTooltipComponent implements ClientTooltipComponent {
    private final ColorHeaderComponent headerComponent;

    public ColorHeaderClientTooltipComponent(ColorHeaderComponent headerComponent) {
        this.headerComponent = headerComponent;
    }

    public ColorHeaderComponent getHeaderComponent() {
        return this.headerComponent;
    }

    @Override
    public int getWidth(Font font) {
        return this.headerComponent.getWidth(font);
    }

    @Override
    public int getHeight() {
        return this.headerComponent.getHeight();
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource vertexConsumers) {
        this.headerComponent.drawText(font, x, y, matrix, vertexConsumers);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        this.headerComponent.drawItems(font, x, y, graphics);
    }
}