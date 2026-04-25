package org.yanbwe.colortooltips.animation;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.yanbwe.colortooltips.Config;

public class TooltipLockManager {
    private static boolean hasValidAnchor = false;
    private static float lockedAnchorY = 0.0f;
    private static float offsetY = 0.0f;

    /**
     * 鼠标滚轮滚动时移动提示框。
     * 按住 Shift + 滚动 → 上下移动提示框（拦截滚轮信号）
     * 没有按 Shift → 不拦截，让滚轮事件正常传递（兼容可滚动的GUI）
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!hasValidAnchor) {
            return;
        }

        if (!Screen.hasShiftDown()) {
            // 没有按 Shift 时不拦截，让事件正常传递
            return;
        }

        double scrollDelta = event.getScrollDelta();
        if (Math.abs(scrollDelta) < 0.1) {
            return;
        }

        float sensitivity = Config.LOCK_SCROLL_SENSITIVITY.get().floatValue();
        offsetY -= (float) (scrollDelta * sensitivity);

        event.setCanceled(true);
    }

    /**
     * 每帧由渲染逻辑调用，更新锚点Y位置。
     * 接收未经过偏移的原始锚点Y坐标。
     * 不重置偏移量。
     */
    public static void onAnchorPositionUpdated(float anchorY) {
        lockedAnchorY = anchorY;
        hasValidAnchor = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenClose(ScreenEvent.Closing event) {
        reset();
    }

    /**
     * 始终返回 false，不再需要在按住 T 键时阻止提示框隐藏
     */
    public static boolean shouldPreventTooltipHide() {
        return false;
    }

    public static boolean hasValidLock() {
        return hasValidAnchor;
    }

    public static float getLockedAnchorY() {
        return lockedAnchorY;
    }

    public static float getOffsetY() {
        return offsetY;
    }

    /**
     * 重置偏移量（保留锚点位置）。
     * 切换物品或提示框淡入时调用。
     */
    public static void resetOffsets() {
        offsetY = 0.0f;
    }

    public static void reset() {
        hasValidAnchor = false;
        lockedAnchorY = 0.0f;
        offsetY = 0.0f;
    }
}
