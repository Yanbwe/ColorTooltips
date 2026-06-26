package org.yanbwe.colortooltips;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(ColorTooltips.MODID)
public class ColorTooltips {

    public static final String MODID = "colortooltips";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ColorTooltips(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("ColorTooltips commonSetup");
    }
}
