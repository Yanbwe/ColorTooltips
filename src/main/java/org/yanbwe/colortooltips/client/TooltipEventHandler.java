package org.yanbwe.colortooltips.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.yanbwe.colortooltips.Config;
import org.yanbwe.colortooltips.animation.TooltipAnchor;
import org.yanbwe.colortooltips.animation.TooltipAnimationSystem;
import org.yanbwe.colortooltips.animation.TooltipState;
import org.yanbwe.colortooltips.animation.TooltipTarget;
import org.yanbwe.colortooltips.tooltip.ColorHeaderComponent;
import org.yanbwe.colortooltips.util.ColorUtils;
import org.yanbwe.colortooltips.util.TooltipRenderer;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;

import java.util.ArrayList;
import java.util.List;

public class TooltipEventHandler {
    private static ItemStack cachedStack = ItemStack.EMPTY;
    private static List<ClientTooltipComponent> cachedComponents = null;
    private static Font cachedFont = null;
    private static ClientTooltipPositioner cachedPositioner = null;
    private static boolean renderedThisFrame = false;
    private static boolean isReplayPhase = false;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        if (!Config.ENABLED.get()) {
            return;
        }

        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        if (isVirtualItem(itemStack)) {
            return;
        }

        var tooltipElements = event.getTooltipElements();

        boolean hasTextComponent = !tooltipElements.isEmpty() && tooltipElements.get(0).left().isPresent();

        if (hasTextComponent) {
            tooltipElements.remove(0);
        }

        tooltipElements.add(0, Either.right(net.minecraft.world.inventory.tooltip.TooltipComponent.class.cast(new ColorHeaderComponent(itemStack))));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderTooltipColor(RenderTooltipEvent.Color event) {
        if (!Config.ENABLED.get()) {
            return;
        }

        var itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        int rarity = RarityRegistry.getNormalizedRarity(itemStack);
        int borderColor = RarityColorUtil.getRarityArgbColor(rarity);

        int bgColor = ColorUtils.withAlpha(
            ColorUtils.darkenColor(borderColor, 1.0f - Config.BG_DARKEN.get().floatValue()),
            Config.BG_ALPHA.get().floatValue()
        );

        event.setBorderStart(borderColor);
        event.setBorderEnd(borderColor);
        event.setBackgroundStart(bgColor);
        event.setBackgroundEnd(bgColor);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenClose(ScreenEvent.Closing event) {
        cachedStack = ItemStack.EMPTY;
        cachedComponents = null;
        cachedFont = null;
        cachedPositioner = null;
        renderedThisFrame = false;
        isReplayPhase = false;
        TooltipAnimationSystem.reset();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!Config.ENABLED.get()) {
            return;
        }
        if (!renderedThisFrame) {
            TooltipAnimationSystem.onFrameWithoutLiveItem();
        }
        if (!renderedThisFrame
                && TooltipAnimationSystem.shouldRenderCachedTooltip()
                && cachedComponents != null
                && !cachedStack.isEmpty()
                && cachedFont != null
                && cachedPositioner != null) {
            isReplayPhase = true;
            renderCustomTooltip(
                    event.getGuiGraphics(),
                    cachedFont,
                    cachedComponents,
                    event.getMouseX(),
                    event.getMouseY(),
                    cachedPositioner,
                    cachedStack,
                    false
            );
            isReplayPhase = false;
        }
        renderedThisFrame = false;
    }

    public static boolean renderCustomTooltip(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, ItemStack stack) {
        return renderCustomTooltip(graphics, font, components, x, y, positioner, stack, true);
    }

    public static boolean renderCustomTooltip(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, ItemStack stack, boolean liveRender) {
        if (!Config.ENABLED.get()) {
            return false;
        }

        if (components.isEmpty()) {
            return false;
        }

        boolean isVirtual = isVirtualItem(stack);

        if (isVirtual) {
            return false;
        }

        if (liveRender && !isReplayPhase) {
            renderedThisFrame = true;
            cachedStack = stack.copy();
            cachedComponents = new ArrayList<>(components);
            cachedFont = font;
            cachedPositioner = positioner;
        }

        TooltipRenderer.updateScrollOffset();

        int rarity = RarityRegistry.getNormalizedRarity(stack);
        int targetRawBorderColor = RarityColorUtil.getRarityArgbColor(rarity);

        int contentWidth = 0;
        int contentHeight = 0;
        for (ClientTooltipComponent component : components) {
            contentWidth = Math.max(contentWidth, component.getWidth(font));
            contentHeight += component.getHeight();
        }

        boolean hasExtraContent = components.size() > 1;
        int titleBarExtraHeight = hasExtraContent ? 1 : 0;
        int heightAdjust = hasExtraContent ? 0 : -2;

        int borderSize = 1;
        int innerPadding = 2;
        int targetWidth = contentWidth + borderSize * 2 + innerPadding * 2;
        int targetHeight = contentHeight + borderSize * 2 + innerPadding * 2 + heightAdjust;

        var targetPos = positioner.positionTooltip(graphics.guiWidth(), graphics.guiHeight(), x, y, targetWidth, targetHeight);
        boolean isLeft = targetPos.x() + targetWidth / 2 < x;
        TooltipAnchor anchor = isLeft ? TooltipAnchor.RIGHT_TOP : TooltipAnchor.LEFT_TOP;
        float anchorX = isLeft ? targetPos.x() + targetWidth : targetPos.x();

        float anchorY;
        if (liveRender && !isReplayPhase) {
            if (TooltipAnimationSystem.isLocked()) {
                anchorY = TooltipAnimationSystem.getLockedAnchorY() + TooltipAnimationSystem.getLockOffsetY();
            } else {
                anchorY = targetPos.y();
            }
            TooltipAnimationSystem.onLiveItemObserved(stack, anchorY);
        } else if (TooltipAnimationSystem.isLocked()) {
            anchorY = TooltipAnimationSystem.getLockedAnchorY() + TooltipAnimationSystem.getLockOffsetY();
        } else {
            anchorY = targetPos.y();
        }

        TooltipTarget target = new TooltipTarget(
                targetWidth,
                targetHeight,
                anchorX,
                anchorY,
                anchor,
                targetRawBorderColor,
                TooltipAnimationSystem.computeTargetAlpha()
        );
        TooltipState state = TooltipAnimationSystem.update(stack, target);

        int width = state.getWidthInt();
        int height = state.getHeightInt();
        int renderX = state.getRenderX();
        int renderY = state.getRenderY();
        int borderColor = state.colorArgb;
        int bgColor = ColorUtils.withAlpha(
                ColorUtils.darkenColor(borderColor, 1.0f - Config.BG_DARKEN.get().floatValue()),
                Config.BG_ALPHA.get().floatValue()
        );
        int darkenedBorderColor = ColorUtils.darkenColor(borderColor, 0.5f);
        float fadeAlpha = state.alpha;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 400.0);

        int innerX = renderX + borderSize + innerPadding;
        int innerY = renderY + borderSize + innerPadding;

        // 开启裁剪以确保超出过渡大小的内容不显示,允许外扩 2 像素以保证 1px 的外边框能正常渲染
        graphics.enableScissor(renderX - 2, renderY - 2, renderX + width + 2, renderY + height + 2);

        // 绘制边框和背景(使用插值后的尺寸和颜色)
        TooltipRenderer.drawOuterBorder(graphics, renderX, renderY, width, height, darkenedBorderColor, fadeAlpha);
        if (Config.BORDER_GRADIENT_ENABLED.get()) {
            graphics.drawManaged(() -> TooltipRenderer.drawGradientScrollingBorder(graphics, renderX, renderY, width, height, borderColor, targetRawBorderColor, fadeAlpha, TooltipAnimationSystem.getTargetWidthInt(), TooltipAnimationSystem.getTargetHeightInt()));
        } else {
            TooltipRenderer.drawRarityBorder(graphics, renderX, renderY, width, height, borderColor, fadeAlpha);
        }
        TooltipRenderer.drawInnerBorder(graphics, renderX, renderY, width, height, darkenedBorderColor, fadeAlpha);

        graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, renderX, renderY, width, height, bgColor, fadeAlpha));
        if (Config.TITLEBAR_GRADIENT_ENABLED.get()) {
            graphics.drawManaged(() -> TooltipRenderer.drawGradientTitleBar(graphics, renderX, renderY, width, height, borderColor, targetRawBorderColor, fadeAlpha, TooltipAnimationSystem.getTargetWidthInt(), TooltipAnimationSystem.getTargetHeightInt()));
        }

        int componentX = innerX;
        int componentY = innerY;

        for (int i = 0; i < components.size(); i++) {
            if (i == 1) {
                componentY += titleBarExtraHeight;
            }
            ClientTooltipComponent component = components.get(i);
            
            // 通过 Shader 透明度控制组件渲染透明度
            if (fadeAlpha < 0.999f) {
                graphics.flush();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, fadeAlpha);
                graphics.setColor(1.0f, 1.0f, 1.0f, fadeAlpha);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }

            // 渲染文字和图片
            component.renderText(font, componentX, componentY, graphics.pose().last().pose(), graphics.bufferSource());
            component.renderImage(font, componentX, componentY, graphics);

            if (fadeAlpha < 0.999f) {
                graphics.flush();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.disableBlend();
            }
            
            componentY += component.getHeight();
        }

        float switchFlashProgress = TooltipAnimationSystem.getSwitchFlashProgress();
        if (switchFlashProgress >= 0.0f) {
            graphics.drawManaged(() -> TooltipRenderer.drawEntryAnimation(graphics, renderX, renderY, width, height, switchFlashProgress, fadeAlpha, isLeft));
        }

        // 在关闭裁剪前提交缓冲区，确保裁剪范围生效
        graphics.flush();
        graphics.disableScissor();

        graphics.pose().popPose();

        return true;
    }

    public static boolean isVirtualItem(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getTooltipImage().isPresent();
    }
}
