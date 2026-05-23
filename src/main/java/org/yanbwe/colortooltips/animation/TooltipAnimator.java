package org.yanbwe.colortooltips.animation;

import net.minecraft.Util;

public class TooltipAnimator {
    // 基础速度常量（由配置的速度倍率缩放）
    private static final float BASE_POSITION_SPEED = 16.0f;
    private static final float BASE_SIZE_SPEED = 14.0f;
    private static final float ALPHA_SPEED = 18.0f;
    private static final float TRANSITION_SPEED = 8.0f;
    private static final float SWITCH_OFFSET_SPEED = 14.0f;

    private TooltipTarget target;
    private final TooltipState state = new TooltipState();
    private long lastTickTimeMs;
    private boolean initialized;
    private float switchOffsetX;
    private float switchOffsetY;

    // 可配置的平滑参数（由 TooltipAnimationSystem.applyStyle() 设置）
    private float positionSpeed = BASE_POSITION_SPEED;
    private float sizeSpeed = BASE_SIZE_SPEED;
    private float alphaSpeed = ALPHA_SPEED;
    private boolean smoothMovement = true;
    private boolean smoothScaling = true;

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
        positionSpeed = BASE_POSITION_SPEED;
        sizeSpeed = BASE_SIZE_SPEED;
        alphaSpeed = ALPHA_SPEED;
        smoothMovement = true;
        smoothScaling = true;
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

    /**
     * 强制将 animator 状态初始化为目标值（仅位置/大小/颜色），不重置 alpha 和 transitionProgress。
     * <p>
     * 用于多提示框帧后恢复 —— 共享 ANIMATOR 在多提示框帧中被污染，
     * 下帧若不重置位置起点，将从错误位置缓动到新目标产生"重影"。
     * <p>
     * 与 {@link #setTarget(TooltipTarget)} 中的首次初始化不同，
     * 此方法保持 alpha 和 transitionProgress 不变，避免闪烁。
     *
     * @param target 提示框目标参数
     */
    public void forceInit(TooltipTarget target) {
        this.target = target;
        initialized = true;
        lastTickTimeMs = 0L;
        state.width = target.width;
        state.height = target.height;
        state.anchorX = target.anchorX;
        state.anchorY = target.anchorY;
        state.renderOffsetX = 0.0f;
        state.renderOffsetY = 0.0f;
        state.anchor = target.anchor;
        state.colorArgb = target.colorArgb;
        // 不碰 alpha 和 transitionProgress，保留现有动画状态
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

        // 平滑缩放 vs 直接跳变（由 smoothScaling 控制）
        if (smoothScaling) {
            state.width = approach(state.width, target.width, sizeSpeed, dt);
            state.height = approach(state.height, target.height, sizeSpeed, dt);
        } else {
            state.width = target.width;
            state.height = target.height;
        }
        // 平滑移动 vs 直接跳变（由 smoothMovement 控制）
        if (smoothMovement) {
            state.anchorX = approach(state.anchorX, target.anchorX, positionSpeed, dt);
            state.anchorY = approach(state.anchorY, target.anchorY, positionSpeed, dt);
        } else {
            state.anchorX = target.anchorX;
            state.anchorY = target.anchorY;
        }
        state.alpha = approach(state.alpha, target.alpha, alphaSpeed, dt);
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

    /**
     * 设置平滑移动参数。
     * @param enabled true = 平滑缓动，false = 直接跳变到目标位置
     * @param speed 速度倍率（1.0 = 默认速度），最小 0.1
     */
    public void setSmoothMovement(boolean enabled, float speed) {
        this.smoothMovement = enabled;
        this.positionSpeed = Math.max(0.1f, speed * BASE_POSITION_SPEED);
    }

    /**
     * 设置平滑缩放参数。
     * @param enabled true = 平滑缩放，false = 直接跳变到目标大小
     * @param speed 速度倍率（1.0 = 默认速度），最小 0.1
     */
    public void setSmoothScaling(boolean enabled, float speed) {
        this.smoothScaling = enabled;
        this.sizeSpeed = Math.max(0.1f, speed * BASE_SIZE_SPEED);
    }

    /**
     * 设置透明度过渡速度。
     * 通过指数平滑控制 fade-in/fade-out 过渡时长。
     * speed 越高过渡越快。约 5/speed 秒到达 ~99% 的目标值。
     *
     * @param speed 透明度过渡速度，最小 0.1，默认 ALPHA_SPEED=18.0f
     */
    public void setAlphaSpeed(float speed) {
        this.alphaSpeed = Math.max(0.1f, speed);
    }

    private static float computeRenderX(TooltipAnchor anchor, float anchorX, float width) {
        if (anchor == TooltipAnchor.RIGHT_TOP) {
            return anchorX - width;
        }
        return anchorX;
    }

}
