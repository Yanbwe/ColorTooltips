package org.yanbwe.colortooltips.animation;

import net.minecraft.Util;

public class TooltipAnimator {
    private static final float POSITION_SPEED = 16.0f;
    private static final float SIZE_SPEED = 14.0f;
    private static final float ALPHA_SPEED = 18.0f;
    private static final float TRANSITION_SPEED = 8.0f;
    private static final float SWITCH_OFFSET_SPEED = 28.0f;

    private TooltipTarget target;
    private final TooltipState state = new TooltipState();
    private long lastTickTimeMs;
    private boolean initialized;
    private float switchOffsetX;
    private float switchOffsetY;

    public void reset() {
        target = null;
        lastTickTimeMs = 0L;
        initialized = false;
        state.width = 0.0f;
        state.height = 0.0f;
        state.anchorX = 0.0f;
        state.anchorY = 0.0f;
        state.renderOffsetX = 0.0f;
        state.renderOffsetY = 0.0f;
        state.anchor = TooltipAnchor.LEFT_TOP;
        state.colorArgb = 0xFFFFFFFF;
        state.alpha = 0.0f;
        state.transitionProgress = 1.0f;
        switchOffsetX = 0.0f;
        switchOffsetY = 0.0f;
    }

    public void setTarget(TooltipTarget newTarget) {
        TooltipTarget previousTarget = target;
        if (initialized && target != null && target.anchor != newTarget.anchor) {
            float currentRenderX = computeRenderX(state.anchor, state.anchorX, state.width) + switchOffsetX;
            float currentRenderY = state.anchorY + switchOffsetY;
            float nextBaseRenderX = computeRenderX(newTarget.anchor, newTarget.anchorX, state.width);
            float nextBaseRenderY = newTarget.anchorY;
            switchOffsetX = currentRenderX - nextBaseRenderX;
            switchOffsetY = currentRenderY - nextBaseRenderY;
        }
        target = newTarget;
        if (!initialized) {
            initialized = true;
            lastTickTimeMs = 0L;
            state.width = newTarget.width;
            state.height = newTarget.height;
            state.anchorX = newTarget.anchorX;
            state.anchorY = newTarget.anchorY;
            state.renderOffsetX = 0.0f;
            state.renderOffsetY = 0.0f;
            state.anchor = newTarget.anchor;
            state.colorArgb = newTarget.colorArgb;
            state.alpha = 0.0f;
            state.transitionProgress = 0.0f;
        } else {
            state.colorArgb = newTarget.colorArgb;
        }
    }

    public void restartTransition() {
        if (!initialized) {
            return;
        }
        state.transitionProgress = 0.0f;
        lastTickTimeMs = 0L;
    }

    public void resetAlpha() {
        if (!initialized) {
            return;
        }
        state.alpha = 1.0f;
    }

    public void resetAlphaToZero() {
        if (!initialized) {
            return;
        }
        state.alpha = 0.0f;
    }

    public int getTargetWidthInt() {
        return target != null ? Math.max(1, Math.round(target.width)) : Math.max(1, Math.round(state.width));
    }

    public int getTargetHeightInt() {
        return target != null ? Math.max(1, Math.round(target.height)) : Math.max(1, Math.round(state.height));
    }

    public TooltipState tickAndGet() {
        if (target == null) {
            return state.copy();
        }

        long now = Util.getMillis();
        if (lastTickTimeMs == 0L) {
            lastTickTimeMs = now;
            return state.copy();
        }

        float dt = Math.max(0.0f, Math.min(0.05f, (now - lastTickTimeMs) / 1000.0f));
        lastTickTimeMs = now;

        state.width = approach(state.width, target.width, SIZE_SPEED, dt);
        state.height = approach(state.height, target.height, SIZE_SPEED, dt);
        state.anchorX = approach(state.anchorX, target.anchorX, POSITION_SPEED, dt);
        state.anchorY = approach(state.anchorY, target.anchorY, POSITION_SPEED, dt);
        state.alpha = approach(state.alpha, target.alpha, ALPHA_SPEED, dt);
        state.transitionProgress = approach(state.transitionProgress, 1.0f, TRANSITION_SPEED, dt);
        state.anchor = target.anchor;
        switchOffsetX = approach(switchOffsetX, 0.0f, SWITCH_OFFSET_SPEED, dt);
        switchOffsetY = approach(switchOffsetY, 0.0f, SWITCH_OFFSET_SPEED, dt);
        state.renderOffsetX = switchOffsetX;
        state.renderOffsetY = switchOffsetY;
        state.colorArgb = target.colorArgb;

        return state.copy();
    }

    private static final float SNAP_EPSILON = 0.001f;

    private static float approach(float current, float target, float speed, float dt) {
        float diff = target - current;
        if (Math.abs(diff) < SNAP_EPSILON) {
            return target;
        }
        float blend = 1.0f - (float) Math.exp(-speed * dt);
        return current + diff * blend;
    }

    private static float computeRenderX(TooltipAnchor anchor, float anchorX, float width) {
        if (anchor == TooltipAnchor.RIGHT_TOP) {
            return anchorX - width;
        }
        return anchorX;
    }

}
