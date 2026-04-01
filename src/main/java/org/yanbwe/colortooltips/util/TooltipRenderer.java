package org.yanbwe.colortooltips.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.Util;
import org.yanbwe.colortooltips.Config;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class TooltipRenderer {

    private static float scrollOffset = 0;
    private static long lastRenderTime = 0;
    // 缓存每种稀有度对应的 HSV 偏移量,避免每帧重复计算且保证过渡平滑
    private static final Map<Integer, float[][]> hsvOffsetCache = new HashMap<>();

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

    public static void drawGradientScrollingBorder(GuiGraphics graphics, int x, int y, int width, int height, int rarityColor, float fadeAlpha) {
        int targetColor = TooltipAnimationManager.getTargetBorderColor();
        float[][] offsets = hsvOffsetCache.computeIfAbsent(targetColor, k -> {
            float[][] genOffsets = new float[4][3];
            Random rand = new Random(k);
            for (int i = 0; i < 4; i++) {
                float hueVariation = Config.HUE_VARIATION.get().floatValue();
                float valueVariation = Config.VALUE_VARIATION.get().floatValue();
                float saturationVariation = Config.SATURATION_VARIATION.get().floatValue();
                
                genOffsets[i][0] = rand.nextFloat() * hueVariation - hueVariation / 2;
                genOffsets[i][1] = rand.nextFloat() * saturationVariation - saturationVariation / 2;
                genOffsets[i][2] = rand.nextFloat() * valueVariation - valueVariation / 2;
            }
            // 固定打乱顺序
            Random shuffleRand = new Random(k);
            for (int i = 0; i < 4; i++) {
                int swapIdx = shuffleRand.nextInt(4);
                float[] temp = genOffsets[i];
                genOffsets[i] = genOffsets[swapIdx];
                genOffsets[swapIdx] = temp;
            }
            return genOffsets;
        });

        // 基于当前的插值颜色,应用稳定的 HSV 偏移量
        int[] colors = new int[4];
        float[] baseHsv = rgbToHsv(rarityColor);
        int alphaPart = rarityColor & 0xFF000000;
        for (int i = 0; i < 4; i++) {
            float h = (baseHsv[0] + offsets[i][0] + 1.0f) % 1.0f;
            float s = Math.max(0, Math.min(1.0f, baseHsv[1] + offsets[i][1]));
            float v = Math.max(0, Math.min(1.0f, baseHsv[2] + offsets[i][2]));
            colors[i] = (hsvToRgb(h, s, v) & 0x00FFFFFF) | alphaPart;
        }

        int targetWidth = TooltipAnimationManager.isAnimating() ? TooltipAnimationManager.getTargetWidth() : width;
        int targetHeight = TooltipAnimationManager.isAnimating() ? TooltipAnimationManager.getTargetHeight() : height;
        
        int P = 2 * width + 2 * height;
        int targetP = 2 * targetWidth + 2 * targetHeight;
        
        if (P <= 0 || targetP <= 0) return;

        List<Float> points = new ArrayList<>();
        points.add(0f);
        points.add((float) width);
        points.add((float) (width + height));
        points.add((float) (2 * width + height));
        points.add((float) P);

        float[] cp = new float[4];
        for (int i = 0; i < 4; i++) {
            float targetCpDist = (scrollOffset + i * targetP / 4f) % targetP;
            if (targetCpDist < 0) targetCpDist += targetP;
            
            cp[i] = targetCpDist * (P / (float) targetP);
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

            float a1 = (((color1 >> 24) & 0xFF) / 255f) * fadeAlpha;
            float r1 = ((color1 >> 16) & 0xFF) / 255f;
            float g1 = ((color1 >> 8) & 0xFF) / 255f;
            float b1 = (color1 & 0xFF) / 255f;

            float a2 = (((color2 >> 24) & 0xFF) / 255f) * fadeAlpha;
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
                consumer.vertex(matrix, x2, y + height - 1, 0).color(r2, g2, b2, a2).endVertex();
                consumer.vertex(matrix, x2, y + height, 0).color(r2, g2, b2, a2).endVertex();
                consumer.vertex(matrix, x1, y + height, 0).color(r1, g1, b1, a1).endVertex();
                consumer.vertex(matrix, x1, y + height - 1, 0).color(r1, g1, b1, a1).endVertex();
            } else if (d1 >= 2 * width + height && d2 <= P) {
                // Left edge (d increases from bottom to top)
                float y1 = y + height - (d1 - 2 * width - height);
                float y2 = y + height - (d2 - 2 * width - height);
                consumer.vertex(matrix, x, y2, 0).color(r2, g2, b2, a2).endVertex();
                consumer.vertex(matrix, x, y1, 0).color(r1, g1, b1, a1).endVertex();
                consumer.vertex(matrix, x + 1, y1, 0).color(r1, g1, b1, a1).endVertex();
                consumer.vertex(matrix, x + 1, y2, 0).color(r2, g2, b2, a2).endVertex();
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void drawOuterBorder(GuiGraphics graphics, int x, int y, int width, int height, int borderColor, float fadeAlpha) {
        int alpha = (int) (((borderColor >> 24) & 0xFF) * fadeAlpha);
        int outerBorderColor = (borderColor & 0x00FFFFFF) | (alpha << 24);
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        graphics.fill(x - 1, y - 1, x + width + 1, y, outerBorderColor);
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, outerBorderColor);
        graphics.fill(x - 1, y, x, y + height, outerBorderColor);
        graphics.fill(x + width, y, x + width + 1, y + height, outerBorderColor);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void drawInnerBorder(GuiGraphics graphics, int x, int y, int width, int height, int borderColor, float fadeAlpha) {
        int alpha = (int) (((borderColor >> 24) & 0xFF) * fadeAlpha);
        int innerBorderColor = (borderColor & 0x00FFFFFF) | (alpha << 24);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, innerBorderColor);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, innerBorderColor);
        graphics.fill(x + 1, y + 2, x + 2, y + height - 2, innerBorderColor);
        graphics.fill(x + width - 2, y + 2, x + width - 1, y + height - 2, innerBorderColor);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void drawRarityBorder(GuiGraphics graphics, int x, int y, int width, int height, int borderColor, float fadeAlpha) {
        int alpha = (int) (((borderColor >> 24) & 0xFF) * fadeAlpha);
        int finalColor = (borderColor & 0x00FFFFFF) | (alpha << 24);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        graphics.fillGradient(x, y, x + width, y + 1, finalColor, finalColor);
        graphics.fillGradient(x, y + height - 1, x + width, y + height, finalColor, finalColor);
        graphics.fillGradient(x, y, x + 1, y + height, finalColor, finalColor);
        graphics.fillGradient(x + width - 1, y, x + width, y + height, finalColor, finalColor);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void drawBackground(GuiGraphics graphics, int x, int y, int width, int height, int bgColor, float fadeAlpha) {
        int alpha = (int) (((bgColor >> 24) & 0xFF) * fadeAlpha);
        int finalColor = (bgColor & 0x00FFFFFF) | (alpha << 24);

        int bgX1 = x + 2;
        int bgY1 = y + 2;
        int bgX2 = x + width - 2;
        int bgY2 = y + height - 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        graphics.fill(bgX1, bgY1, bgX2, bgY2, finalColor);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void drawGradientTitleBar(GuiGraphics graphics, int x, int y, int width, int height, int rarityColor, float fadeAlpha) {
        int targetColor = TooltipAnimationManager.getTargetBorderColor();
        float[][] offsets = hsvOffsetCache.computeIfAbsent(targetColor + 1, k -> {
            float[][] genOffsets = new float[4][3];
            Random rand = new Random(k);
            for (int i = 0; i < 4; i++) {
                float hueVariation = Config.HUE_VARIATION.get().floatValue();
                float valueVariation = Config.VALUE_VARIATION.get().floatValue();
                float saturationVariation = Config.SATURATION_VARIATION.get().floatValue();
                
                genOffsets[i][0] = rand.nextFloat() * hueVariation - hueVariation / 2;
                genOffsets[i][1] = rand.nextFloat() * saturationVariation - saturationVariation / 2;
                genOffsets[i][2] = rand.nextFloat() * valueVariation - valueVariation / 2;
            }
            Random shuffleRand = new Random(k);
            for (int i = 0; i < 4; i++) {
                int swapIdx = shuffleRand.nextInt(4);
                float[] temp = genOffsets[i];
                genOffsets[i] = genOffsets[swapIdx];
                genOffsets[swapIdx] = temp;
            }
            return genOffsets;
        });

        // 基于当前的插值颜色,应用稳定的 HSV 偏移量
        int[] colors = new int[4];
        float[] baseHsv = rgbToHsv(rarityColor);
        int alphaPart = rarityColor & 0xFF000000;
        for (int i = 0; i < 4; i++) {
            float h = (baseHsv[0] + offsets[i][0] + 1.0f) % 1.0f;
            float s = Math.max(0, Math.min(1.0f, baseHsv[1] + offsets[i][1]));
            float v = Math.max(0, Math.min(1.0f, baseHsv[2] + offsets[i][2]));
            colors[i] = (hsvToRgb(h, s, v) & 0x00FFFFFF) | alphaPart;
        }

        int targetWidth = TooltipAnimationManager.isAnimating() ? TooltipAnimationManager.getTargetWidth() : width;
        int targetHeight = TooltipAnimationManager.isAnimating() ? TooltipAnimationManager.getTargetHeight() : height;
        int P = 2 * targetWidth + 2 * targetHeight;
        if (P <= 0) return;

        int barY1 = y + 2;
        int barY2 = barY1 + 24;

        float startD = 2;
        float endD = width - 2;
        if (endD <= startD) return;

        List<Float> points = new ArrayList<>();
        points.add(startD);
        points.add(endD);

        float[] cp = new float[4];
        for (int i = 0; i < 4; i++) {
            cp[i] = (scrollOffset + i * P / 4f) % P;
            if (cp[i] < 0) cp[i] += P;
            if (cp[i] > startD && cp[i] < endD) {
                points.add(cp[i]);
            }
        }

        Collections.sort(points);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();

        float baseAlpha = (((rarityColor >> 24) & 0xff) / 255f) * fadeAlpha;

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

            float r1 = ((color1 >> 16) & 0xFF) / 255f;
            float g1 = ((color1 >> 8) & 0xFF) / 255f;
            float b1 = (color1 & 0xFF) / 255f;

            float r2 = ((color2 >> 16) & 0xFF) / 255f;
            float g2 = ((color2 >> 8) & 0xFF) / 255f;
            float b2 = (color2 & 0xFF) / 255f;

            float a1 = baseAlpha * (1.0f - (d1 - startD) / (endD - startD));
            float a2 = baseAlpha * (1.0f - (d2 - startD) / (endD - startD));

            float x1 = x + d1;
            float x2 = x + d2;

            consumer.vertex(matrix, x1, barY1, 0).color(r1, g1, b1, a1).endVertex();
            consumer.vertex(matrix, x1, barY2, 0).color(r1, g1, b1, a1).endVertex();
            consumer.vertex(matrix, x2, barY2, 0).color(r2, g2, b2, a2).endVertex();
            consumer.vertex(matrix, x2, barY1, 0).color(r2, g2, b2, a2).endVertex();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void drawEntryAnimation(GuiGraphics graphics, int x, int y, int width, int height, float progress, float fadeAlpha, boolean isLeft) {
        if (progress >= 1.0f || progress < 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();

        // 拖尾参数,增加分段数和拖尾长度
        int tailSegments = 30; // 分段数越大越平滑
        float tailLengthFactor = 0.8f; // 增加拖尾长度,相对于总路径比例

        // 使用目标尺寸计算路径,保证白点在动画过程中运动轨迹是平滑且固定的
        int targetWidth = TooltipAnimationManager.getTargetWidth();
        int targetHeight = TooltipAnimationManager.getTargetHeight();
        float totalDistTopRight = targetWidth + targetHeight;
        float totalDistLeftBottom = targetHeight + targetWidth;

        // 白点1路径计算函数 (如果是左侧,则从右上角向左上角移动,然后向下;如果是右侧,从左上角向右上角,然后向下)
        java.util.function.Function<Float, float[]> getPoint1Pos = (p) -> {
            float d = p * totalDistTopRight;
            if (isLeft) {
                if (d <= targetWidth) {
                    return new float[]{x + targetWidth - d, y}; // 向左移动
                } else {
                    return new float[]{x, y + (d - targetWidth)}; // 向下移动
                }
            } else {
                if (d <= targetWidth) {
                    return new float[]{x + d, y}; // 向右移动
                } else {
                    return new float[]{x + targetWidth, y + (d - targetWidth)}; // 向下移动
                }
            }
        };

        // 白点2路径计算函数 (如果是左侧,则从右上角向下移动,然后向左;如果是右侧,从左上角向下,然后向右)
        java.util.function.Function<Float, float[]> getPoint2Pos = (p) -> {
            float d = p * totalDistLeftBottom;
            if (isLeft) {
                if (d <= targetHeight) {
                    return new float[]{x + targetWidth, y + d}; // 向下移动
                } else {
                    return new float[]{x + targetWidth - (d - targetHeight), y + targetHeight}; // 向左移动
                }
            } else {
                if (d <= targetHeight) {
                    return new float[]{x, y + d}; // 向下移动
                } else {
                    return new float[]{x + (d - targetHeight), y + targetHeight}; // 向右移动
                }
            }
        };

        // 绘制拖尾的辅助方法
        java.util.function.BiConsumer<java.util.function.Function<Float, float[]>, Float> drawTrail = (posFunc, currentProgress) -> {
            for (int i = 0; i < tailSegments; i++) {
                float segmentProgress = currentProgress - (currentProgress * tailLengthFactor * (i / (float) tailSegments));
                if (segmentProgress < 0) segmentProgress = 0;

                float nextProgress = currentProgress - (currentProgress * tailLengthFactor * ((i + 1) / (float) tailSegments));
                if (nextProgress < 0) nextProgress = 0;

                float[] pos1 = posFunc.apply(segmentProgress);
                float[] pos2 = posFunc.apply(nextProgress);

                float alpha1 = (float) Math.pow(1.0f - (i / (float) tailSegments), 0.5f);
                float alpha2 = (float) Math.pow(1.0f - ((i + 1) / (float) tailSegments), 0.5f);

                // 随着动画整体进度,整体变透明消失,采用更平缓的曲线
                float globalFade = 1.0f - (float) Math.pow(currentProgress, 2);
                alpha1 *= globalFade * fadeAlpha; // 应用淡出透明度
                alpha2 *= globalFade * fadeAlpha; // 应用淡出透明度

                // 绘制线段 (这里用宽度为2像素的小四边形代替线段,确保在所有角度下可见)
                float dx = pos2[0] - pos1[0];
                float dy = pos2[1] - pos1[1];
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                
                if (len > 0.001f) {
                    float nx = -dy / len;
                    float ny = dx / len;
                    float thickness = 1.0f; // 粗细
                    
                    consumer.vertex(matrix, pos2[0] + nx * thickness, pos2[1] + ny * thickness, 0).color(1f, 1f, 1f, alpha2).endVertex();
                    consumer.vertex(matrix, pos2[0] - nx * thickness, pos2[1] - ny * thickness, 0).color(1f, 1f, 1f, alpha2).endVertex();
                    consumer.vertex(matrix, pos1[0] - nx * thickness, pos1[1] - ny * thickness, 0).color(1f, 1f, 1f, alpha1).endVertex();
                    consumer.vertex(matrix, pos1[0] + nx * thickness, pos1[1] + ny * thickness, 0).color(1f, 1f, 1f, alpha1).endVertex();
                } else if (i == 0) {
                    // 对于非常小的移动或者头部点,直接绘制一个小方块
                    consumer.vertex(matrix, pos1[0] + 1, pos1[1] - 1, 0).color(1f, 1f, 1f, alpha1).endVertex();
                    consumer.vertex(matrix, pos1[0] + 1, pos1[1] + 1, 0).color(1f, 1f, 1f, alpha1).endVertex();
                    consumer.vertex(matrix, pos1[0] - 1, pos1[1] + 1, 0).color(1f, 1f, 1f, alpha1).endVertex();
                    consumer.vertex(matrix, pos1[0] - 1, pos1[1] - 1, 0).color(1f, 1f, 1f, alpha1).endVertex();
                }
            }
        };

        // 绘制两个白点及其拖尾
        drawTrail.accept(getPoint1Pos, progress);
        drawTrail.accept(getPoint2Pos, progress);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}