package org.yanbwe.colortooltips.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

public class TooltipRenderer {

    public static void drawOuterBorder(GuiGraphics graphics, int x, int y, int width, int height, int borderColor) {
        int outerBorderColor = (borderColor & 0x00FFFFFF) | 0x80000000;
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, outerBorderColor);
    }

    public static void drawInnerBorder(GuiGraphics graphics, int x, int y, int width, int height, int borderColor) {
        int innerBorderColor = (borderColor & 0x00FFFFFF) | 0x80000000;
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, innerBorderColor);
    }

    public static void drawRarityBorder(GuiGraphics graphics, int x, int y, int width, int height, int borderColor) {
        graphics.fillGradient(x, y, x + width, y + 1, borderColor, borderColor);
        graphics.fillGradient(x, y + height - 1, x + width, y + height, borderColor, borderColor);
        graphics.fillGradient(x, y, x + 1, y + height, borderColor, borderColor);
        graphics.fillGradient(x + width - 1, y, x + width, y + height, borderColor, borderColor);
    }

    public static void drawBackground(GuiGraphics graphics, int x, int y, int width, int height, int bgColor) {
        int bgX1 = x + 2;
        int bgY1 = y + 2;
        int bgX2 = x + width - 2;
        int bgY2 = y + height - 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        graphics.fill(bgX1, bgY1, bgX2, bgY2, bgColor);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void drawGradientTitleBar(GuiGraphics graphics, int x, int y, int width, int height, int rarityColor) {
        int barX1 = x + 2;
        int barY1 = y + 2;
        int barX2 = x + width - 2;
        int barY2 = barY1 + 24;

        int leftColor = rarityColor;
        int rightColor = rarityColor & 0x00FFFFFF;

        float leftA = ((leftColor >> 24) & 0xff) / 255f;
        float leftR = ((leftColor >> 16) & 0xff) / 255f;
        float leftG = ((leftColor >> 8) & 0xff) / 255f;
        float leftB = (leftColor & 0xff) / 255f;

        float rightA = ((rightColor >> 24) & 0xff) / 255f;
        float rightR = ((rightColor >> 16) & 0xff) / 255f;
        float rightG = ((rightColor >> 8) & 0xff) / 255f;
        float rightB = (rightColor & 0xff) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();

        consumer.vertex(matrix, barX1, barY1, 0).color(leftR, leftG, leftB, leftA).endVertex();
        consumer.vertex(matrix, barX1, barY2, 0).color(leftR, leftG, leftB, leftA).endVertex();
        consumer.vertex(matrix, barX2, barY2, 0).color(rightR, rightG, rightB, rightA).endVertex();
        consumer.vertex(matrix, barX2, barY1, 0).color(rightR, rightG, rightB, rightA).endVertex();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}