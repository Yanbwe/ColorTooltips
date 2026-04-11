package org.yanbwe.colortooltips.util;

import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;

public class TooltipAnimationManager {

    private static final long ANIMATION_DURATION_MS = 250;
    
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

    // 位置切换动画
    private static boolean lastIsLeft = false;
    private static boolean hasLastPosition = false;
    private static int lastTargetX = 0;
    private static int lastTargetY = 0;
    private static float positionOffsetX = 0;
    private static float positionOffsetY = 0;
    private static long positionAnimationStartTime = 0;
    private static float startPositionOffsetX = 0;
    private static float startPositionOffsetY = 0;
    private static final long POSITION_ANIMATION_DURATION_MS = 250;

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
            hasLastPosition = false; // 首次显示时重置位置状态
            
            // 确保淡入时也能触发动画(用于物品大小缓动等)
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
                // 不重置 hasLastPosition,允许跨物品触发左右位置缓动
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
     * 更新位置状态,处理左右切换时的缓动动画
     */
    public static void updatePosition(int targetX, int targetY, boolean isLeft) {
        if (!hasLastPosition || currentStack.isEmpty()) {
            lastTargetX = targetX;
            lastTargetY = targetY;
            lastIsLeft = isLeft;
            hasLastPosition = true;
            positionOffsetX = 0;
            positionOffsetY = 0;
            startPositionOffsetX = 0;
            startPositionOffsetY = 0;
            return;
        }

        // 如果发生了左右切换
        if (isLeft != lastIsLeft) {
            // 计算当前位置与目标位置的偏移
            float currentX = lastTargetX + positionOffsetX;
            float currentY = lastTargetY + positionOffsetY;
            
            startPositionOffsetX = currentX - targetX;
            startPositionOffsetY = currentY - targetY;
            
            positionOffsetX = startPositionOffsetX;
            positionOffsetY = startPositionOffsetY;
            
            positionAnimationStartTime = Util.getMillis();
        } else {
            // 更新当前的偏移量(根据时间缓动)
            long currentTime = Util.getMillis();
            long elapsed = currentTime - positionAnimationStartTime;
            
            if (elapsed >= POSITION_ANIMATION_DURATION_MS) {
                positionOffsetX = 0;
                positionOffsetY = 0;
                startPositionOffsetX = 0;
                startPositionOffsetY = 0;
            } else {
                float t = (float) elapsed / POSITION_ANIMATION_DURATION_MS;
                // 缓出 (ease-out) 曲线: f(t) = 1 - (1 - t)^3
                float progress = 1.0f - (float) Math.pow(1.0f - t, 3);
                positionOffsetX = startPositionOffsetX * (1.0f - progress);
                positionOffsetY = startPositionOffsetY * (1.0f - progress);
            }
        }

        lastTargetX = targetX;
        lastTargetY = targetY;
        lastIsLeft = isLeft;
    }

    public static int getInterpolatedX() {
        return (int) (lastTargetX + positionOffsetX);
    }

    public static int getInterpolatedY() {
        return (int) (lastTargetY + positionOffsetY);
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
        hasLastPosition = false;
        lastTargetX = 0;
        lastTargetY = 0;
        lastIsLeft = false;
        positionOffsetX = 0;
        positionOffsetY = 0;
        startPositionOffsetX = 0;
        startPositionOffsetY = 0;
    }
}
