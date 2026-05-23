package org.yanbwe.colortooltips;

import org.yanbwe.colortooltips.config.ConfigManager;

/**
 * 配置兼容层 — 桥接旧版 {@code Config.XXX.get()} 调用到新的 ConfigManager。
 * <p>
 * 所有原有公共静态字段保留，使用简单的 {@code BoolVal}/{@code IntVal}/{@code DoubleVal}
 * 包装类型替代 ForgeConfigSpec.XXXValue，以维持 {@code .get()} / {@code .set()} 调用兼容性。
 * <p>
 * 在静态初始化块中触发 ConfigManager 单例加载，并将值同步到这些兼容字段。
 * <p>
 * Compatibility bridge — delegates old {@code Config.XXX.get()} calls to the new ConfigManager.
 * Wrapper types ({@code BoolVal}/{@code IntVal}/{@code DoubleVal}) replace ForgeConfigSpec.XXXValue
 * to preserve existing caller syntax during gradual migration.
 */
public final class Config {

    // ══════════════════════════════════════════════════════════
    // 值包装类型（替代 ForgeConfigSpec.XXXValue，保持 .get()/.set() 兼容）
    // TODO: 待所有调用方迁移至 ConfigManager 后移除此段
    // ══════════════════════════════════════════════════════════

    public static final class BoolVal {
        private boolean value;
        BoolVal(boolean value) { this.value = value; }
        public boolean get() { return value; }
        public void set(boolean value) { this.value = value; }
    }

    public static final class IntVal {
        private int value;
        IntVal(int value) { this.value = value; }
        public int get() { return value; }
        public void set(int value) { this.value = value; }
    }

    public static final class DoubleVal {
        private double value;
        DoubleVal(double value) { this.value = value; }
        public double get() { return value; }
        public void set(double value) { this.value = value; }
    }

    // ══════════════════════════════════════════════════════════
    // 公共静态字段（默认值与旧版 ForgeConfigSpec 保持一致）
    // ══════════════════════════════════════════════════════════

    // options
    public static final BoolVal ENABLED = new BoolVal(true);
    public static final BoolVal CUSTOM_HEADER_ENABLED = new BoolVal(true);
    public static final DoubleVal BG_ALPHA = new DoubleVal(0.97);
    public static final DoubleVal BG_DARKEN = new DoubleVal(0.7);
    public static final BoolVal BORDER_GRADIENT_ENABLED = new BoolVal(true);
    public static final BoolVal TITLEBAR_GRADIENT_ENABLED = new BoolVal(true);

    // color_variation
    public static final DoubleVal HUE_VARIATION = new DoubleVal(0.1);
    public static final DoubleVal VALUE_VARIATION = new DoubleVal(0.4);
    public static final DoubleVal SATURATION_VARIATION = new DoubleVal(0.1);

    // switch_animation
    public static final IntVal SWITCH_TAIL_SEGMENTS = new IntVal(50);
    public static final DoubleVal SWITCH_TAIL_LENGTH = new DoubleVal(1.6);
    public static final DoubleVal SWITCH_TAIL_ALPHA_EXPONENT = new DoubleVal(0.5);
    public static final IntVal FADE_OUT_DELAY = new IntVal(100);
    public static final IntVal FADE_IN_DURATION = new IntVal(100);
    public static final IntVal SWITCH_FLASH_DURATION = new IntVal(500);
    public static final DoubleVal ITEM_SCALE_MIN = new DoubleVal(0.5);

    // color_flow
    public static final DoubleVal GRADIENT_PERIOD = new DoubleVal(200.0);
    public static final DoubleVal SCROLL_SPEED = new DoubleVal(0.05);

    // tooltip_lock
    public static final DoubleVal LOCK_SCROLL_SENSITIVITY = new DoubleVal(10.0);

    // ══════════════════════════════════════════════════════════
    // 静态初始化 — 触发 ConfigManager 加载并同步值
    // ══════════════════════════════════════════════════════════

    static {
        try {
            ConfigManager cm = ConfigManager.getInstance();
            syncFrom(cm);
        } catch (Exception e) {
            ColorTooltips.LOGGER.warn("Config: failed to initialize ConfigManager, keeping hardcoded defaults", e);
        }
    }

    /**
     * 从 ConfigManager 同步所有兼容字段的值。
     * ConfigManager 当前返回的是硬编码默认值（stub），后续将接入 JSON 配置。
     */
    private static void syncFrom(ConfigManager cm) {
        ENABLED.set(cm.isEnabled());
        CUSTOM_HEADER_ENABLED.set(cm.isCustomHeaderEnabled());
        BG_ALPHA.set(cm.getBgAlpha());
        BG_DARKEN.set(cm.getBgDarken());
        BORDER_GRADIENT_ENABLED.set(cm.isBorderGradientEnabled());
        TITLEBAR_GRADIENT_ENABLED.set(cm.isTitlebarGradientEnabled());
        HUE_VARIATION.set(cm.getHueVariation());
        VALUE_VARIATION.set(cm.getValueVariation());
        SATURATION_VARIATION.set(cm.getSaturationVariation());
        SWITCH_TAIL_SEGMENTS.set(cm.getSwitchTailSegments());
        SWITCH_TAIL_LENGTH.set(cm.getSwitchTailLength());
        SWITCH_TAIL_ALPHA_EXPONENT.set(cm.getSwitchTailAlphaExponent());
        FADE_OUT_DELAY.set(cm.getFadeOutDelay());
        FADE_IN_DURATION.set(cm.getFadeInDuration());
        SWITCH_FLASH_DURATION.set(cm.getSwitchFlashDuration());
        ITEM_SCALE_MIN.set(cm.getItemScaleMin());
        GRADIENT_PERIOD.set(cm.getGradientPeriod());
        SCROLL_SPEED.set(cm.getScrollSpeed());
        LOCK_SCROLL_SENSITIVITY.set(cm.getLockSensitivity());
    }

    private Config() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }
}
