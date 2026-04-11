package org.yanbwe.colortooltips.animation;

public class TooltipState {
    public float width;
    public float height;
    public float anchorX;
    public float anchorY;
    public float renderOffsetX;
    public float renderOffsetY;
    public TooltipAnchor anchor;
    public int colorArgb;
    public float alpha;
    public float transitionProgress;

    public TooltipState copy() {
        TooltipState state = new TooltipState();
        state.width = this.width;
        state.height = this.height;
        state.anchorX = this.anchorX;
        state.anchorY = this.anchorY;
        state.renderOffsetX = this.renderOffsetX;
        state.renderOffsetY = this.renderOffsetY;
        state.anchor = this.anchor;
        state.colorArgb = this.colorArgb;
        state.alpha = this.alpha;
        state.transitionProgress = this.transitionProgress;
        return state;
    }

    public int getRenderX() {
        if (anchor == TooltipAnchor.RIGHT_TOP) {
            return Math.round(anchorX - width + renderOffsetX);
        }
        return Math.round(anchorX + renderOffsetX);
    }

    public int getRenderY() {
        return Math.round(anchorY + renderOffsetY);
    }

    public int getWidthInt() {
        return Math.max(1, Math.round(width));
    }

    public int getHeightInt() {
        return Math.max(1, Math.round(height));
    }
}
