package org.yanbwe.colortooltips.util;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TooltipFadeManager {
    public enum State {
        IDLE,
        WAITING,
        FADING_OUT,
        FADING_IN,
        HIDDEN
    }

    private static State state = State.HIDDEN;
    private static long lastItemTime = 0;
    private static long lastUpdateTime = 0;
    private static float currentAlpha = 0.0f;

    private static ItemStack cachedStack = ItemStack.EMPTY;
    private static List<ClientTooltipComponent> cachedComponents = null;
    private static Font cachedFont = null;
    private static ClientTooltipPositioner cachedPositioner = null;
    private static int lastMouseX = 0;
    private static int lastMouseY = 0;

    private static boolean renderedThisFrame = false;
    private static boolean isRenderPhase = false;
    private static boolean fadingInFromHidden = false;

    public static boolean isFadingInFromHidden() {
        return fadingInFromHidden;
    }

    public static void onTooltipRendered(ItemStack stack, List<ClientTooltipComponent> components, Font font, ClientTooltipPositioner positioner, int mouseX, int mouseY) {
        if (isRenderPhase) return;
        
        renderedThisFrame = true;
        lastItemTime = Util.getMillis();

        cachedStack = stack;
        cachedComponents = new ArrayList<>(components);
        cachedFont = font;
        cachedPositioner = positioner;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (state == State.HIDDEN || state == State.FADING_OUT || state == State.WAITING) {
            if (state == State.HIDDEN || state == State.FADING_OUT) {
                fadingInFromHidden = true;
            }
            state = State.FADING_IN;
        } else {
            fadingInFromHidden = false;
        }
    }

    public static void update() {
        long currentTime = Util.getMillis();
        if (lastUpdateTime == 0) lastUpdateTime = currentTime;
        long delta = currentTime - lastUpdateTime;
        lastUpdateTime = currentTime;

        if (renderedThisFrame) {
            if (state == State.FADING_IN) {
                // 每0.1秒100% -> 速率是10.0/s
                currentAlpha += delta / 100.0f;
                if (currentAlpha >= 1.0f) {
                    currentAlpha = 1.0f;
                    state = State.IDLE;
                }
            } else {
                currentAlpha = 1.0f;
                state = State.IDLE;
            }
        } else {
            if (state == State.IDLE || state == State.FADING_IN) {
                if (currentTime - lastItemTime >= 100) {
                    state = State.FADING_OUT;
                } else {
                    state = State.WAITING;
                }
            } else if (state == State.WAITING) {
                if (currentTime - lastItemTime >= 100) {
                    state = State.FADING_OUT;
                }
            }

            if (state == State.FADING_OUT) {
                currentAlpha -= delta / 100.0f;
                if (currentAlpha <= 0.0f) {
                    currentAlpha = 0.0f;
                    state = State.HIDDEN;
                    cachedStack = ItemStack.EMPTY;
                    cachedComponents = null;
                    cachedFont = null;
                    cachedPositioner = null;
                }
            }
        }

        renderedThisFrame = false;
    }

    public static float getFadeAlpha() {
        return Math.max(0.0f, Math.min(1.0f, currentAlpha));
    }

    public static boolean shouldRenderCached() {
        return !renderedThisFrame && state != State.HIDDEN && cachedComponents != null && !cachedStack.isEmpty();
    }

    public static void renderCached(GuiGraphics graphics, int currentMouseX, int currentMouseY) {
        if (shouldRenderCached()) {
            isRenderPhase = true;
            org.yanbwe.colortooltips.client.TooltipEventHandler.renderCustomTooltip(
                    graphics, cachedFont, cachedComponents, currentMouseX, currentMouseY, cachedPositioner, cachedStack
            );
            isRenderPhase = false;
        }
    }

    public static void reset() {
        state = State.HIDDEN;
        currentAlpha = 0.0f;
        cachedStack = ItemStack.EMPTY;
        cachedComponents = null;
        cachedFont = null;
        cachedPositioner = null;
        renderedThisFrame = false;
        isRenderPhase = false;
    }
}