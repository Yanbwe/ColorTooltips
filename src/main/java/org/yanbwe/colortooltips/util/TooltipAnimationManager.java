package org.yanbwe.colortooltips.util;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

public class TooltipAnimationManager {

    private static final long ANIMATION_DURATION_MS = 500;
    
    private static ItemStack currentStack = ItemStack.EMPTY;
    private static long animationStartTime = 0;
    private static boolean isAnimating = false;

    // 尺寸过渡动画
    private static int lastWidth = 0;
    private static int lastHeight = 0;
    private static int targetWidth = 0;
    private static int targetHeight = 0;

    // 颜色过渡动画
    private static int lastBorderColor = 0;
    private static int targetBorderColor = 0;
    private static int lastBgColor = 0;
    private static int targetBgColor = 0;

    /**
     * 更新动画状态,如果物品改变或初次显示,则重新触发动画
     */
    public static void update(ItemStack newStack, int currentWidth, int currentHeight, int currentBorderColor, int currentBgColor) {
        // 如果没有物品,重置状态
        if (newStack == null || newStack.isEmpty()) {
            currentStack = ItemStack.EMPTY;
            isAnimating = false;
            lastWidth = 0;
            lastHeight = 0;
            targetWidth = 0;
            targetHeight = 0;
            lastBorderColor = 0;
            targetBorderColor = 0;
            lastBgColor = 0;
            targetBgColor = 0;
            return;
        }

        // 首次显示
        if (lastWidth == 0 || lastHeight == 0 || TooltipFadeManager.isFadingInFromHidden()) {
            lastWidth = currentWidth;
            lastHeight = currentHeight;
            targetWidth = currentWidth;
            targetHeight = currentHeight;
            lastBorderColor = currentBorderColor;
            targetBorderColor = currentBorderColor;
            lastBgColor = currentBgColor;
            targetBgColor = currentBgColor;
            
            // 确保淡入时也能触发动画（用于物品大小缓动等）
            currentStack = newStack.copy();
            animationStartTime = Util.getMillis();
            isAnimating = true;
            return;
        }

        // 尺寸发生变化或者物品发生变化或者颜色发生变化
        boolean sizeChanged = currentWidth != targetWidth || currentHeight != targetHeight;
        boolean colorChanged = currentBorderColor != targetBorderColor || currentBgColor != targetBgColor;
        boolean itemChanged = !ItemStack.isSameItemSameTags(currentStack, newStack);

        if (itemChanged || sizeChanged || colorChanged) {
            if (itemChanged) {
                currentStack = newStack.copy();
            }
            
            // 将当前的显示状态作为下一次动画的起点
            if (isAnimating) {
                float progress = getProgress();
                lastWidth = getInterpolatedWidth();
                lastHeight = getInterpolatedHeight();
                lastBorderColor = ColorUtils.interpolateColor(lastBorderColor, targetBorderColor, progress);
                lastBgColor = ColorUtils.interpolateColor(lastBgColor, targetBgColor, progress);
            } else {
                lastWidth = targetWidth;
                lastHeight = targetHeight;
                lastBorderColor = targetBorderColor;
                lastBgColor = targetBgColor;
            }
            
            targetWidth = currentWidth;
            targetHeight = currentHeight;
            targetBorderColor = currentBorderColor;
            targetBgColor = currentBgColor;
            
            animationStartTime = Util.getMillis();
            isAnimating = true;
        }
    }

    /**
     * 获取当前动画进度 [0.0, 1.0]
     */
    public static float getProgress() {
        if (!isAnimating) {
            return 1.0f;
        }

        long currentTime = Util.getMillis();
        long elapsed = currentTime - animationStartTime;

        if (elapsed >= ANIMATION_DURATION_MS) {
            isAnimating = false;
            return 1.0f;
        }

        // 线性进度
        float t = (float) elapsed / ANIMATION_DURATION_MS;

        // 缓出 (ease-out) 曲线: f(t) = 1 - (1 - t)^3
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    /**
     * 获取当前插值后的边框颜色
     */
    public static int getInterpolatedBorderColor() {
        if (!isAnimating) return targetBorderColor;
        return ColorUtils.interpolateColor(lastBorderColor, targetBorderColor, getProgress());
    }

    /**
     * 获取当前插值后的背景颜色
     */
    public static int getInterpolatedBgColor() {
        if (!isAnimating) return targetBgColor;
        return ColorUtils.interpolateColor(lastBgColor, targetBgColor, getProgress());
    }

    /**
     * 是否正在动画中
     */
    public static boolean isAnimating() {
        return isAnimating;
    }

    public static int getTargetWidth() {
        return targetWidth;
    }

    public static int getTargetHeight() {
        return targetHeight;
    }

    public static int getTargetBorderColor() {
        return targetBorderColor;
    }

    public static int getTargetBgColor() {
        return targetBgColor;
    }

    public static int getInterpolatedWidth() {
        if (!isAnimating) return targetWidth;
        float progress = getProgress();
        return (int) (lastWidth + (targetWidth - lastWidth) * progress);
    }

    public static int getInterpolatedHeight() {
        if (!isAnimating) return targetHeight;
        float progress = getProgress();
        return (int) (lastHeight + (targetHeight - lastHeight) * progress);
    }

    public static float getItemScale() {
        if (!isAnimating) return 1.0f;
        float progress = getProgress();
        // 0.5 到 1.0 的缓出
        return 0.5f + 0.5f * progress;
    }
    
    /**
     * 重置动画(例如提示框关闭时调用)
     */
    public static void reset() {
        currentStack = ItemStack.EMPTY;
        isAnimating = false;
        lastWidth = 0;
        lastHeight = 0;
        targetWidth = 0;
        targetHeight = 0;
        lastBorderColor = 0;
        targetBorderColor = 0;
        lastBgColor = 0;
        targetBgColor = 0;
    }
}
