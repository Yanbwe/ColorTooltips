package org.yanbwe.colortooltips.animation;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.colortooltips.Config;
import org.yanbwe.colortooltips.util.ColorUtils;

public class TooltipAnimationSystem {
    private static final TooltipAnimator ANIMATOR = new TooltipAnimator();
    private static TooltipState lastState = new TooltipState();
    private static ItemStack activeStack = ItemStack.EMPTY;
    private static boolean isTextOnlyMode = false; // 标记是否为纯文本模式
    private static long lastVisibleTimeMs;
    private static long lostCandidateStartTimeMs;
    private static boolean visibleRequested;
    private static long switchFlashStartTimeMs = -1L;
    private static long fadeInStartTimeMs = -1L;

    // 颜色过渡动画（与物理引擎分离）：目标颜色变化时 250ms easeOutCubic 渐变
    private static final long COLOR_ANIM_DURATION_MS = 250L;
    private static int colorAnimFromArgb;
    private static int colorAnimToArgb;
    private static long colorAnimStartMs = -1L;
    private static int lastTargetColorArgb = 0;

    static {
        ANIMATOR.reset();
        lastState = ANIMATOR.tickAndGet();
    }

    public static TooltipState update(ItemStack stack, TooltipTarget target) {
        // null 检查仍然需要，但空物品堆允许继续更新
        if (stack == null) {
            return lastState;
        }

        int newTargetColor = target.colorArgb;

        // 颜色动画：检测目标颜色变化（与物理引擎分离）
        if (colorAnimStartMs < 0L) {
            // 未在动画中：检测颜色是否变化
            if (newTargetColor != lastTargetColorArgb) {
                if (lastTargetColorArgb != 0) {
                    // 从当前显示的颜色渐变到新目标色
                    startColorAnimation(lastState.colorArgb, newTargetColor);
                }
                lastTargetColorArgb = newTargetColor;
            }
        } else if (newTargetColor != colorAnimToArgb) {
            // 动画进行中被中断：从当前插值颜色重新开始
            startColorAnimation(sampleCurrentColorAnim(), newTargetColor);
            lastTargetColorArgb = newTargetColor;
        }

        ANIMATOR.setTarget(target);
        processEvents();
        lastState = ANIMATOR.tickAndGet();

        // 颜色动画后处理（覆盖物理引擎的即时颜色）
        lastState.colorArgb = applyColorAnimation(lastState.colorArgb);

        return lastState;
    }

    /**
     * 为纯文本tooltip触发动画系统
     * 即使没有物品也能触发显示
     */
    public static void onTextOnlyTooltipObserved() {
        long now = Util.getMillis();

        // 从隐藏状态首次显示纯文本提示框
        if (!isTextOnlyMode && activeStack.isEmpty()) {
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SHOW_FROM_HIDDEN);
        }

        // 清除物品栈，标记为纯文本模式
        isTextOnlyMode = true;
        activeStack = ItemStack.EMPTY;
        visibleRequested = true;
        lastVisibleTimeMs = now;
        lostCandidateStartTimeMs = 0L;
    }

    /**
     * 检查纯文本tooltip是否应该显示
     */
    public static boolean isTextOnlyTooltipVisible() {
        return visibleRequested && isTextOnlyMode;
    }

    public static void onLiveItemObserved(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        long now = Util.getMillis();

        boolean isFirstAppearance = activeStack.isEmpty();
        boolean itemChanged = !isFirstAppearance && !ItemStack.isSameItemSameTags(activeStack, stack);

        if (isTextOnlyMode) {
            // 从纯文本切换到物品 → 内容切换动画（alpha 保持，不重新淡入）
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SWITCH_ITEM);
        } else if (isFirstAppearance) {
            // 从隐藏状态重新出现 → 淡入动画
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SHOW_FROM_HIDDEN);
        } else if (itemChanged) {
            // 物品切换到不同物品 → 切换动画
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SWITCH_ITEM);
        }

        isTextOnlyMode = false;
        activeStack = stack.copy();
        visibleRequested = true;
        lastVisibleTimeMs = now;
        lostCandidateStartTimeMs = 0L;
    }

    public static void onFrameWithoutLiveItem() {
        if (TooltipLockManager.shouldPreventTooltipHide()) {
            return;
        }

        // 纯文本模式下也允许消失
        if (activeStack.isEmpty() && !isTextOnlyMode) {
            return;
        }
        long now = Util.getMillis();
        if (lostCandidateStartTimeMs == 0L) {
            lostCandidateStartTimeMs = now;
            return;
        }

        if (now - lostCandidateStartTimeMs >= Config.FADE_OUT_DELAY.get()) {
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.LOST_CONFIRMED);
            activeStack = ItemStack.EMPTY;
            isTextOnlyMode = false;
            visibleRequested = false;
            lostCandidateStartTimeMs = 0L;
        }
    }

    public static float computeTargetAlpha() {
        if (!visibleRequested) {
            return 0.0f;
        }
        
        if (fadeInStartTimeMs < 0L) {
            return 1.0f;
        }
        
        long now = Util.getMillis();
        long elapsed = now - fadeInStartTimeMs;
        if (elapsed >= Config.FADE_IN_DURATION.get()) {
            fadeInStartTimeMs = -1L;
            return 1.0f;
        }

        float progress = Math.max(0.0f, Math.min(1.0f, elapsed / (float) Config.FADE_IN_DURATION.get()));
        return easeOutCubic(progress);
    }

    public static float getItemScale() {
        float t = easeOutCubic(lastState.transitionProgress);
        return Config.ITEM_SCALE_MIN.get().floatValue() + (1.0f - Config.ITEM_SCALE_MIN.get().floatValue()) * t;
    }

    public static float getTransitionProgress() {
        return easeOutCubic(lastState.transitionProgress);
    }

    public static float getAlpha() {
        return Math.max(0.0f, Math.min(1.0f, lastState.alpha));
    }

    public static int getTargetWidthInt() {
        return ANIMATOR.getTargetWidthInt();
    }

    public static int getTargetHeightInt() {
        return ANIMATOR.getTargetHeightInt();
    }

    public static float getSwitchFlashProgress() {
        if (switchFlashStartTimeMs < 0L) {
            return -1.0f;
        }
        long elapsed = Util.getMillis() - switchFlashStartTimeMs;
        if (elapsed >= Config.SWITCH_FLASH_DURATION.get()) {
            return -1.0f;
        }
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) Config.SWITCH_FLASH_DURATION.get()));
        return easeOutCubic(t);
    }

    public static boolean shouldRenderCachedTooltip() {
        return visibleRequested || getAlpha() > 0.01f || getSwitchFlashProgress() >= 0.0f;
    }

    public static void reset() {
        activeStack = ItemStack.EMPTY;
        isTextOnlyMode = false;
        lastVisibleTimeMs = 0L;
        lostCandidateStartTimeMs = 0L;
        visibleRequested = false;
        switchFlashStartTimeMs = -1L;
        fadeInStartTimeMs = -1L;
        colorAnimStartMs = -1L;
        lastTargetColorArgb = 0;
        TooltipLifecycleEventBus.clear();
        ANIMATOR.reset();
        lastState = ANIMATOR.tickAndGet();
        TooltipLockManager.reset();
    }

    public static float getLockOffsetY() {
        return TooltipLockManager.getOffsetY();
    }

    public static boolean isLocked() {
        return TooltipLockManager.hasValidLock();
    }

    public static float getLockedAnchorY() {
        return TooltipLockManager.getLockedAnchorY();
    }

    private static void processEvents() {
        TooltipLifecycleEventType eventType;
        while ((eventType = TooltipLifecycleEventBus.poll()) != null) {
            if (eventType == TooltipLifecycleEventType.SHOW_FROM_HIDDEN) {
                ANIMATOR.resetAlphaToZero();
                ANIMATOR.restartTransition();
                fadeInStartTimeMs = Util.getMillis();
                TooltipLockManager.resetOffsets();
            } else if (eventType == TooltipLifecycleEventType.SWITCH_ITEM) {
                ANIMATOR.resetAlpha();
                ANIMATOR.restartTransition();
                switchFlashStartTimeMs = Util.getMillis();
                TooltipLockManager.resetOffsets();
            }
        }
    }

    // ---- 颜色过渡动画（独立于物理引擎） ----

    private static void startColorAnimation(int fromColor, int toColor) {
        colorAnimFromArgb = fromColor;
        colorAnimToArgb = toColor;
        colorAnimStartMs = Util.getMillis();
    }

    /**
     * 采样当前动画插值颜色（无副作用，仅用于中断时获取当前值）
     */
    private static int sampleCurrentColorAnim() {
        if (colorAnimStartMs < 0L) return colorAnimToArgb;
        long elapsed = Util.getMillis() - colorAnimStartMs;
        if (elapsed >= COLOR_ANIM_DURATION_MS) {
            return colorAnimToArgb;
        }
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) COLOR_ANIM_DURATION_MS));
        float eased = easeOutCubic(t);
        return ColorUtils.interpolateColor(colorAnimFromArgb, colorAnimToArgb, eased);
    }

    /**
     * 应用颜色动画，返回插值后的颜色
     * 动画结束后自动清理状态并返回目标色
     */
    private static int applyColorAnimation(int fallbackColor) {
        if (colorAnimStartMs < 0L) {
            return fallbackColor;
        }
        long elapsed = Util.getMillis() - colorAnimStartMs;
        if (elapsed >= COLOR_ANIM_DURATION_MS) {
            colorAnimStartMs = -1L;
            return colorAnimToArgb;
        }
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) COLOR_ANIM_DURATION_MS));
        float eased = easeOutCubic(t);
        return ColorUtils.interpolateColor(colorAnimFromArgb, colorAnimToArgb, eased);
    }

    private static float easeOutCubic(float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - (float) Math.pow(1.0f - clamped, 3.0f);
    }
}
