package org.yanbwe.colortooltips;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.yanbwe.colortooltips.animation.TooltipLockManager;
import org.yanbwe.colortooltips.client.TooltipEventHandler;
import org.yanbwe.colortooltips.command.ColorTooltipsCommand;
import org.yanbwe.colortooltips.config.ConfigManager;
import org.yanbwe.colortooltips.tooltip.ColorHeaderClientTooltipComponent;
import org.yanbwe.colortooltips.tooltip.ColorHeaderComponent;

import java.util.List;

@Mod(value = ColorTooltips.MODID, dist = Dist.CLIENT)
public class ColorTooltipsClient {

    public ColorTooltipsClient(ModContainer container) {
        IEventBus modEventBus = container.getEventBus();

        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterTooltipFactories);

        // NeoForge game bus 事件
        NeoForge.EVENT_BUS.register(TooltipEventHandler.class);
        NeoForge.EVENT_BUS.register(TooltipLockManager.class);
        NeoForge.EVENT_BUS.register(ClientLoginListener.class);
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        ColorTooltips.LOGGER.info("ColorTooltips clientSetup");
        ConfigManager.init();
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        // /colortooltips reload 与 /colortooltips bypass ...
        ColorTooltipsCommand.register(event.getDispatcher());
    }

    private void onRegisterTooltipFactories(final RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ColorHeaderComponent.class, ColorHeaderClientTooltipComponent::new);
    }

    // ══════════════════════════════════════════════════════════
    // 客户端登录监听器
    // ══════════════════════════════════════════════════════════

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
