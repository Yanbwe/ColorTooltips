package org.yanbwe.colortooltips;

import org.yanbwe.raritycore.client.RarityExclusionManager;

public class RenderingContext {

    public static void startTooltipItemRendering() {
        RarityExclusionManager.setRenderingTooltipItem(true);
    }

    public static void endTooltipItemRendering() {
        RarityExclusionManager.clear();
    }
}
