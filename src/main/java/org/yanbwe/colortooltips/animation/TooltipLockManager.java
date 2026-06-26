package org.yanbwe.colortooltips.animation;

import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.yanbwe.colortooltips.config.ConfigManager;

public class TooltipLockManager {
    private static boolean hasValidAnchor = false;
    private static float lockedAnchorY = 0.0f;
    private static float offsetY = 0.0f;

    /**
     * 鼠标滚轮滚动时移动提示框。
     * 按住 Shift + 滚动 → 上下移动提示框（拦截滚轮信号）
     * 没有按 Shift → 不拦截，让滚轮事件正常传递（兼容可滚动的GUI）
     * <p>
     * NeoForge 1.21.1 适配：getScrollDelta() → getScrollDeltaY()
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        ConfigManager config = ConfigManager.getInstance();
        if (!config.isTooltipLockEnabled()) {
            return;
        }

        if (!hasValidAnchor) {
            return;
        }

        if (!Screen.hasShiftDown()) {
            return;
        }

        double scrollDelta = event.getScrollDeltaY();
        if (Math.abs(scrollDelta) < 0.1) {
            return;
        }

        float sensitivity = (float) config.getLockSensitivity();
        offsetY -= (float) (scrollDelta * sensitivity);

        event.setCanceled(true);
    }

    /**
     * 每帧由渲染逻辑调用，更新锚点Y位置。
     */
    public static void onAnchorPositionUpdated(float anchorY) {
        lockedAnchorY = anchorY;
        hasValidAnchor = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenClose(ScreenEvent.Closing event) {
        reset();
    }

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

    public static void resetOffsets() {
        offsetY = 0.0f;
    }

    public static void reset() {
        hasValidAnchor = false;
        lockedAnchorY = 0.0f;
        offsetY = 0.0f;
    }
}
