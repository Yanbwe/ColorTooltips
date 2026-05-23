package org.yanbwe.colortooltips.config;

import org.yanbwe.colortooltips.util.ColorUtils;

/**
 * 颜色修改器，通过 HSV 色彩空间对 ARGB 颜色进行亮度和饱和度的偏移调整。
 * <p>
 * 保留原始 alpha 通道不变，仅对 RGB 分量进行 HSV 空间下的偏移计算。
 * 所有偏移量自动钳制到有效范围，确保输出颜色值始终合法。
 *
 * <h3>偏移范围</h3>
 * <ul>
 *   <li><b>brightness</b>：-1.0（纯黑） → 0（不变） → 1.0（纯白）</li>
 *   <li><b>saturation</b>：-1.0（灰度） → 0（不变） → 1.0（极艳）</li>
 * </ul>
 */
public final class ColorModifier {

    private ColorModifier() {}

    /**
     * 对 ARGB 颜色应用亮度和饱和度偏移。
     * <p>
     * 内部流程：
     * <ol>
     *   <li>提取原始 alpha 通道</li>
     *   <li>钳制偏移量到 [-1.0, 1.0]</li>
     *   <li>通过 {@link ColorUtils#rgbToHsv(int)} 转换到 HSV 空间</li>
     *   <li>应用偏移：新 V = V + brightnessOffset，新 S = S + saturationOffset</li>
     *   <li>通过 {@link ColorUtils#hsvToRgb(float, float, float)} 转换回 RGB</li>
     *   <li>重新应用原始 alpha 通道</li>
     * </ol>
     *
     * @param argbColor        原始 ARGB 颜色值
     * @param brightnessOffset 亮度偏移（-1.0 ~ 1.0）
     * @param saturationOffset 饱和度偏移（-1.0 ~ 1.0）
     * @return 调整后的 ARGB 颜色值
     */
    public static int apply(int argbColor, double brightnessOffset, double saturationOffset) {
        // 提取并保留原始 alpha 通道
        int alpha = (argbColor >> 24) & 0xFF;

        // 钳制偏移量到有效范围
        double clampedBrightness = clamp(brightnessOffset, -1.0, 1.0);
        double clampedSaturation = clamp(saturationOffset, -1.0, 1.0);

        // 转换到 HSV 空间（ColorUtils.rgbToHsv 忽略 alpha，仅处理 RGB）
        float[] hsv = ColorUtils.rgbToHsv(argbColor);

        // 在 HSV 空间应用偏移
        float newSaturation = clamp(hsv[1] + (float) clampedSaturation, 0.0f, 1.0f);
        float newBrightness = clamp(hsv[2] + (float) clampedBrightness, 0.0f, 1.0f);

        // 转换回 RGB（ColorUtils.hsvToRgb 返回 0xFF______ 格式）
        int rgb = ColorUtils.hsvToRgb(hsv[0], newSaturation, newBrightness);

        // 重新应用原始 alpha 通道
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    // ══════════════════════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════════════════════

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
