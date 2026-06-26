package org.yanbwe.colortooltips.animation;

public class TooltipTarget {
    public final float width;
    public final float height;
    public final float anchorX;
    public final float anchorY;
    public final TooltipAnchor anchor;
    public final int colorArgb;
    public final float alpha;

    public TooltipTarget(float width, float height, float anchorX, float anchorY, TooltipAnchor anchor, int colorArgb, float alpha) {
        this.width = width;
        this.height = height;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchor = anchor;
        this.colorArgb = colorArgb;
        this.alpha = alpha;
    }
}
