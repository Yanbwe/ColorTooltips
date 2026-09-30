package org.yanbwe.colortooltips;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
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
import org.yanbwe.colortooltips.command.ColorTooltipsCommand;
import org.yanbwe.colortooltips.tooltip.ColorHeaderClientTooltipComponent;
import org.yanbwe.colortooltips.tooltip.ColorHeaderComponent;
import org.yanbwe.colortooltips.animation.TooltipLockManager;
import org.yanbwe.colortooltips.config.ConfigManager;

import java.util.List;

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
            MinecraftForge.EVENT_BUS.register(ClientLoginListener.class);

            // 注册客户端命令：/colortooltips reload 与 /colortooltips bypass ...
            MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent cmdEvent) ->
                ColorTooltipsCommand.register(cmdEvent.getDispatcher()));
        }

        @SubscribeEvent
        public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(ColorHeaderComponent.class, ColorHeaderClientTooltipComponent::new);
        }
    }

    /**
     * 客户端登录监听器 — 在玩家进入世界时显示首次配置加载的警告。
     * <p>
     * 由于首次加载发生在客户端初始化阶段（此时无玩家在线），
     * 警告会在玩家登录后首次进入世界时通过聊天栏显示。
     */
    public static class ClientLoginListener {

        private static boolean warningsShown = false;

        @SubscribeEvent
        public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            if (warningsShown) return;
            warningsShown = true;

            List<String> warnings = ConfigManager.getInstance().consumeLoadWarnings();
            if (warnings.isEmpty()) return;

            for (String name : warnings) {
                Minecraft.getInstance().execute(() -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.sendSystemMessage(
                            Component.translatable("colortooltips.config.load_failed", name)
                        );
                    }
                });
            }
        }
    }
}