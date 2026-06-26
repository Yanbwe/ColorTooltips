package org.yanbwe.colortooltips.animation;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.colortooltips.config.ConfigManager;
import org.yanbwe.colortooltips.config.DynamicColorResolver;
import org.yanbwe.colortooltips.config.StyleDefinition;
import org.yanbwe.colortooltips.util.ColorUtils;

public class TooltipAnimationSystem {
    private static final TooltipAnimator ANIMATOR = new TooltipAnimator();
    private static TooltipState lastState = new TooltipState();
    private static ItemStack activeStack = ItemStack.EMPTY;
    private static boolean isTextOnlyMode = false;
    private static long lastVisibleTimeMs;
    private static long lostCandidateStartTimeMs;
    private static boolean visibleRequested;
    private static long switchFlashStartTimeMs = -1L;
    private static long fadeInStartTimeMs = -1L;

    private static int fadeOutDelay = 500;
    private static int fadeOutDurationMs = 100;
    private static int fadeInDuration = 100;
    private static int switchFlashDuration = 500;
    private static double itemScaleMin = 0.5;
    private static int switchFlashColor = 0xFFFFFFFF;

    private static boolean fadeOutEnabled = true;
    private static boolean fadeInEnabled = true;
    private static boolean switchEffectEnabled = true;
    private static boolean switchScaleEnabled = true;
    private static boolean appearScaleEnabled = true;
    private static double itemScaleMinSwitch = 0.5;
    private static double itemScaleMinAppear = 0.5;

    private static final long COLOR_ANIM_DURATION_MS = 250L;
    private static int colorAnimFromArgb;
    private static int colorAnimToArgb;
    private static long colorAnimStartMs = -1L;
    private static int lastTargetColorArgb = 0;
    private static int displayedColor = 0;

    private static final long CROSSFADE_DURATION_MS = 250L;
    private static long crossfadeStartMs = -1L;
    private static String lastStyleName = null;
    private static int crossfadeOldBorderColor;
    private static int crossfadeOldBgColor;
    private static int crossfadeNewBorderColor;
    private static int crossfadeNewBgColor;

    private static boolean needsAnimatorReset = false;

    static {
        ANIMATOR.reset();
        lastState = ANIMATOR.tickAndGet();
        applyAnimationDefaults();
    }

    public static TooltipState update(ItemStack stack, TooltipTarget target) {
        if (stack == null) return lastState;

        int newTargetColor = target.colorArgb;

        if (ConfigManager.getInstance().isSmoothColor()) {
            if (colorAnimStartMs < 0L) {
                if (newTargetColor != lastTargetColorArgb) {
                    if (lastTargetColorArgb != 0 && displayedColor != 0) {
                        startColorAnimation(displayedColor, newTargetColor);
                    }
                    lastTargetColorArgb = newTargetColor;
                }
            } else if (newTargetColor != colorAnimToArgb) {
                startColorAnimation(sampleCurrentColorAnim(), newTargetColor);
                lastTargetColorArgb = newTargetColor;
            }
        } else {
            colorAnimStartMs = -1L;
            lastTargetColorArgb = newTargetColor;
        }

        if (needsAnimatorReset) {
            ANIMATOR.forceInit(target);
            needsAnimatorReset = false;
        } else {
            ANIMATOR.setTarget(target);
        }
        processEvents();
        lastState = ANIMATOR.tickAndGet();
        lastState.colorArgb = applyColorAnimation(lastState.colorArgb);
        displayedColor = lastState.colorArgb;

        return lastState;
    }

    public static void onTextOnlyTooltipObserved() {
        long now = Util.getMillis();
        if (!isTextOnlyMode && activeStack.isEmpty()) {
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SHOW_FROM_HIDDEN);
        }
        isTextOnlyMode = true;
        activeStack = ItemStack.EMPTY;
        visibleRequested = true;
        lastVisibleTimeMs = now;
        lostCandidateStartTimeMs = 0L;
    }

    public static boolean isTextOnlyTooltipVisible() {
        return visibleRequested && isTextOnlyMode;
    }

    public static void onLiveItemObserved(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        long now = Util.getMillis();

        boolean isFirstAppearance = activeStack.isEmpty();
        // NeoForge 1.21.1 适配：isSameItemSameTags → isSameItemSameComponents
        boolean itemChanged = !isFirstAppearance && !ItemStack.isSameItemSameComponents(activeStack, stack);

        if (isTextOnlyMode) {
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SWITCH_ITEM);
        } else if (isFirstAppearance) {
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SHOW_FROM_HIDDEN);
        } else if (itemChanged) {
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.SWITCH_ITEM);
        }

        isTextOnlyMode = false;
        activeStack = stack.copy();
        visibleRequested = true;
        lastVisibleTimeMs = now;
        lostCandidateStartTimeMs = 0L;
    }

    public static void onFrameWithoutLiveItem() {
        if (TooltipLockManager.shouldPreventTooltipHide()) return;
        if (activeStack.isEmpty() && !isTextOnlyMode) return;
        if (!fadeOutEnabled) return;

        long now = Util.getMillis();
        if (lostCandidateStartTimeMs == 0L) {
            lostCandidateStartTimeMs = now;
            return;
        }

        if (now - lostCandidateStartTimeMs >= fadeOutDelay) {
            TooltipLifecycleEventBus.publish(TooltipLifecycleEventType.LOST_CONFIRMED);
            activeStack = ItemStack.EMPTY;
            isTextOnlyMode = false;
            visibleRequested = false;
            lostCandidateStartTimeMs = 0L;
        }
    }

    public static float computeTargetAlpha() {
        if (!visibleRequested) return 0.0f;
        if (!fadeInEnabled || fadeInStartTimeMs < 0L) return 1.0f;

        long now = Util.getMillis();
        long elapsed = now - fadeInStartTimeMs;
        if (elapsed >= fadeInDuration) {
            fadeInStartTimeMs = -1L;
            return 1.0f;
        }
        float progress = Math.max(0.0f, Math.min(1.0f, elapsed / (float) fadeInDuration));
        return easeOutCubic(progress);
    }

    public static float getItemScale() {
        float t = easeOutCubic(lastState.transitionProgress);
        return (float) itemScaleMin + (1.0f - (float) itemScaleMin) * t;
    }

    public static float getTransitionProgress() {
        return easeOutCubic(lastState.transitionProgress);
    }

    public static float getAlpha() {
        return Math.max(0.0f, Math.min(1.0f, lastState.alpha));
    }

    public static int getTargetWidthInt() { return ANIMATOR.getTargetWidthInt(); }
    public static int getTargetHeightInt() { return ANIMATOR.getTargetHeightInt(); }

    public static float getSwitchFlashProgress() {
        if (switchFlashStartTimeMs < 0L) return -1.0f;
        long elapsed = Util.getMillis() - switchFlashStartTimeMs;
        if (elapsed >= switchFlashDuration) return -1.0f;
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) switchFlashDuration));
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
        displayedColor = 0;
        crossfadeStartMs = -1L;
        lastStyleName = null;
        applyAnimationDefaults();
        TooltipLifecycleEventBus.clear();
        ANIMATOR.reset();
        lastState = ANIMATOR.tickAndGet();
        TooltipLockManager.reset();
    }

    public static float getLockOffsetY() { return TooltipLockManager.getOffsetY(); }
    public static boolean isLocked() { return TooltipLockManager.hasValidLock(); }
    public static float getLockedAnchorY() { return TooltipLockManager.getLockedAnchorY(); }
    public static void setNeedsAnimatorReset(boolean needsReset) { needsAnimatorReset = needsReset; }

    public static TooltipState createInstantState(TooltipTarget target) {
        TooltipState state = new TooltipState();
        state.width = target.width;
        state.height = target.height;
        state.anchorX = target.anchorX;
        state.anchorY = target.anchorY;
        state.renderOffsetX = 0;
        state.renderOffsetY = 0;
        state.anchor = target.anchor;
        state.alpha = target.alpha;
        state.transitionProgress = 1.0f;
        state.colorArgb = target.colorArgb;
        state.colorArgb = applyColorAnimation(state.colorArgb);
        return state;
    }

    public static void applyStyle(StyleDefinition style, ItemStack stack) {
        if (style == null || style.getAnimation() == null) {
            applyAnimationDefaults();
            return;
        }

        boolean hasValidStack = stack != null && !stack.isEmpty();
        StyleDefinition.AnimationConfig anim = style.getAnimation();

        StyleDefinition.AnimationConfig.FadeOutConfig fadeOut = anim.getFadeOut();
        if (fadeOut != null) {
            fadeOutEnabled = fadeOut.isEnabled();
            fadeOutDelay = fadeOut.getDelay();
            fadeOutDurationMs = fadeOut.getDuration();
            float alphaSpd = fadeOutDurationMs > 0 ? 5000.0f / fadeOutDurationMs : 18.0f;
            ANIMATOR.setAlphaSpeed(alphaSpd);
        } else {
            fadeOutEnabled = true; fadeOutDelay = 500; fadeOutDurationMs = 100;
            ANIMATOR.setAlphaSpeed(18.0f);
        }

        StyleDefinition.AnimationConfig.FadeInConfig fadeIn = anim.getFadeIn();
        if (fadeIn != null) {
            fadeInEnabled = fadeIn.isEnabled();
            fadeInDuration = fadeIn.getDuration();
        } else {
            fadeInEnabled = true; fadeInDuration = 100;
        }

        StyleDefinition.AnimationConfig.SmoothMovementConfig smoothMove = anim.getSmoothMovement();
        if (smoothMove != null) {
            ANIMATOR.setSmoothMovement(smoothMove.isRealTimeEnabled(), (float) smoothMove.getSpeed());
        } else {
            ANIMATOR.setSmoothMovement(true, 1.0f);
        }

        StyleDefinition.AnimationConfig.SmoothScalingConfig smoothScale = anim.getSmoothScaling();
        if (smoothScale != null) {
            ANIMATOR.setSmoothScaling(smoothScale.isEnabled(), (float) smoothScale.getSpeed());
        } else {
            ANIMATOR.setSmoothScaling(true, 1.0f);
        }

        if (!hasValidStack) return;

        StyleDefinition.AnimationConfig.SwitchEffectConfig switchEffect = anim.getSwitchEffect();
        if (switchEffect != null) {
            switchEffectEnabled = switchEffect.isEnabled();
            switchFlashDuration = switchEffect.getDuration();
            switchFlashColor = DynamicColorResolver.resolve(switchEffect.getColor(), stack);
        } else {
            switchEffectEnabled = true; switchFlashDuration = 500; switchFlashColor = 0xFFFFFFFF;
        }

        StyleDefinition.AnimationConfig.ItemModelAnimConfig itemModelAnim = anim.getItemModel();
        if (itemModelAnim != null) {
            if (itemModelAnim.getSwitching() != null) {
                switchScaleEnabled = itemModelAnim.getSwitching().isEnabled();
                itemScaleMinSwitch = itemModelAnim.getSwitching().getStartSize();
            } else { switchScaleEnabled = true; itemScaleMinSwitch = 0.5; }
            if (itemModelAnim.getAppearing() != null) {
                appearScaleEnabled = itemModelAnim.getAppearing().isEnabled();
                itemScaleMinAppear = itemModelAnim.getAppearing().getStartSize();
            } else { appearScaleEnabled = true; itemScaleMinAppear = 0.5; }
        } else {
            switchScaleEnabled = true; appearScaleEnabled = true;
            itemScaleMinSwitch = 0.5; itemScaleMinAppear = 0.5;
        }
        itemScaleMin = itemScaleMinSwitch;
    }

    private static void applyAnimationDefaults() {
        fadeOutEnabled = true; fadeOutDelay = 500; fadeOutDurationMs = 100;
        fadeInEnabled = true; fadeInDuration = 100;
        switchEffectEnabled = true; switchFlashDuration = 500; switchFlashColor = 0xFFFFFFFF;
        switchScaleEnabled = true; appearScaleEnabled = true;
        itemScaleMinSwitch = 0.5; itemScaleMinAppear = 0.5; itemScaleMin = 0.5;
        ANIMATOR.setAlphaSpeed(18.0f);
        ANIMATOR.setSmoothMovement(true, 1.0f);
        ANIMATOR.setSmoothScaling(true, 1.0f);
    }

    public static int getSwitchFlashColor() { return switchFlashColor; }

    public static void startCrossfade(String styleName, int oldBorderColor, int oldBgColor,
                                       int newBorderColor, int newBgColor) {
        lastStyleName = styleName;
        crossfadeOldBorderColor = oldBorderColor;
        crossfadeOldBgColor = oldBgColor;
        crossfadeNewBorderColor = newBorderColor;
        crossfadeNewBgColor = newBgColor;
        crossfadeStartMs = Util.getMillis();
    }

    public static float getCrossfadeProgress() {
        if (crossfadeStartMs < 0L) return -1.0f;
        long elapsed = Util.getMillis() - crossfadeStartMs;
        if (elapsed >= CROSSFADE_DURATION_MS) { crossfadeStartMs = -1L; return -1.0f; }
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) CROSSFADE_DURATION_MS));
        return easeOutCubic(t);
    }

    public static int getCrossfadeOldBorderColor() { return crossfadeOldBorderColor; }
    public static int getCrossfadeOldBgColor() { return crossfadeOldBgColor; }
    public static int getCrossfadeNewBorderColor() { return crossfadeNewBorderColor; }
    public static int getCrossfadeNewBgColor() { return crossfadeNewBgColor; }
    public static String getLastStyleName() { return lastStyleName; }

    private static void processEvents() {
        TooltipLifecycleEventType eventType;
        while ((eventType = TooltipLifecycleEventBus.poll()) != null) {
            if (eventType == TooltipLifecycleEventType.SHOW_FROM_HIDDEN) {
                ANIMATOR.resetAlphaToZero();
                if (appearScaleEnabled) { ANIMATOR.restartTransition(); itemScaleMin = itemScaleMinAppear; }
                fadeInStartTimeMs = Util.getMillis();
                TooltipLockManager.resetOffsets();
            } else if (eventType == TooltipLifecycleEventType.SWITCH_ITEM) {
                ANIMATOR.resetAlpha();
                if (switchScaleEnabled) { ANIMATOR.restartTransition(); itemScaleMin = itemScaleMinSwitch; }
                if (switchEffectEnabled) { switchFlashStartTimeMs = Util.getMillis(); }
                TooltipLockManager.resetOffsets();
            }
        }
    }

    private static void startColorAnimation(int fromColor, int toColor) {
        colorAnimFromArgb = fromColor;
        colorAnimToArgb = toColor;
        colorAnimStartMs = Util.getMillis();
    }

    private static int sampleCurrentColorAnim() {
        if (colorAnimStartMs < 0L) return colorAnimToArgb;
        long elapsed = Util.getMillis() - colorAnimStartMs;
        if (elapsed >= COLOR_ANIM_DURATION_MS) return colorAnimToArgb;
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) COLOR_ANIM_DURATION_MS));
        return ColorUtils.interpolateColorHSV(colorAnimFromArgb, colorAnimToArgb, easeOutCubic(t));
    }

    private static int applyColorAnimation(int fallbackColor) {
        if (colorAnimStartMs < 0L) return fallbackColor;
        long elapsed = Util.getMillis() - colorAnimStartMs;
        if (elapsed >= COLOR_ANIM_DURATION_MS) { colorAnimStartMs = -1L; return colorAnimToArgb; }
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) COLOR_ANIM_DURATION_MS));
        return ColorUtils.interpolateColorHSV(colorAnimFromArgb, colorAnimToArgb, easeOutCubic(t));
    }

    private static float easeOutCubic(float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - (float) Math.pow(1.0f - clamped, 3.0f);
    }
}
