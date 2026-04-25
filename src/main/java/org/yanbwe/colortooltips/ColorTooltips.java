package org.yanbwe.colortooltips;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.yanbwe.colortooltips.client.MissingRarityScreen;
import org.yanbwe.colortooltips.client.TooltipEventHandler;
import org.yanbwe.colortooltips.compat.RarityCoreProxy;
import org.yanbwe.colortooltips.tooltip.ColorHeaderClientTooltipComponent;
import org.yanbwe.colortooltips.tooltip.ColorHeaderComponent;
import org.yanbwe.colortooltips.animation.TooltipLockManager;

@Mod(ColorTooltips.MODID)
public class ColorTooltips {

    public static final String MODID = "colortooltips";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ColorTooltips() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("ColorTooltips commonSetup");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        private static boolean hasShownRarityCoreWarning = false;

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("ColorTooltips clientSetup");
            MinecraftForge.EVENT_BUS.register(TooltipEventHandler.class);
            MinecraftForge.EVENT_BUS.register(TooltipLockManager.class);

            // 注册 RarityCore 缺失警告监听器
            MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Opening openingEvent) -> {
                // 只在标题画面打开时检查
                if (!(openingEvent.getNewScreen() instanceof TitleScreen)) return;
                // 已在本会话中显示过，不再重复弹出
                if (hasShownRarityCoreWarning) return;
                // 已安装 RarityCore，不需要警告
                if (RarityCoreProxy.isLoaded()) return;
                // 配置中已禁用警告
                if (!Config.SHOW_RARITY_CORE_WARNING.get()) return;

                hasShownRarityCoreWarning = true;
                openingEvent.setNewScreen(new MissingRarityScreen());
            });
        }

        @SubscribeEvent
        public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(ColorHeaderComponent.class, ColorHeaderClientTooltipComponent::new);
        }
    }
}