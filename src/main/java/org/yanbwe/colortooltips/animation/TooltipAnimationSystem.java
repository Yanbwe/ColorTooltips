package org.yanbwe.colortooltips.animation;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

public class TooltipAnimationSystem {
    private static final TooltipAnimator ANIMATOR = new TooltipAnimator();
    private static final long FADE_OUT_DELAY_MS = 100L;
    private static final long SWITCH_FLASH_DURATION_MS = 250L;
    private static TooltipState lastState = new TooltipState();
    private static ItemStack activeStack = ItemStack.EMPTY;
    private static long lastVisibleTimeMs;
    private static long lostCandidateStartTimeMs;
    private static boolean visibleRequested;
    private static long switchFlashStartTimeMs = -1L;

    static {
        ANIMATOR.reset();
        lastState = ANIMATOR.tickAndGet();
    }

    public static TooltipState update(ItemStack stack, TooltipTarget target) {
        if (stack == null || stack.isEmpty()) {
            return lastState;
        }

        ANIMATOR.setTarget(target);
        processEvents();
        lastState = ANIMATOR.tickAndGet();
        return lastState;
    }

    public static void onLiveItemObserved(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        long now = Util.getMillis();
        if (activeStack.isEmpty()) {
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SHOW_FROM_HIDDEN);
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SWITCH_ITEM);
            activeStack = stack.copy();
        } else if (!ItemStack.isSameItemSameTags(activeStack, stack)) {
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SWITCH_ITEM);
            activeStack = stack.copy();
        }

        visibleRequested = true;
        lastVisibleTimeMs = now;
        lostCandidateStartTimeMs = 0L;
    }

    public static void onFrameWithoutLiveItem() {
        if (activeStack.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        if (lostCandidateStartTimeMs == 0L) {
            lostCandidateStartTimeMs = now;
            return;
        }

        if (now - lostCandidateStartTimeMs >= FADE_OUT_DELAY_MS) {
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.LOST_CONFIRMED);
            activeStack = ItemStack.EMPTY;
            visibleRequested = false;
            lostCandidateStartTimeMs = 0L;
        }
    }

    public static float computeTargetAlpha() {
        return visibleRequested ? 1.0f : 0.0f;
    }

    public static float getItemScale() {
        float t = easeOutCubic(lastState.transitionProgress);
        return 0.5f + 0.5f * t;
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
        if (elapsed >= SWITCH_FLASH_DURATION_MS) {
            return -1.0f;
        }
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) SWITCH_FLASH_DURATION_MS));
        return easeOutCubic(t);
    }

    public static boolean shouldRenderCachedTooltip() {
        return visibleRequested || getAlpha() > 0.01f || getSwitchFlashProgress() >= 0.0f;
    }

    public static void reset() {
        activeStack = ItemStack.EMPTY;
        lastVisibleTimeMs = 0L;
        lostCandidateStartTimeMs = 0L;
        visibleRequested = false;
        switchFlashStartTimeMs = -1L;
        TooltipLifecycleEventBus.clear();
        ANIMATOR.reset();
        lastState = ANIMATOR.tickAndGet();
    }

    private static void processEvents() {
        TooltipLifecycleEventType eventType;
        while ((eventType = TooltipLifecycleEventBus.poll()) != null) {
            if (eventType == TooltipLifecycleEventType.SWITCH_ITEM) {
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
