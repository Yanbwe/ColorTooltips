package org.yanbwe.colortooltips.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.Util;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class TooltipRenderer {

    private static float scrollOffset = 0;
    private static long lastRenderTime = 0;
    private static final Map<Integer, int[]> colorCache = new HashMap<>();

    public static void updateScrollOffset() {
        long currentTime = Util.getMillis();
        if (lastRenderTime != 0) {
            long delta = currentTime - lastRenderTime;
            if (delta < 100) {
                scrollOffset += delta * 0.05f; // 调整此值以改变滚动速度
            }
        }
        lastRenderTime = currentTime;
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0f;
        float g = ((rgb >> 8) & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h = 0;
        if (delta != 0) {
            if (max == r) {
                h = (g - b) / delta;
            } else if (max == g) {
                h = 2 + (b - r) / delta;
            } else {
                h = 4 + (r - g) / delta;
            }
            h /= 6;
            if (h < 0) h += 1;
        }
        float s = max == 0 ? 0 : delta / max;
        return new float[]{h, s, max};
    }

    private static int hsvToRgb(float h, float s, float v) {
        int i = (int)(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r = 0, g = 0, b = 0;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            case 5: r = v; g = p; b = q; break;
        }
        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    private static int interpolateColor(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void drawGradientScrollingBorder(GuiGraphics graphics, int x, int y, int width, int height, int rarityColor) {
        int[] colors = colorCache.computeIfAbsent(rarityColor, k -> {
            int[] genColors = new int[4];
            float[] hsv = rgbToHsv(k);
            Random rand = new Random(k);
            for (int i = 0; i < 4; i++) {
                float h = hsv[0] + (rand.nextFloat() * 0.1f - 0.05f);
                if (h < 0) h += 1;
                if (h > 1) h -= 1;
                float s = hsv[1];
                float v = hsv[2] + (rand.nextFloat() * 0.2f - 0.1f);
                if (v < 0) v = 0;
                if (v > 1) v = 1;
                genColors[i] = hsvToRgb(h, s, v);
                genColors[i] = (genColors[i] & 0x00FFFFFF) | (k & 0xFF000000);
            }
            for (int i = 0; i < 4; i++) {
                int swapIdx = rand.nextInt(4);
                int temp = genColors[i];
                genColors[i] = genColors[swapIdx];
                genColors[swapIdx] = temp;
            }
            return genColors;
        });

        int P = 2 * width + 2 * height;
        if (P <= 0) return;

        List<Float> points = new ArrayList<>();
        points.add(0f);
        points.add((float) width);
        points.add((float) (width + height));
        points.add((float) (2 * width + height));
        points.add((float) P);

        float[] cp = new float[4];
        for (int i = 0; i < 4; i++) {
            cp[i] = (scrollOffset + i * P / 4f) % P;
            if (cp[i] < 0) cp[i] += P;
            points.add(cp[i]);
        }

        Collections.sort(points);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();

        for (int i = 0; i < points.size() - 1; i++) {
            float d1 = points.get(i);
            float d2 = points.get(i + 1);
            if (Math.abs(d2 - d1) < 0.1f) continue;

            float relD1 = (d1 - cp[0] + P) % P;
            float relD2 = (d2 - cp[0] + P) % P;
            
            int idx1 = (int) (relD1 / (P / 4f));
            float ratio1 = (relD1 % (P / 4f)) / (P / 4f);
            int color1 = interpolateColor(colors[idx1 % 4], colors[(idx1 + 1) % 4], ratio1);

            int idx2 = (int) (relD2 / (P / 4f));
            float ratio2 = (relD2 % (P / 4f)) / (P / 4f);
            int color2 = interpolateColor(colors[idx2 % 4], colors[(idx2 + 1) % 4], ratio2);

            float a1 = ((color1 >> 24) & 0xFF) / 255f;
            float r1 = ((color1 >> 16) & 0xFF) / 255f;
            float g1 = ((color1 >> 8) & 0xFF) / 255f;
            float b1 = (color1 & 0xFF) / 255f;

            float a2 = ((color2 >> 24) & 0xFF) / 255f;
            float r2 = ((color2 >> 16) & 0xFF) / 255f;
            float g2 = ((color2 >> 8) & 0xFF) / 255f;
            float b2 = (color2 & 0xFF) / 255f;

            if (d1 >= 0 && d2 <= width) {
                // Top edge
                float x1 = x + d1;
                float x2 = x + d2;
                consumer.vertex(matrix, x1, y, 0).color(r1, g1, b1, a1).endVertex();
                consumer.vertex(matrix, x1, y + 1, 0).color(r1, g1, b1, a1).endVertex();
                consumer.vertex(matrix, x2, y + 1, 0).color(r2, g2, b2, a2).endVertex();
                consumer.vertex(matrix, x2, y, 0).color(r2, g2, b2, a2).endVertex();
            } else if (d1 >= width && d2 <= width + height) {
                // Right edge
                float y1 = y + (d1 - width);
                float y2 = y + (d2 - width);
                consumer.vertex(matrix, x + width - 1, y1, 0).color(r1, g1, b1, a1).endVertex();
                consumer.vertex(matrix, x + width - 1, y2, 0).color(r2, g2, b2, a2).endVertex();
                consumer.vertex(matrix, x + width, y2, 0).color(r2, g2, b2, a2).endVertex();
                consumer.vertex(matrix, x + width, y1, 0).color(r1, g1, b1, a1).endVertex();
            } else if (d1 >= width + height && d2 <= 2 * width + height) {
                // Bottom edge (d increases from right to left)
                float x1 = x + width - (d1 - width - height);
                float x2 = x + width - (d2 - width - height);
                // Note: x1 is > x2, so we draw from x2 to x1 to keep standard quad winding, or just keep same winding
                consumer.vertex(matrix, x2, y + height - 1, 0).color(r2, g2, b2, a2).endVertex();
                consumer.vertex(matrix, x2, y + height, 0).color(r2, g2, b2, a2).endVertex();
                consumer.vertex(matrix, x1, y + height, 0).color(r1, g1, b1, a1).endVertex();
                consumer.vertex(matrix, x1, y + height - 1, 0).color(r1, g1, b1, a1).endVertex();
            } else if (d1 >= 2 * width + height && d2 <= P) {
                // Left edge (d increases from bottom to top)
                float y1 = y + height - (d1 - 2 * width - height);
                float y2 = y + height - (d2 - 2 * width - height);
                // Note: y1 is > y2
                consumer.vertex(matrix, x, y2, 0).color(r2, g2, b2, a2).endVertex();
                consumer.vertex(matrix, x, y1, 0).color(r1, g1, b1, a1).endVertex();
                consumer.vertex(matrix, x + 1, y1, 0).color(r1, g1, b1, a1).endVertex();
                consumer.vertex(matrix, x + 1, y2, 0).color(r2, g2, b2, a2).endVertex();
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

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