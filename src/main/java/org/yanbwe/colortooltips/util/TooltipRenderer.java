package org.yanbwe.colortooltips.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.yanbwe.colortooltips.config.StyleDefinition;
import org.yanbwe.colortooltips.util.ColorUtils;

public class TooltipRenderer {

    /**
     * 获取离散色段中指定位置的颜色（新版多色渲染）。
     * 将总长度均分为 colors.length 段，每段使用纯色不做梯度插值，
     * offset 偏移实现颜色流动。
     *
     * @param pos        沿路径的位置（0 ~ totalLen）
     * @param offset     流动偏移量（正值向右/顺时针流动）
     * @param colors     颜色数组
     * @param totalLen   路径总长度（像素）
     * @param segmentLen 每段长度（= totalLen / colors.length）
     * @return ARGB 颜色值
     */
    private static int getDiscreteSegmentColor(float pos, float offset, int[] colors, float totalLen, float segmentLen) {
        float shifted = pos - offset;
        shifted = ((shifted % totalLen) + totalLen) % totalLen;
        float indexFloat = shifted / segmentLen;
        int idx = (int) indexFloat;
        float frac = indexFloat - idx;
        int idx0 = idx % colors.length;
        int idx1 = (idx + 1) % colors.length;
        return ColorUtils.interpolateColor(colors[idx0], colors[idx1], frac);
    }

    private static void unpackColor(int color, float fadeAlpha, float[] out) {
        out[0] = ((color >> 16) & 0xFF) / 255f;
        out[1] = ((color >> 8) & 0xFF) / 255f;
        out[2] = (color & 0xFF) / 255f;
        out[3] = (((color >> 24) & 0xFF) / 255f) * fadeAlpha;
    }

    // ══════════════════════════════════════════════════════════
    // drawGradientScrollingBorder — 新版（接收样式参数）
    // ══════════════════════════════════════════════════════════

    /**
     * 绘制滚动离散色段边框（新版样式系统，支持多色离散色段）。
     * <p>
     * 边框周长被均分为 fillColors.length 段，每段使用纯色不做梯度插值。
     * flowOffset 驱动颜色沿边框流动，方向由 borderConfig.colorFlowDirection 控制。
     *
     * @param fillColors   已解析的 ARGB 填充颜色数组（由 DynamicColorResolver.resolve(entry,stack) 解析）
     * @param fadeAlpha    全局淡出透明度
     * @param borderConfig 边框样式配置（opacity, colorFlowDirection）
     * @param flowOffset   颜色流动引擎的当前偏移量
     */
    public static void drawGradientScrollingBorder(GuiGraphics graphics, int x, int y, int width, int height,
            int[] fillColors, float fadeAlpha, int targetWidth, int targetHeight,
            StyleDefinition.BorderConfig borderConfig, float flowOffset) {
        if (width <= 0 || height <= 0) return;
        if (fillColors == null || fillColors.length == 0) return;

        // 应用边框 opacity 到颜色 alpha 通道
        float opacityFactor = (float) borderConfig.getOpacity();
        int[] colors = new int[fillColors.length];
        for (int i = 0; i < fillColors.length; i++) {
            int alpha = (int) (((fillColors[i] >> 24) & 0xFF) * opacityFactor);
            colors[i] = (fillColors[i] & 0x00FFFFFF) | (alpha << 24);
        }

        float P = 2.0f * (width + height);
        float segmentLen = P / colors.length;
        // flowOffset 为归一化值(0~1)，乘以周长 P 转换为像素偏移
        float pixelOffset = flowOffset * (float) borderConfig.getColorFlowSpeed() * P;
        // 根据 colorFlowDirection 决定流动方向：逆时针时取反偏移
        boolean clockwise = !"CounterClockwise".equalsIgnoreCase(borderConfig.getColorFlowDirection());
        float offset = clockwise ? pixelOffset : -pixelOffset;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();

        float[] c1 = new float[4], c2 = new float[4];
        int segmentsPerEdge = Math.max(4, Math.max(width, height) / 12);

        // ── 顶边：周长位置 0 → width（从左到右） ──
        for (int i = 0; i < segmentsPerEdge; i++) {
            float t1 = (float) i / segmentsPerEdge;
            float t2 = (float) (i + 1) / segmentsPerEdge;
            float x1 = x + t1 * width;
            float x2 = x + t2 * width;
            float pos1 = t1 * width;
            float pos2 = t2 * width;
            unpackColor(getDiscreteSegmentColor(pos1, offset, colors, P, segmentLen), fadeAlpha, c1);
            unpackColor(getDiscreteSegmentColor(pos2, offset, colors, P, segmentLen), fadeAlpha, c2);
            consumer.vertex(matrix, x1, y, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
            consumer.vertex(matrix, x1, y + 1, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
            consumer.vertex(matrix, x2, y + 1, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
            consumer.vertex(matrix, x2, y, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
        }

        // ── 右边：周长位置 width → width+height（从上到下） ──
        for (int i = 0; i < segmentsPerEdge; i++) {
            float t1 = (float) i / segmentsPerEdge;
            float t2 = (float) (i + 1) / segmentsPerEdge;
            float y1 = y + t1 * height;
            float y2 = y + t2 * height;
            float pos1 = width + t1 * height;
            float pos2 = width + t2 * height;
            unpackColor(getDiscreteSegmentColor(pos1, offset, colors, P, segmentLen), fadeAlpha, c1);
            unpackColor(getDiscreteSegmentColor(pos2, offset, colors, P, segmentLen), fadeAlpha, c2);
            consumer.vertex(matrix, x + width - 1, y1, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
            consumer.vertex(matrix, x + width - 1, y2, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
            consumer.vertex(matrix, x + width, y2, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
            consumer.vertex(matrix, x + width, y1, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
        }

        // ── 底边：周长位置 width+height → 2*width+height（从右到左） ──
        for (int i = 0; i < segmentsPerEdge; i++) {
            float t1 = (float) i / segmentsPerEdge;
            float t2 = (float) (i + 1) / segmentsPerEdge;
            float x1_rev = x + width - t1 * width;
            float x2_rev = x + width - t2 * width;
            float pos1 = width + height + t1 * width;
            float pos2 = width + height + t2 * width;
            unpackColor(getDiscreteSegmentColor(pos1, offset, colors, P, segmentLen), fadeAlpha, c1);
            unpackColor(getDiscreteSegmentColor(pos2, offset, colors, P, segmentLen), fadeAlpha, c2);
            consumer.vertex(matrix, x2_rev, y + height - 1, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
            consumer.vertex(matrix, x2_rev, y + height, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
            consumer.vertex(matrix, x1_rev, y + height, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
            consumer.vertex(matrix, x1_rev, y + height - 1, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
        }

        // ── 左边：周长位置 2*width+height → P（从下到上） ──
        for (int i = 0; i < segmentsPerEdge; i++) {
            float t1 = (float) i / segmentsPerEdge;
            float t2 = (float) (i + 1) / segmentsPerEdge;
            float y1_rev = y + height - t1 * height;
            float y2_rev = y + height - t2 * height;
            float pos1 = 2 * width + height + t1 * height;
            float pos2 = 2 * width + height + t2 * height;
            unpackColor(getDiscreteSegmentColor(pos1, offset, colors, P, segmentLen), fadeAlpha, c1);
            unpackColor(getDiscreteSegmentColor(pos2, offset, colors, P, segmentLen), fadeAlpha, c2);
            consumer.vertex(matrix, x, y2_rev, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
            consumer.vertex(matrix, x, y1_rev, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
            consumer.vertex(matrix, x + 1, y1_rev, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
            consumer.vertex(matrix, x + 1, y2_rev, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制变暗的滚动渐变描边（外描边 / 内描边用）。
     * <p>
     * 将主体边框的 fillColor 数组中每个颜色统一变暗后，调用
     * {@link #drawGradientScrollingBorder(GuiGraphics, int, int, int, int, int[], float, int, int, StyleDefinition.BorderConfig, float)}
     * 在指定位置渲染。外描边传入 {@code (x-1, y-1, w+2, h+2)}，内描边传入 {@code (x+1, y+1, w-2, h-2)}。
     *
     * @param fillColors  主体边框的填充色数组（不会被修改，内部拷贝后变暗）
     * @param darkenFactor 变暗因子（0~1），1.0 不变，0.0 全黑；推荐 0.5
     */
    public static void drawDarkenedGradientScrollingBorder(GuiGraphics graphics, int x, int y, int width, int height,
            int[] fillColors, float fadeAlpha, int targetWidth, int targetHeight,
            StyleDefinition.BorderConfig borderConfig, float flowOffset, float darkenFactor) {
        if (fillColors == null || fillColors.length == 0) return;
        int[] darkened = new int[fillColors.length];
        for (int i = 0; i < fillColors.length; i++) {
            darkened[i] = ColorUtils.darkenColor(fillColors[i], darkenFactor);
        }
        drawGradientScrollingBorder(graphics, x, y, width, height, darkened, fadeAlpha,
                targetWidth, targetHeight, borderConfig, flowOffset);
    }
    // ══════════════════════════════════════════════════════════
    // drawOuterBorder / drawInnerBorder / drawRarityBorder — 不变
    // ══════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════
    // drawBackground — 新版（接收背景样式配置）
    // ══════════════════════════════════════════════════════════

    /**
     * 绘制提示框背景（新版样式系统）。
     * 背景色由调用方通过 DynamicColorResolver 解析后传入，opacity 来自 backGround 配置。
     *
     * @param bgColor   已解析的背景色 ARGB
     * @param fadeAlpha 全局淡出透明度
     * @param bgConfig  背景样式配置（使用其 opacity）
     */
    public static void drawBackground(GuiGraphics graphics, int x, int y, int width, int height,
            int bgColor, float fadeAlpha, StyleDefinition.BackgroundConfig bgConfig) {
        float opacity = (float) bgConfig.getOpacity();
        int alpha = (int) (((bgColor >> 24) & 0xFF) * fadeAlpha * opacity);
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

    /**
     * 绘制提示框背景（旧版兼容方法，opacity 默认为 1.0）。
     */
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

    /**
     * 绘制多色离散段背景（新版样式系统，支持多色离散色段）。
     * <p>
     * 背景区域沿 direction 方向被均分为 fillColors.length 段，每段使用纯色。
     * flowOffset 驱动颜色流动，方向由 bgConfig.colorFlowDirection 控制。
     *
     * @param fillColors 已解析的填充色 ARGB 数组
     * @param fadeAlpha  全局淡出透明度
     * @param bgConfig   背景样式配置（opacity, colorFlowDirection）
     * @param flowOffset 颜色流动引擎的当前偏移量
     */
    public static void drawBackground(GuiGraphics graphics, int x, int y, int width, int height,
            int[] fillColors, float fadeAlpha, StyleDefinition.BackgroundConfig bgConfig, float flowOffset) {
        if (fillColors == null || fillColors.length == 0) return;

        float opacity = (float) bgConfig.getOpacity();
        int[] colors = new int[fillColors.length];
        for (int i = 0; i < fillColors.length; i++) {
            int alpha = (int) (((fillColors[i] >> 24) & 0xFF) * opacity);
            colors[i] = (fillColors[i] & 0x00FFFFFF) | (alpha << 24);
        }

        int bgX1 = x + 2;
        int bgY1 = y + 2;
        int bgX2 = x + width - 2;
        int bgY2 = y + height - 2;
        int bgW = bgX2 - bgX1;
        int bgH = bgY2 - bgY1;
        if (bgW <= 0 || bgH <= 0) return;

        String direction = bgConfig.getColorFlowDirection();
        boolean isVertical = "TopToDown".equalsIgnoreCase(direction) || "DownToTop".equalsIgnoreCase(direction);
        boolean reverse = "DownToTop".equalsIgnoreCase(direction) || "RightToLeft".equalsIgnoreCase(direction);

        float totalLen = isVertical ? bgH : bgW;
        float segmentLen = totalLen / colors.length;
        // flowOffset 为归一化值(0~1)，乘以 totalLen 转换为像素偏移
        float pixelOffset = flowOffset * (float) bgConfig.getColorFlowSpeed() * totalLen;
        float offset = reverse ? -pixelOffset : pixelOffset;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();

        float[] c1 = new float[4], c2 = new float[4];
        int segments = Math.max(4, (int) totalLen / 12);

        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / segments;
            float t2 = (float) (i + 1) / segments;
            float pos1 = t1 * totalLen;
            float pos2 = t2 * totalLen;

            int color1 = getDiscreteSegmentColor(pos1, offset, colors, totalLen, segmentLen);
            int color2 = getDiscreteSegmentColor(pos2, offset, colors, totalLen, segmentLen);
            unpackColor(color1, fadeAlpha, c1);
            unpackColor(color2, fadeAlpha, c2);

            if (isVertical) {
                float y1 = bgY1 + pos1;
                float y2 = bgY1 + pos2;
                consumer.vertex(matrix, bgX1, y1, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
                consumer.vertex(matrix, bgX1, y2, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
                consumer.vertex(matrix, bgX2, y2, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
                consumer.vertex(matrix, bgX2, y1, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
            } else {
                float x1 = bgX1 + pos1;
                float x2 = bgX1 + pos2;
                consumer.vertex(matrix, x1, bgY1, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
                consumer.vertex(matrix, x2, bgY1, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
                consumer.vertex(matrix, x2, bgY2, 0).color(c2[0], c2[1], c2[2], c2[3]).endVertex();
                consumer.vertex(matrix, x1, bgY2, 0).color(c1[0], c1[1], c1[2], c1[3]).endVertex();
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    // drawGradientTitleBar — 新版（接收样式参数）
    // ══════════════════════════════════════════════════════════

    /**
     * 绘制离散色段标题栏（新版样式系统，支持多色离散色段）。
     * <p>
     * 标题栏宽度被均分为 fillColors.length 段，每段使用纯色不做梯度插值。
     * flowOffset 驱动颜色沿标题栏流动，方向由 titleBarConfig.colorFlowDirection 控制。
     *
     * @param fillColors      已解析的填充色 ARGB 数组
     * @param fadeAlpha       全局淡出透明度
     * @param titleBarConfig  标题栏样式配置（opacity, extraOpacityOnRight, colorFlowDirection）
     * @param flowOffset      颜色流动引擎的当前偏移量
     */
    public static void drawGradientTitleBar(GuiGraphics graphics, int x, int y, int width, int height,
            int[] fillColors, float fadeAlpha, int targetWidth, int targetHeight, int titleBarHeight,
            StyleDefinition.TitleBarConfig titleBarConfig, float flowOffset) {
        if (fillColors == null || fillColors.length == 0) return;

        // 应用标题栏 opacity 到颜色 alpha 通道
        int[] colors = new int[fillColors.length];
        float opacity = (float) titleBarConfig.getOpacity();
        for (int i = 0; i < fillColors.length; i++) {
            int alpha = (int) (((fillColors[i] >> 24) & 0xFF) * opacity);
            colors[i] = (fillColors[i] & 0x00FFFFFF) | (alpha << 24);
        }

        int barY1 = y + 2;
        int barY2 = barY1 + titleBarHeight;

        float startD = 2;
        float endD = width - 2;
        if (endD <= startD) return;

        float barLen = endD - startD;
        float segmentLen = barLen / colors.length;
        // flowOffset 为归一化值(0~1)，乘以 barLen 转换为像素偏移
        float pixelOffset = flowOffset * (float) titleBarConfig.getColorFlowSpeed() * barLen;
        // 根据 colorFlowDirection 决定流动方向
        boolean leftToRight = !"RightToLeft".equalsIgnoreCase(titleBarConfig.getColorFlowDirection());
        float offset = leftToRight ? pixelOffset : -pixelOffset;

        boolean extraOpacityOnRight = titleBarConfig.isExtraOpacityOnRight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();

        float baseAlpha = ((colors[0] >> 24) & 0xff) / 255f * fadeAlpha;
        int segments = Math.max(4, (int) barLen / 12);

        for (int i = 0; i < segments; i++) {
            float t1 = (float) i / segments;
            float t2 = (float) (i + 1) / segments;
            float d1 = startD + t1 * barLen;
            float d2 = startD + t2 * barLen;

            int color1 = getDiscreteSegmentColor(d1, offset, colors, barLen, segmentLen);
            int color2 = getDiscreteSegmentColor(d2, offset, colors, barLen, segmentLen);

            float r1 = ((color1 >> 16) & 0xFF) / 255f;
            float g1 = ((color1 >> 8) & 0xFF) / 255f;
            float b1 = (color1 & 0xFF) / 255f;
            float r2 = ((color2 >> 16) & 0xFF) / 255f;
            float g2 = ((color2 >> 8) & 0xFF) / 255f;
            float b2 = (color2 & 0xFF) / 255f;

            float a1 = baseAlpha * (extraOpacityOnRight ? (1.0f - t1) : 1.0f);
            float a2 = baseAlpha * (extraOpacityOnRight ? (1.0f - t2) : 1.0f);

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
    // ══════════════════════════════════════════════════════════
    // drawEntryAnimation — 公共实现
    // ══════════════════════════════════════════════════════════

    /**
     * 计算入场动画轨迹点 1（顶边→右边路径）。
     */
    private static float[] getEntryPoint1Pos(float p, int x, int y,
            int targetWidth, int targetHeight, boolean isLeft, float totalDistTopRight) {
        float d = p * totalDistTopRight;
        if (isLeft) {
            if (d <= targetWidth) return new float[]{x + targetWidth - d, y};
            else return new float[]{x, y + (d - targetWidth)};
        } else {
            if (d <= targetWidth) return new float[]{x + d, y};
            else return new float[]{x + targetWidth, y + (d - targetWidth)};
        }
    }

    /**
     * 计算入场动画轨迹点 2（左边→底边路径）。
     */
    private static float[] getEntryPoint2Pos(float p, int x, int y,
            int targetWidth, int targetHeight, boolean isLeft, float totalDistLeftBottom) {
        float d = p * totalDistLeftBottom;
        if (isLeft) {
            if (d <= targetHeight) return new float[]{x + targetWidth, y + d};
            else return new float[]{x + targetWidth - (d - targetHeight), y + targetHeight};
        } else {
            if (d <= targetHeight) return new float[]{x, y + d};
            else return new float[]{x + (d - targetHeight), y + targetHeight};
        }
    }

    /**
     * 入场动画内部渲染实现 — 消除新旧 API 的重复代码。
     * <p>
     * 由三个公开的 {@code drawEntryAnimation} 重载共享，
     * 仅 tailSegments / tailLengthFactor / alphaExponent 等参数来源不同。
     */
    private static void drawEntryAnimationImpl(GuiGraphics graphics, int x, int y, int width, int height,
            float progress, float fadeAlpha, boolean isLeft,
            int tailSegments, float tailLengthFactor, float alphaExponent,
            float fr, float fg, float fb) {

        if (progress >= 1.0f || progress < 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        var consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();

        int targetWidth = width;
        int targetHeight = height;
        float totalDistTopRight = targetWidth + targetHeight;
        float totalDistLeftBottom = targetHeight + targetWidth;

        java.util.function.BiConsumer<java.util.function.Function<Float, float[]>, Float> drawTrail = (posFunc, currentProgress) -> {
            for (int i = 0; i < tailSegments; i++) {
                float segmentProgress = currentProgress - (currentProgress * tailLengthFactor * (i / (float) tailSegments));
                if (segmentProgress < 0) segmentProgress = 0;

                float nextProgress = currentProgress - (currentProgress * tailLengthFactor * ((i + 1) / (float) tailSegments));
                if (nextProgress < 0) nextProgress = 0;

                float[] pos1 = posFunc.apply(segmentProgress);
                float[] pos2 = posFunc.apply(nextProgress);

                float alpha1 = (float) Math.pow(1.0f - (i / (float) tailSegments), alphaExponent);
                float alpha2 = (float) Math.pow(1.0f - ((i + 1) / (float) tailSegments), alphaExponent);

                float globalFade = 1.0f - (float) Math.pow(currentProgress, 2);
                alpha1 *= globalFade * fadeAlpha;
                alpha2 *= globalFade * fadeAlpha;

                float dx = pos2[0] - pos1[0];
                float dy = pos2[1] - pos1[1];
                float len = (float) Math.sqrt(dx * dx + dy * dy);

                if (len > 0.001f) {
                    float nx = -dy / len;
                    float ny = dx / len;
                    float thickness = 1.0f;

                    consumer.vertex(matrix, pos2[0] + nx * thickness, pos2[1] + ny * thickness, 0).color(fr, fg, fb, alpha2).endVertex();
                    consumer.vertex(matrix, pos2[0] - nx * thickness, pos2[1] - ny * thickness, 0).color(fr, fg, fb, alpha2).endVertex();
                    consumer.vertex(matrix, pos1[0] - nx * thickness, pos1[1] - ny * thickness, 0).color(fr, fg, fb, alpha1).endVertex();
                    consumer.vertex(matrix, pos1[0] + nx * thickness, pos1[1] + ny * thickness, 0).color(fr, fg, fb, alpha1).endVertex();
                } else if (i == 0) {
                    consumer.vertex(matrix, pos1[0] + 1, pos1[1] - 1, 0).color(fr, fg, fb, alpha1).endVertex();
                    consumer.vertex(matrix, pos1[0] + 1, pos1[1] + 1, 0).color(fr, fg, fb, alpha1).endVertex();
                    consumer.vertex(matrix, pos1[0] - 1, pos1[1] + 1, 0).color(fr, fg, fb, alpha1).endVertex();
                    consumer.vertex(matrix, pos1[0] - 1, pos1[1] - 1, 0).color(fr, fg, fb, alpha1).endVertex();
                }
            }
        };

        java.util.function.Function<Float, float[]> getPoint1Pos = (p) ->
                getEntryPoint1Pos(p, x, y, targetWidth, targetHeight, isLeft, totalDistTopRight);
        java.util.function.Function<Float, float[]> getPoint2Pos = (p) ->
                getEntryPoint2Pos(p, x, y, targetWidth, targetHeight, isLeft, totalDistLeftBottom);

        drawTrail.accept(getPoint1Pos, progress);
        drawTrail.accept(getPoint2Pos, progress);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    /**
     * 绘制物品切换入场动画（新版样式系统）。
     * 参数来自 StyleDefinition.AnimationConfig.SwitchEffectConfig。
     *
     * @param progress      动画进度 0.0~1.0
     * @param switchEffect  切换特效配置（segmentation, length）
     * @param flashColor    闪光颜色 ARGB（从样式的 switchEffect.color 解析）
     */
    public static void drawEntryAnimation(GuiGraphics graphics, int x, int y, int width, int height,
            float progress, float fadeAlpha, boolean isLeft,
            StyleDefinition.AnimationConfig.SwitchEffectConfig switchEffect, int flashColor) {
        float fr = ((flashColor >> 16) & 0xFF) / 255.0f;
        float fg = ((flashColor >> 8) & 0xFF) / 255.0f;
        float fb = (flashColor & 0xFF) / 255.0f;

        drawEntryAnimationImpl(graphics, x, y, width, height,
                progress, fadeAlpha, isLeft,
                switchEffect.getSegmentation(), (float) switchEffect.getLength(), 0.5f,
                fr, fg, fb);
    }
}
