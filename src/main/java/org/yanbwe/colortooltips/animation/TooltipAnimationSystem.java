package org.yanbwe.colortooltips.animation;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.colortooltips.Config;

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

    static {
        ANIMATOR.reset();
        lastState = ANIMATOR.tickAndGet();
    }

    public static TooltipState update(ItemStack stack, TooltipTarget target) {
        // null 检查仍然需要，但空物品堆允许继续更新
        if (stack == null) {
            return lastState;
        }

        ANIMATOR.setTarget(target);
        processEvents();
        lastState = ANIMATOR.tickAndGet();
        return lastState;
    }

    /**
     * 为纯文本tooltip触发动画系统
     * 即使没有物品也能触发显示
     */
    public static void onTextOnlyTooltipObserved(float anchorY) {
        TooltipLockManager.onAnchorPositionUpdated(anchorY);
        
        long now = Util.getMillis();
        
        // 无论之前是否有物品，现在都需要显示纯文本tooltip
        if (!isTextOnlyMode) {
            // 从有物品切换到纯文本，或首次显示纯文本
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SHOW_FROM_HIDDEN);
        }
        
        // 标记为纯文本模式
        isTextOnlyMode = true;
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

    public static void onLiveItemObserved(ItemStack stack, float currentAnchorY) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        TooltipLockManager.onAnchorPositionUpdated(currentAnchorY);

        long now = Util.getMillis();
        
        // 清除纯文本模式，切换到物品模式
        if (isTextOnlyMode) {
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
            } else if (eventType == TooltipLifecycleEventType.SWITCH_ITEM) {
                ANIMATOR.resetAlpha();
                ANIMATOR.restartTransition();
                switchFlashStartTimeMs = Util.getMillis();
            }
        }
    }

    private static float easeOutCubic(float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - (float) Math.pow(1.0f - clamped, 3.0f);
    }
}
