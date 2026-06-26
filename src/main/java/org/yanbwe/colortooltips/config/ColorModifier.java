package org.yanbwe.colortooltips.config;

import org.yanbwe.colortooltips.util.ColorUtils;

/**
 * 颜色修正器 — 对解析后的 ARGB 颜色应用 brightness（明度）和 saturation（饱和度）偏移。
 * <p>
 * 从 StyleDefinition.FillColorEntry.ColorModifier 中提取为独立工具类，
 * 保持数据类与业务逻辑分离。
 */
public final class ColorModifier {

    private ColorModifier() {}

    /**
     * 应用 brightness 和 saturation 偏移到 ARGB 颜色。
     * <p>
     * 先将 ARGB 转换为 HSV，对 V (明度) 和 S (饱和度) 应用偏移量后转回 ARGB。
     * brightness 偏移规则：factor = 1.0 + brightness，结果钳制到 [0, 1]。
     * saturation 偏移规则：factor = 1.0 + saturation，结果钳制到 [0, 1]。
     *
     * @param argb       原始 ARGB 颜色
     * @param brightness 明度偏移 (-1.0 ~ 1.0)，正数变亮、负数变暗
     * @param saturation 饱和度偏移 (-1.0 ~ 1.0)，正数变鲜艳、负数变灰
     * @return 修正后的 ARGB 颜色
     */
    public static int apply(int argb, double brightness, double saturation) {
        if (brightness == 0.0 && saturation == 0.0) {
            return argb;
        }

        float[] hsv = ColorUtils.rgbToHsv(argb);

        // 明度偏移
        hsv[2] = (float) Math.max(0.0, Math.min(1.0, hsv[2] + brightness));

        // 饱和度偏移
        hsv[1] = (float) Math.max(0.0, Math.min(1.0, hsv[1] + saturation));

        int rgb = ColorUtils.hsvToRgb(hsv[0], hsv[1], hsv[2]);

        // 保留原始 alpha
        int alpha = (argb >> 24) & 0xFF;
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
