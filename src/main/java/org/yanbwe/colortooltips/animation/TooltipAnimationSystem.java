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
    private static boolean isTextOnlyMode = false; // 标记是否为纯文本模式
    private static long lastVisibleTimeMs;
    private static long lostCandidateStartTimeMs;
    private static boolean visibleRequested;
    private static long switchFlashStartTimeMs = -1L;
    private static long fadeInStartTimeMs = -1L;

    // 样式驱动的动画参数（由 applyStyle() 更新）
    private static int fadeOutDelay = 500;
    private static int fadeOutDurationMs = 100;
    private static int fadeInDuration = 100;
    private static int switchFlashDuration = 500;
    private static double itemScaleMin = 0.5;
    private static int switchFlashColor = 0xFFFFFFFF;

    // 样式驱动的功能开关（由 applyStyle() 更新）
    private static boolean fadeOutEnabled = true;
    private static boolean fadeInEnabled = true;
    private static boolean switchEffectEnabled = true;
    private static boolean switchScaleEnabled = true;
    private static boolean appearScaleEnabled = true;
    private static double itemScaleMinSwitch = 0.5;
    private static double itemScaleMinAppear = 0.5;

    // 颜色过渡动画（与物理引擎分离）：目标颜色变化时 250ms easeOutCubic HSV 渐变
    private static final long COLOR_ANIM_DURATION_MS = 250L;
    private static int colorAnimFromArgb;
    private static int colorAnimToArgb;
    private static long colorAnimStartMs = -1L;
    private static int lastTargetColorArgb = 0;
    /** 上一帧实际显示的颜色（由 applyColorAnimation 输出），确保动画始终从正确起点开始 */
    private static int displayedColor = 0;

    // 跨样式 Crossfade：样式名变化时 250ms easeOutCubic 颜色混合
    private static final long CROSSFADE_DURATION_MS = 250L;
    private static long crossfadeStartMs = -1L;
    private static String lastStyleName = null;
    private static int crossfadeOldBorderColor;
    private static int crossfadeOldBgColor;
    private static int crossfadeNewBorderColor;
    private static int crossfadeNewBgColor;

    static {
        ANIMATOR.reset();
        lastState = ANIMATOR.tickAndGet();
        applyAnimationDefaults();
    }

    public static TooltipState update(ItemStack stack, TooltipTarget target) {
        // null 检查仍然需要，但空物品堆允许继续更新
        if (stack == null) {
            return lastState;
        }

        int newTargetColor = target.colorArgb;

        // 颜色动画 / crossfade：检测目标颜色变化（与物理引擎分离）
        // smoothColor=true 时启用 250ms easeOutCubic 颜色渐变（效果接近 crossfade）
        // smoothColor=false 时颜色直接跳变（清空动画状态，立即使用新颜色）
        if (ConfigManager.getInstance().isSmoothColor()) {
            if (colorAnimStartMs < 0L) {
                // 未在动画中：检测颜色是否变化
                if (newTargetColor != lastTargetColorArgb) {
                    if (lastTargetColorArgb != 0 && displayedColor != 0) {
                        // 从上一帧实际显示的颜色渐变到新目标色
                        startColorAnimation(displayedColor, newTargetColor);
                    }
                    lastTargetColorArgb = newTargetColor;
                }
            } else if (newTargetColor != colorAnimToArgb) {
                // 动画进行中被中断：从当前插值颜色重新开始
                startColorAnimation(sampleCurrentColorAnim(), newTargetColor);
                lastTargetColorArgb = newTargetColor;
            }
        } else {
            // smoothColor=false：直接跳变，清除任何进行中的颜色动画
            colorAnimStartMs = -1L;
            lastTargetColorArgb = newTargetColor;
        }

        ANIMATOR.setTarget(target);
        processEvents();
        lastState = ANIMATOR.tickAndGet();

        // 颜色动画后处理（覆盖物理引擎的即时颜色）
        lastState.colorArgb = applyColorAnimation(lastState.colorArgb);
        displayedColor = lastState.colorArgb;

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
        
        // fadeOut.enabled=false 时不触发消失逻辑，提示框无限保持
        if (!fadeOutEnabled) {
            return;
        }
        
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
        if (!visibleRequested) {
            return 0.0f;
        }
        
        // fadeIn.enabled=false 时跳过淡入动画，直接返回完整不透明度
        if (!fadeInEnabled || fadeInStartTimeMs < 0L) {
            return 1.0f;
        }
        
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
        if (elapsed >= switchFlashDuration) {
            return -1.0f;
        }
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

    public static float getLockOffsetY() {
        return TooltipLockManager.getOffsetY();
    }

    public static boolean isLocked() {
        return TooltipLockManager.hasValidLock();
    }

    public static float getLockedAnchorY() {
        return TooltipLockManager.getLockedAnchorY();
    }

    // ══════════════════════════════════════════════════════════
    // 样式驱动的动画参数更新
    // ══════════════════════════════════════════════════════════

    /**
     * 从样式定义中更新动画参数。
     * <p>
     * 每次样式切换时调用（例如物品切换导致样式改变），刷新淡入淡出时长、
     * 闪光特效参数、物品模型缩放和平滑移动/缩放行为。
     * <p>
     * 纯文本提示框模式（stack 为空）下自动降级使用硬编码默认值。
     *
     * @param style 样式定义，可为 null（降级使用默认值）
     * @param stack 当前物品栈，用于解析动态颜色标记（如 switchEffect.color 中的 @rarityCore）
     */
    public static void applyStyle(StyleDefinition style, ItemStack stack) {
        if (style == null || style.getAnimation() == null) {
            applyAnimationDefaults();
            return;
        }

        boolean hasValidStack = stack != null && !stack.isEmpty();
        StyleDefinition.AnimationConfig anim = style.getAnimation();

        // 淡出参数（不依赖物品栈）
        StyleDefinition.AnimationConfig.FadeOutConfig fadeOut = anim.getFadeOut();
        if (fadeOut != null) {
            fadeOutEnabled = fadeOut.isEnabled();
            fadeOutDelay = fadeOut.getDelay();
            fadeOutDurationMs = fadeOut.getDuration();
            float alphaSpd = fadeOutDurationMs > 0 ? 5000.0f / fadeOutDurationMs : 18.0f;
            ANIMATOR.setAlphaSpeed(alphaSpd);
        } else {
            fadeOutEnabled = true;
            fadeOutDelay = 500;
            fadeOutDurationMs = 100;
            ANIMATOR.setAlphaSpeed(18.0f);
        }

        // 淡入参数（不依赖物品栈）
        StyleDefinition.AnimationConfig.FadeInConfig fadeIn = anim.getFadeIn();
        if (fadeIn != null) {
            fadeInEnabled = fadeIn.isEnabled();
            fadeInDuration = fadeIn.getDuration();
        } else {
            fadeInEnabled = true;
            fadeInDuration = 100;
        }

        // 平滑移动/缩放参数（不依赖物品栈）
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

        // 以下参数依赖有效物品栈
        StyleDefinition.AnimationConfig.SwitchEffectConfig switchEffect = anim.getSwitchEffect();
        if (switchEffect != null) {
            switchEffectEnabled = switchEffect.isEnabled();
            switchFlashDuration = switchEffect.getDuration();
            // 通过 DynamicColorResolver 解析闪光颜色（基于切换后的物品）
            switchFlashColor = DynamicColorResolver.resolve(switchEffect.getColor(), stack);
        } else {
            switchEffectEnabled = true;
            switchFlashDuration = 500;
            switchFlashColor = 0xFFFFFFFF;
        }

        // 物品模型切换动画的起始缩放
        StyleDefinition.AnimationConfig.ItemModelAnimConfig itemModelAnim = anim.getItemModel();
        if (itemModelAnim != null) {
            if (itemModelAnim.getSwitching() != null) {
                switchScaleEnabled = itemModelAnim.getSwitching().isEnabled();
                itemScaleMinSwitch = itemModelAnim.getSwitching().getStartSize();
            } else {
                switchScaleEnabled = true;
                itemScaleMinSwitch = 0.5;
            }
            if (itemModelAnim.getAppearing() != null) {
                appearScaleEnabled = itemModelAnim.getAppearing().isEnabled();
                itemScaleMinAppear = itemModelAnim.getAppearing().getStartSize();
            } else {
                appearScaleEnabled = true;
                itemScaleMinAppear = 0.5;
            }
        } else {
            switchScaleEnabled = true;
            appearScaleEnabled = true;
            itemScaleMinSwitch = 0.5;
            itemScaleMinAppear = 0.5;
        }
        // 默认使用 switch 的缩放值（processEvents 中会根据事件类型覆盖）
        itemScaleMin = itemScaleMinSwitch;
    }

    /**
     * 降级使用硬编码默认动画参数（与 AnimationConfig.createDefault() 一致）。
     * 用于样式缺失、纯文本提示框模式或系统重置时。
     */
    private static void applyAnimationDefaults() {
        fadeOutEnabled = true;
        fadeOutDelay = 500;
        fadeOutDurationMs = 100;
        fadeInEnabled = true;
        fadeInDuration = 100;
        switchEffectEnabled = true;
        switchFlashDuration = 500;
        switchFlashColor = 0xFFFFFFFF;
        switchScaleEnabled = true;
        appearScaleEnabled = true;
        itemScaleMinSwitch = 0.5;
        itemScaleMinAppear = 0.5;
        itemScaleMin = 0.5;
        ANIMATOR.setAlphaSpeed(18.0f);
        ANIMATOR.setSmoothMovement(true, 1.0f);
        ANIMATOR.setSmoothScaling(true, 1.0f);
    }

    /**
     * @return 当前切换闪光特效的颜色（ARGB），由 {@link #applyStyle(StyleDefinition, ItemStack)} 中的
     *         {@link DynamicColorResolver#resolve(String, ItemStack)} 解析
     */
    public static int getSwitchFlashColor() {
        return switchFlashColor;
    }

    // ══════════════════════════════════════════════════════════
    // 跨样式 Crossfade（样式名变化时触发）
    // ══════════════════════════════════════════════════════════

    /**
     * 触发跨样式 crossfade 动画。
     * 当 {@link ConfigManager#getStyleForStack} 返回的样式名与上次不同时调用。
     * <p>
     * 如果 crossfade 已在进行中（被中断），从当前进度重新开始。
     *
     * @param styleName       新样式名
     * @param oldBorderColor  旧样式的边框颜色（当前帧渲染值）
     * @param oldBgColor      旧样式的背景颜色（当前帧渲染值）
     * @param newBorderColor  新样式的目标边框颜色
     * @param newBgColor      新样式的目标背景颜色
     */
    public static void startCrossfade(String styleName, int oldBorderColor, int oldBgColor,
                                       int newBorderColor, int newBgColor) {
        lastStyleName = styleName;
        crossfadeOldBorderColor = oldBorderColor;
        crossfadeOldBgColor = oldBgColor;
        crossfadeNewBorderColor = newBorderColor;
        crossfadeNewBgColor = newBgColor;
        crossfadeStartMs = Util.getMillis();
    }

    /**
     * @return crossfade 动画进度 0.0~1.0，无活动 crossfade 时返回 -1.0f
     */
    public static float getCrossfadeProgress() {
        if (crossfadeStartMs < 0L) {
            return -1.0f;
        }
        long elapsed = Util.getMillis() - crossfadeStartMs;
        if (elapsed >= CROSSFADE_DURATION_MS) {
            crossfadeStartMs = -1L;
            return -1.0f;
        }
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) CROSSFADE_DURATION_MS));
        return easeOutCubic(t);
    }

    /** @return crossfade 期间的旧边框颜色 */
    public static int getCrossfadeOldBorderColor() { return crossfadeOldBorderColor; }

    /** @return crossfade 期间的旧背景颜色 */
    public static int getCrossfadeOldBgColor() { return crossfadeOldBgColor; }

    /** @return crossfade 期间的新边框颜色 */
    public static int getCrossfadeNewBorderColor() { return crossfadeNewBorderColor; }

    /** @return crossfade 期间的新背景颜色 */
    public static int getCrossfadeNewBgColor() { return crossfadeNewBgColor; }

    /** @return 上一次记录的样式名，用于检测样式变化 */
    public static String getLastStyleName() { return lastStyleName; }

    private static void processEvents() {
        TooltipLifecycleEventType eventType;
        while ((eventType = TooltipLifecycleEventBus.poll()) != null) {
            if (eventType == TooltipLifecycleEventType.SHOW_FROM_HIDDEN) {
                ANIMATOR.resetAlphaToZero();
                // itemModel.appearing：启用时触发缩放动画，否则跳过
                if (appearScaleEnabled) {
                    ANIMATOR.restartTransition();
                    itemScaleMin = itemScaleMinAppear;
                }
                fadeInStartTimeMs = Util.getMillis();
                TooltipLockManager.resetOffsets();
            } else if (eventType == TooltipLifecycleEventType.SWITCH_ITEM) {
                ANIMATOR.resetAlpha();
                // itemModel.switching：启用时触发缩放动画，否则跳过
                if (switchScaleEnabled) {
                    ANIMATOR.restartTransition();
                    itemScaleMin = itemScaleMinSwitch;
                }
                // switchEffect.enabled：启用时播放切换闪光动画
                if (switchEffectEnabled) {
                    switchFlashStartTimeMs = Util.getMillis();
                }
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
     * 采样当前动画插值颜色（无副作用，仅用于中断时获取当前值）。
     * 使用 HSV 空间插值以获得更自然的颜色过渡效果。
     */
    private static int sampleCurrentColorAnim() {
        if (colorAnimStartMs < 0L) return colorAnimToArgb;
        long elapsed = Util.getMillis() - colorAnimStartMs;
        if (elapsed >= COLOR_ANIM_DURATION_MS) {
            return colorAnimToArgb;
        }
        float t = Math.max(0.0f, Math.min(1.0f, elapsed / (float) COLOR_ANIM_DURATION_MS));
        float eased = easeOutCubic(t);
        return ColorUtils.interpolateColorHSV(colorAnimFromArgb, colorAnimToArgb, eased);
    }

    /**
     * 应用颜色动画，返回 HSV 插值后的颜色。
     * 动画结束后自动清理状态并返回目标色。
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
        return ColorUtils.interpolateColorHSV(colorAnimFromArgb, colorAnimToArgb, eased);
    }

    private static float easeOutCubic(float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - (float) Math.pow(1.0f - clamped, 3.0f);
    }
}
