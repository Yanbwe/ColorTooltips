package org.yanbwe.colortooltips.util;

public class ColorUtils {

    public static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        float h = 0.0f;
        if (delta != 0) {
            if (max == r) {
                h = ((g - b) / delta) % 6;
            } else if (max == g) {
                h = (b - r) / delta + 2;
            } else {
                h = (r - g) / delta + 4;
            }
            h /= 6;
            if (h < 0) h += 1;
        }

        float s = max == 0 ? 0 : delta / max;
        float v = max;

        return new float[]{h, s, v};
    }

    public static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;

        float r, g, b;
        int sector = (int) (h * 6) % 6;

        switch (sector) {
            case 0:
                r = c; g = x; b = 0;
                break;
            case 1:
                r = x; g = c; b = 0;
                break;
            case 2:
                r = 0; g = c; b = x;
                break;
            case 3:
                r = 0; g = x; b = c;
                break;
            case 4:
                r = x; g = 0; b = c;
                break;
            default:
                r = c; g = 0; b = x;
                break;
        }

        int red = (int) ((r + m) * 255);
        int green = (int) ((g + m) * 255);
        int blue = (int) ((b + m) * 255);

        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    public static int darkenColor(int argb, float factor) {
        float[] hsv = rgbToHsv(argb);
        hsv[2] = hsv[2] * factor;
        return hsvToRgb(hsv[0], hsv[1], hsv[2]);
    }

    public static int withAlpha(int argb, float alpha) {
        int a = (int) (Math.max(0, Math.min(255, alpha * 255)));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    public static int multiplyAlpha(int color, float alphaMultiplier) {
        int alpha = (int) (Math.max(0, Math.min(255, ((color >> 24) & 0xFF) * alphaMultiplier)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static int interpolateColor(int color1, int color2, float ratio) {
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

    /**
     * HSV 空间插值，对色相使用最短路径（环绕色相环）。
     * <p>
     * 当两个颜色在色相环上跨越 0° 边界时（如 350° → 10°），
     * 走短路径（+20°）而非长路径（-340°），确保动画不出现逆向色相跳跃。
     *
     * @param fromArgb 起始颜色（ARGB，alpha 保留不参与 HSV 插值）
     * @param toArgb   目标颜色（ARGB，alpha 保留不参与 HSV 插值）
     * @param ratio    插值比例 0.0~1.0
     * @return 插值后的 ARGB 颜色
     */
    public static int interpolateColorHSV(int fromArgb, int toArgb, float ratio) {
        float[] fromHsv = rgbToHsv(fromArgb);
        float[] toHsv = rgbToHsv(toArgb);

        // 色相环最短路径插值
        float h;
        float delta = toHsv[0] - fromHsv[0];
        if (Math.abs(delta) > 0.5f) {
            // 跨越 0°/360° 边界：走短路径
            if (delta > 0) {
                h = fromHsv[0] + (delta - 1.0f) * ratio;
            } else {
                h = fromHsv[0] + (delta + 1.0f) * ratio;
            }
            if (h < 0) h += 1.0f;
            if (h >= 1.0f) h -= 1.0f;
        } else {
            h = fromHsv[0] + delta * ratio;
        }

        float s = fromHsv[1] + (toHsv[1] - fromHsv[1]) * ratio;
        float v = fromHsv[2] + (toHsv[2] - fromHsv[2]) * ratio;

        int rgb = hsvToRgb(h, s, v);

        // 保留原始 alpha（取两者 alpha 插值）
        int fromAlpha = (fromArgb >> 24) & 0xFF;
        int toAlpha = (toArgb >> 24) & 0xFF;
        int alpha = (int) (fromAlpha + (toAlpha - fromAlpha) * ratio);

        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }
}
