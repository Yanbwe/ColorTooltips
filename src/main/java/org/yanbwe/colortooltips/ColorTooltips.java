package org.yanbwe.colortooltips;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.yanbwe.colortooltips.client.TooltipEventHandler;
import org.yanbwe.colortooltips.tooltip.ColorHeaderClientTooltipComponent;
import org.yanbwe.colortooltips.tooltip.ColorHeaderComponent;
import org.yanbwe.colortooltips.animation.TooltipLockManager;
import org.yanbwe.colortooltips.config.ConfigManager;

@Mod(ColorTooltips.MODID)
public class ColorTooltips {

    public static final String MODID = "colortooltips";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ColorTooltips() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("ColorTooltips commonSetup");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("ColorTooltips clientSetup");

            // 初始化配置管理器（确保在渲染前配置就绪）
            ConfigManager.getInstance();

            MinecraftForge.EVENT_BUS.register(TooltipEventHandler.class);
            MinecraftForge.EVENT_BUS.register(TooltipLockManager.class);

            // 注册 /colortooltips reload 客户端命令
            MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent cmdEvent) -> {
                cmdEvent.getDispatcher().register(
                    Commands.literal("colortooltips")
                        .then(Commands.literal("reload")
                            .executes(ctx -> {
                                ConfigManager.getInstance().reload();
                                ctx.getSource().sendSuccess(
                                    () -> Component.literal("ColorTooltips configuration reloaded!"),
                                    false
                                );
                                return 1;
                            })
                        )
                );
            });
        }

        @SubscribeEvent
        public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(ColorHeaderComponent.class, ColorHeaderClientTooltipComponent::new);
        }
    }
}