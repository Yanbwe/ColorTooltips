package org.yanbwe.colortooltips.animation;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;
import org.yanbwe.colortooltips.Config;

public class TooltipLockManager {
    private static boolean isLocked = false;
    private static boolean isTKeyPressed = false;
    private static float offsetY = 0.0f;
    private static float lockedAnchorY = 0.0f;
    private static boolean hasLockedAnchorY = false;

    @SubscribeEvent
    public static void onKeyPress(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_T && event.getAction() == GLFW.GLFW_PRESS) {
            isTKeyPressed = true;
            isLocked = true;
        } else if (event.getKey() == GLFW.GLFW_KEY_T && event.getAction() == GLFW.GLFW_RELEASE) {
            isTKeyPressed = false;
            isLocked = false;
            resetOffset();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        if (!isLocked) {
            return;
        }

        if (!hasLockedAnchorY) {
            return;
        }

        double scrollDelta = event.getScrollDelta();
        if (Math.abs(scrollDelta) < 0.1) {
            return;
        }

        float sensitivity = Config.LOCK_SCROLL_SENSITIVITY.get().floatValue();
        offsetY -= scrollDelta * sensitivity;

        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenClose(ScreenEvent.Closing event) {
        reset();
    }

    public static void onAnchorPositionUpdated(float currentAnchorY) {
        if (isTKeyCurrentlyPressed()) {
            if (!hasLockedAnchorY) {
                lockedAnchorY = currentAnchorY;
                hasLockedAnchorY = true;
            }
            return;
        }

        lockedAnchorY = currentAnchorY;
        hasLockedAnchorY = true;
        offsetY = 0.0f;
    }

    public static void onFrameWithoutLiveItem() {
    }

    public static boolean shouldPreventTooltipHide() {
        return isLocked;
    }

    public static float getOffsetY() {
        return offsetY;
    }

    public static float getLockedAnchorY() {
        return lockedAnchorY;
    }

    public static boolean hasValidLock() {
        return isLocked && hasLockedAnchorY;
    }

    public static boolean isTKeyCurrentlyPressed() {
        return isTKeyPressed;
    }

    public static void reset() {
        isLocked = false;
        isTKeyPressed = false;
        offsetY = 0.0f;
        lockedAnchorY = 0.0f;
        hasLockedAnchorY = false;
    }

    private static void resetOffset() {
        offsetY = 0.0f;
        hasLockedAnchorY = false;
    }
}