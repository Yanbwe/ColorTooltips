package org.yanbwe.colortooltips.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 样式定义数据类，用于解析 styles/*.json 样式文件。
 * 每个字段对应 JSON 中的一个配置块，解析失败时返回带默认值的降级对象。
 * <p>
 * Style configuration data class for parsing styles/*.json files.
 * Each field maps to a JSON block, with sensible defaults on parse failure.
 */
public class StyleDefinition {

    @SerializedName("border")
    private BorderConfig border;

    @SerializedName("backGround")
    private BackgroundConfig backGround;

    @SerializedName("titleBar")
    private TitleBarConfig titleBar;

    @SerializedName("itemName")
    private ItemNameConfig itemName;

    @SerializedName("extraToolTip")
    private ExtraToolTipConfig extraToolTip;

    @SerializedName("itemModel")
    private ItemModelConfig itemModel;

    @SerializedName("animation")
    private AnimationConfig animation;

    // ==================== Getters ====================

    public BorderConfig getBorder() { return border; }
    public BackgroundConfig getBackGround() { return backGround; }
    public TitleBarConfig getTitleBar() { return titleBar; }
    public ItemNameConfig getItemName() { return itemName; }
    public ExtraToolTipConfig getExtraToolTip() { return extraToolTip; }
    public ItemModelConfig getItemModel() { return itemModel; }
    public AnimationConfig getAnimation() { return animation; }

    // ==================== Construction & Factory ====================

    private StyleDefinition() {}

    /**
     * 创建一个所有字段均使用默认值的降级实例。
     * <p>
     * Create a fallback instance with all default values.
     */
    public static StyleDefinition createDefault() {
        StyleDefinition def = new StyleDefinition();
        def.border = BorderConfig.createDefault();
        def.backGround = BackgroundConfig.createDefault();
        def.titleBar = TitleBarConfig.createDefault();
        def.itemName = ItemNameConfig.createDefault();
        def.extraToolTip = ExtraToolTipConfig.createDefault();
        def.itemModel = ItemModelConfig.createDefault();
        def.animation = AnimationConfig.createDefault();
        return def;
    }

    /**
     * 从 JSON 字符串解析样式定义，解析失败时返回带默认值的降级实例。
     * <p>
     * Parse style definition from JSON string.
     * Returns a fallback instance with defaults on parse failure.
     *
     * @param json JSON 字符串 / JSON string
     * @return 解析后的样式定义，或降级默认值 / Parsed style definition, or fallback defaults
     */
    public static StyleDefinition fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return createDefault();
        }
        try {
            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();
            StyleDefinition parsed = gson.fromJson(json, StyleDefinition.class);
            return mergeWithDefaults(parsed);
        } catch (JsonSyntaxException e) {
            // JSON 解析失败，返回降级默认值
            // JSON parse failed, returning fallback defaults
            return createDefault();
        }
    }

    /**
     * 将解析结果中为 null 的字段用默认值填充，确保每个配置块都有效。
     * <p>
     * Bug 8 fix: 同时修复每个 FillColorEntry 的 colorModifier，防止 Gson 对深层嵌套内部类
     * 解析失败导致 colorModifier 为 null，进而使用无效果的默认 (0,0) 值。
     */
    private static StyleDefinition mergeWithDefaults(StyleDefinition parsed) {
        StyleDefinition defaults = createDefault();
        if (parsed.border == null) parsed.border = defaults.border;
        if (parsed.backGround == null) parsed.backGround = defaults.backGround;
        if (parsed.titleBar == null) parsed.titleBar = defaults.titleBar;
        if (parsed.itemName == null) parsed.itemName = defaults.itemName;
        if (parsed.extraToolTip == null) parsed.extraToolTip = defaults.extraToolTip;
        if (parsed.itemModel == null) parsed.itemModel = defaults.itemModel;
        if (parsed.animation == null) parsed.animation = defaults.animation;

        // Bug 8 fix: 确保所有 FillColorEntry 的 colorModifier 均非 null
        // Gson 对 StyleDefinition$FillColorEntry$ColorModifier 这种双层嵌套静态内部类
        // 可能解析失败导致 colorModifier 为 null，此时 getColorModifier() 返回无效果的默认值
        ensureFillColorModifiers(parsed.border.getFillColor());
        ensureFillColorModifiers(parsed.backGround.getFillColor());
        ensureFillColorModifiers(parsed.titleBar.getFillColor());

        return parsed;
    }

    /**
     * 确保列表中的每个 FillColorEntry 的 colorModifier 均非 null。
     * <p>
     * 当 Gson 无法正确解析嵌套的 ColorModifier 对象时（如双层嵌套静态内部类
     * StyleDefinition$FillColorEntry$ColorModifier），该字段可能保持 null。
     * 此时显式初始化为默认值（brightness=0, saturation=0），避免 getColorModifier()
     * 因每次调用 createDefault() 而丢失用户配置的 JSON 值。
     * <p>
     * 注意：如果 Gson 成功解析了 colorModifier，此方法不会覆盖已解析的值。
     */
    private static void ensureFillColorModifiers(List<FillColorEntry> entries) {
        if (entries == null) return;
        for (FillColorEntry entry : entries) {
            if (entry != null && entry.colorModifier == null) {
                entry.colorModifier = FillColorEntry.ColorModifier.createDefault();
            }
        }
    }

    // ================================================================
    // 内部配置类 / Inner Config Classes
    // ================================================================

    /**
     * 边框渐变配置。
     * direction: Clockwise | CounterClockwise
     */
    public static class BorderConfig {

        @SerializedName("opacity")
        private double opacity = 1.0;

        @SerializedName("colorFlowSpeed")
        private double colorFlowSpeed = 1.0;

        @SerializedName("colorFlowDirection")
        private String colorFlowDirection = "Clockwise";

        @SerializedName("fillColor")
        private List<FillColorEntry> fillColor = new ArrayList<>();

        public double getOpacity() { return opacity; }
        public double getColorFlowSpeed() { return colorFlowSpeed; }
        public String getColorFlowDirection() { return colorFlowDirection; }
        public List<FillColorEntry> getFillColor() { return fillColor != null ? fillColor : Collections.emptyList(); }

        static BorderConfig createDefault() {
            BorderConfig c = new BorderConfig();
            c.opacity = 1.0;
            c.colorFlowSpeed = 1.0;
            c.colorFlowDirection = "Clockwise";
            c.fillColor = FillColorEntry.defaultList();
            return c;
        }
    }

    /**
     * 背景渐变配置。
     * direction: TopToDown | DownToTop | LeftToRight | RightToLeft
     */
    public static class BackgroundConfig {

        @SerializedName("opacity")
        private double opacity = 0.8;

        @SerializedName("colorFlowSpeed")
        private double colorFlowSpeed = 0.0;

        @SerializedName("colorFlowDirection")
        private String colorFlowDirection = "TopToDown";

        @SerializedName("fillColor")
        private List<FillColorEntry> fillColor = new ArrayList<>();

        public double getOpacity() { return opacity; }
        public double getColorFlowSpeed() { return colorFlowSpeed; }
        public String getColorFlowDirection() { return colorFlowDirection; }
        public List<FillColorEntry> getFillColor() { return fillColor != null ? fillColor : Collections.emptyList(); }

        static BackgroundConfig createDefault() {
            BackgroundConfig c = new BackgroundConfig();
            c.opacity = 0.8;
            c.colorFlowSpeed = 0.0;
            c.colorFlowDirection = "TopToDown";
            c.fillColor = FillColorEntry.defaultList();
            return c;
        }
    }

    /**
     * 标题栏配置。
     * direction: LeftToRight | RightToLeft
     */
    public static class TitleBarConfig {

        @SerializedName("enabled")
        private boolean enabled = true;

        @SerializedName("twoHeight")
        private boolean twoHeight = true;

        @SerializedName("opacity")
        private double opacity = 1.0;

        @SerializedName("colorFlowSpeed")
        private double colorFlowSpeed = 1.0;

        @SerializedName("colorFlowDirection")
        private String colorFlowDirection = "LeftToRight";

        @SerializedName("extraOpacityOnRight")
        private boolean extraOpacityOnRight = true;

        @SerializedName("fillColor")
        private List<FillColorEntry> fillColor = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public boolean isTwoHeight() { return twoHeight; }
        public double getOpacity() { return opacity; }
        public double getColorFlowSpeed() { return colorFlowSpeed; }
        public String getColorFlowDirection() { return colorFlowDirection; }
        public boolean isExtraOpacityOnRight() { return extraOpacityOnRight; }
        public List<FillColorEntry> getFillColor() { return fillColor != null ? fillColor : Collections.emptyList(); }

        static TitleBarConfig createDefault() {
            TitleBarConfig c = new TitleBarConfig();
            c.enabled = true;
            c.twoHeight = true;
            c.opacity = 1.0;
            c.colorFlowSpeed = 1.0;
            c.colorFlowDirection = "LeftToRight";
            c.extraOpacityOnRight = true;
            c.fillColor = FillColorEntry.defaultTitleBarList();
            return c;
        }
    }

    /**
     * 物品名称变色配置。
     */
    public static class ItemNameConfig {

        @SerializedName("changeColor")
        private ChangeColorConfig changeColor = new ChangeColorConfig();

        public ChangeColorConfig getChangeColor() { return changeColor != null ? changeColor : ChangeColorConfig.createDefault(); }

        static ItemNameConfig createDefault() {
            ItemNameConfig c = new ItemNameConfig();
            c.changeColor = ChangeColorConfig.createDefault();
            return c;
        }

        /**
         * 物品名称颜色变化配置。
         */
        public static class ChangeColorConfig {

            @SerializedName("enabled")
            private boolean enabled = false;

            @SerializedName("color")
            private String color = "@rarityCore";

            public boolean isEnabled() { return enabled; }
            public String getColor() { return color != null ? color : "@rarityCore"; }

            static ChangeColorConfig createDefault() {
                ChangeColorConfig c = new ChangeColorConfig();
                c.enabled = false;
                c.color = "@rarityCore";
                return c;
            }
        }
    }

    /**
     * 额外提示文本配置。
     * content: @rarityCore | @vanillaRarity | @itemID | @modID | 自定义文本
     */
    public static class ExtraToolTipConfig {

        @SerializedName("enabled")
        private boolean enabled = false;

        @SerializedName("content")
        private String content = "@rarityCore";

        @SerializedName("contentColor")
        private String contentColor = "@rarityCore";

        public boolean isEnabled() { return enabled; }
        public String getContent() { return content != null ? content : "@rarityCore"; }
        public String getContentColor() { return contentColor != null ? contentColor : "@rarityCore"; }

        static ExtraToolTipConfig createDefault() {
            ExtraToolTipConfig c = new ExtraToolTipConfig();
            c.enabled = false;
            c.content = "@rarityCore";
            c.contentColor = "@rarityCore";
            return c;
        }
    }

    /**
     * 物品模型配置。
     * bigSize: true = 两行(32px), false = 一行(16px)
     */
    public static class ItemModelConfig {

        @SerializedName("enabled")
        private boolean enabled = true;

        @SerializedName("bigSize")
        private boolean bigSize = true;

        public boolean isEnabled() { return enabled; }
        public boolean isBigSize() { return bigSize; }

        static ItemModelConfig createDefault() {
            ItemModelConfig c = new ItemModelConfig();
            c.enabled = true;
            c.bigSize = true;
            return c;
        }
    }

    /**
     * 动画参数配置。
     * 包含物品模型动画、平滑移动、平滑缩放、淡入/淡出、切换特效。
     */
    public static class AnimationConfig {

        @SerializedName("itemModel")
        private ItemModelAnimConfig itemModel;

        @SerializedName("smoothMovement")
        private SmoothMovementConfig smoothMovement;

        @SerializedName("smoothScaling")
        private SmoothScalingConfig smoothScaling;

        @SerializedName("fadeOut")
        private FadeOutConfig fadeOut;

        @SerializedName("fadeIn")
        private FadeInConfig fadeIn;

        @SerializedName("switchEffect")
        private SwitchEffectConfig switchEffect;

        public ItemModelAnimConfig getItemModel() { return itemModel != null ? itemModel : ItemModelAnimConfig.createDefault(); }
        public SmoothMovementConfig getSmoothMovement() { return smoothMovement != null ? smoothMovement : SmoothMovementConfig.createDefault(); }
        public SmoothScalingConfig getSmoothScaling() { return smoothScaling != null ? smoothScaling : SmoothScalingConfig.createDefault(); }
        public FadeOutConfig getFadeOut() { return fadeOut != null ? fadeOut : FadeOutConfig.createDefault(); }
        public FadeInConfig getFadeIn() { return fadeIn != null ? fadeIn : FadeInConfig.createDefault(); }
        public SwitchEffectConfig getSwitchEffect() { return switchEffect != null ? switchEffect : SwitchEffectConfig.createDefault(); }

        static AnimationConfig createDefault() {
            AnimationConfig c = new AnimationConfig();
            c.itemModel = ItemModelAnimConfig.createDefault();
            c.smoothMovement = SmoothMovementConfig.createDefault();
            c.smoothScaling = SmoothScalingConfig.createDefault();
            c.fadeOut = FadeOutConfig.createDefault();
            c.fadeIn = FadeInConfig.createDefault();
            c.switchEffect = SwitchEffectConfig.createDefault();
            return c;
        }

        /**
         * 物品模型动画配置，包含切换和出现动画。
         */
        public static class ItemModelAnimConfig {

            @SerializedName("switching")
            private SwitchAppearConfig switching;

            @SerializedName("appearing")
            private SwitchAppearConfig appearing;

            public SwitchAppearConfig getSwitching() { return switching != null ? switching : SwitchAppearConfig.createDefault(); }
            public SwitchAppearConfig getAppearing() { return appearing != null ? appearing : SwitchAppearConfig.createDefault(); }

            static ItemModelAnimConfig createDefault() {
                ItemModelAnimConfig c = new ItemModelAnimConfig();
                c.switching = SwitchAppearConfig.createDefault();
                c.appearing = SwitchAppearConfig.createDefault();
                return c;
            }
        }

        /**
         * 物品模型切换/出现动画的通用配置。
         */
        public static class SwitchAppearConfig {

            @SerializedName("enabled")
            private boolean enabled = true;

            @SerializedName("startSize")
            private double startSize = 0.5;

            public boolean isEnabled() { return enabled; }
            public double getStartSize() { return startSize; }

            static SwitchAppearConfig createDefault() {
                SwitchAppearConfig c = new SwitchAppearConfig();
                c.enabled = true;
                c.startSize = 0.5;
                return c;
            }
        }

        /**
         * 平滑移动配置。
         * realTimeEnabled: true = 平滑缓动, false = 直接跳变
         */
        public static class SmoothMovementConfig {

            @SerializedName("realTimeEnabled")
            private boolean realTimeEnabled = true;

            @SerializedName("speed")
            private double speed = 1.0;

            public boolean isRealTimeEnabled() { return realTimeEnabled; }
            public double getSpeed() { return speed; }

            static SmoothMovementConfig createDefault() {
                SmoothMovementConfig c = new SmoothMovementConfig();
                c.realTimeEnabled = true;
                c.speed = 1.0;
                return c;
            }
        }

        /**
         * 平滑缩放配置。
         */
        public static class SmoothScalingConfig {

            @SerializedName("enabled")
            private boolean enabled = true;

            @SerializedName("speed")
            private double speed = 1.0;

            public boolean isEnabled() { return enabled; }
            public double getSpeed() { return speed; }

            static SmoothScalingConfig createDefault() {
                SmoothScalingConfig c = new SmoothScalingConfig();
                c.enabled = true;
                c.speed = 1.0;
                return c;
            }
        }

        /**
         * 淡出动画配置。
         * delay 和 duration 单位为毫秒。
         */
        public static class FadeOutConfig {

            @SerializedName("enabled")
            private boolean enabled = true;

            @SerializedName("delay")
            private int delay = 500;

            @SerializedName("duration")
            private int duration = 100;

            public boolean isEnabled() { return enabled; }
            public int getDelay() { return delay; }
            public int getDuration() { return duration; }

            static FadeOutConfig createDefault() {
                FadeOutConfig c = new FadeOutConfig();
                c.enabled = true;
                c.delay = 500;
                c.duration = 100;
                return c;
            }
        }

        /**
         * 淡入动画配置。
         * duration 单位为毫秒。
         */
        public static class FadeInConfig {

            @SerializedName("enabled")
            private boolean enabled = true;

            @SerializedName("duration")
            private int duration = 100;

            public boolean isEnabled() { return enabled; }
            public int getDuration() { return duration; }

            static FadeInConfig createDefault() {
                FadeInConfig c = new FadeInConfig();
                c.enabled = true;
                c.duration = 100;
                return c;
            }
        }

        /**
         * 物品切换闪光特效配置。
         * color 取切换后物品的动态颜色。
         */
        public static class SwitchEffectConfig {

            @SerializedName("enabled")
            private boolean enabled = true;

            @SerializedName("color")
            private String color = "@rarityCore";

            @SerializedName("duration")
            private int duration = 500;

            @SerializedName("length")
            private double length = 1.6;

            @SerializedName("segmentation")
            private int segmentation = 50;

            public boolean isEnabled() { return enabled; }
            public String getColor() { return color != null ? color : "@rarityCore"; }
            public int getDuration() { return duration; }
            public double getLength() { return length; }
            public int getSegmentation() { return segmentation; }

            static SwitchEffectConfig createDefault() {
                SwitchEffectConfig c = new SwitchEffectConfig();
                c.enabled = true;
                c.color = "@rarityCore";
                c.duration = 500;
                c.length = 1.6;
                c.segmentation = 50;
                return c;
            }
        }
    }

    /**
     * 填充颜色条目，包含颜色值及其可选的修饰参数。
     * <p>
     * Fill color entry, containing a color value and optional modifier parameters.
     */
    public static class FillColorEntry {

        @SerializedName("color")
        private String color = "@rarityCore";

        @SerializedName("colorModifier")
        private ColorModifier colorModifier;

        public String getColor() { return color != null ? color : "@rarityCore"; }
        public ColorModifier getColorModifier() { return colorModifier != null ? colorModifier : ColorModifier.createDefault(); }

        static FillColorEntry createDefault(String color) {
            FillColorEntry e = new FillColorEntry();
            e.color = color;
            e.colorModifier = ColorModifier.createDefault();
            return e;
        }

        static List<FillColorEntry> defaultList() {
            List<FillColorEntry> list = new ArrayList<>();
            list.add(createDefault("@rarityCore"));
            return list;
        }

        static List<FillColorEntry> defaultTitleBarList() {
            List<FillColorEntry> list = new ArrayList<>();
            list.add(createDefault("@rarityCore"));
            FillColorEntry entry2 = createDefault("@rarityCore");
            entry2.colorModifier = ColorModifier.createModified(0.0, -0.3);
            list.add(entry2);
            return list;
        }

        /**
         * 颜色修正参数。
         * brightness: 明度偏移 (-1.0 ~ 1.0)，负数变暗、正数变亮
         * saturation: 饱和度偏移 (-1.0 ~ 1.0)，负数变灰、正数变鲜艳
         * <p>
         * Color modifier parameters.
         * brightness: brightness offset (-1.0 ~ 1.0), negative = darker, positive = brighter
         * saturation: saturation offset (-1.0 ~ 1.0), negative = grayer, positive = more vivid
         */
        public static class ColorModifier {

            @SerializedName("brightness")
            private double brightness = 0.0;

            @SerializedName("saturation")
            private double saturation = 0.0;

            public double getBrightness() { return brightness; }
            public double getSaturation() { return saturation; }

            static ColorModifier createDefault() {
                return new ColorModifier();
            }

            static ColorModifier createModified(double brightness, double saturation) {
                ColorModifier m = new ColorModifier();
                m.brightness = brightness;
                m.saturation = saturation;
                return m;
            }
        }
    }
}
