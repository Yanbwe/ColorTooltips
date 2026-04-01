package org.yanbwe.colortooltips;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = ColorTooltips.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue BG_ALPHA;
    public static final ForgeConfigSpec.DoubleValue BG_DARKEN;

    static {
        BUILDER.push("options");

        ENABLED = BUILDER.comment("是否启用彩色提示框").define("enabled", true);
        BG_ALPHA = BUILDER.comment("背景透明度 (0.0-1.0)").defineInRange("bgAlpha", 0.95, 0.0, 1.0);
        BG_DARKEN = BUILDER.comment("背景暗度系数").defineInRange("bgDarken", 0.7, 0.0, 1.0);

        BUILDER.pop();
    }

    static final ForgeConfigSpec SPEC = BUILDER.build();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
    }
}