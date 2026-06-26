package org.yanbwe.colortooltips;

import org.yanbwe.colortooltips.compat.RarityCoreProxy;

public class RenderingContext {

    public static void startTooltipItemRendering() {
        RarityCoreProxy.startTooltipItemRendering();
    }

    public static void endTooltipItemRendering() {
        RarityCoreProxy.endTooltipItemRendering();
    }
}
