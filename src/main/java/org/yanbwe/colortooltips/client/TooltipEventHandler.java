package org.yanbwe.colortooltips.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.yanbwe.colortooltips.Config;
import org.yanbwe.colortooltips.tooltip.ColorHeaderComponent;
import org.yanbwe.colortooltips.util.ColorUtils;
import org.yanbwe.colortooltips.util.TooltipRenderer;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;

import java.util.List;

public class TooltipEventHandler {

    private static int currentBorderColor = 0;
    private static int currentBgColor = 0;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        if (!Config.ENABLED.get()) {
            return;
        }

        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
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

        currentBorderColor = borderColor;
        currentBgColor = bgColor;

        event.setBorderStart(borderColor);
        event.setBorderEnd(borderColor);
        event.setBackgroundStart(bgColor);
        event.setBackgroundEnd(bgColor);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenClose(ScreenEvent.Closing event) {
        org.yanbwe.colortooltips.util.TooltipAnimationManager.reset();
        org.yanbwe.colortooltips.util.TooltipFadeManager.reset();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        org.yanbwe.colortooltips.util.TooltipFadeManager.update();
        if (org.yanbwe.colortooltips.util.TooltipFadeManager.shouldRenderCached()) {
            org.yanbwe.colortooltips.util.TooltipFadeManager.renderCached(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
    }

    public static boolean renderCustomTooltip(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, ItemStack stack) {
        if (!Config.ENABLED.get()) {
            return false;
        }

        if (components.isEmpty()) {
            return false;
        }

        // 通知淡出管理器，记录当前帧正常渲染了
        org.yanbwe.colortooltips.util.TooltipFadeManager.onTooltipRendered(stack, components, font, positioner, x, y);

        TooltipRenderer.updateScrollOffset();

        int rarity = RarityRegistry.getNormalizedRarity(stack);
        int targetRawBorderColor = RarityColorUtil.getRarityArgbColor(rarity);
        int targetRawBgColor = ColorUtils.withAlpha(
            ColorUtils.darkenColor(targetRawBorderColor, 1.0f - Config.BG_DARKEN.get().floatValue()),
            Config.BG_ALPHA.get().floatValue()
        );

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

        // 更新动画管理器（处理尺寸和颜色过渡）
        org.yanbwe.colortooltips.util.TooltipAnimationManager.update(stack, targetWidth, targetHeight, targetRawBorderColor, targetRawBgColor);
        
        // 获取插值后的状态
        int width = org.yanbwe.colortooltips.util.TooltipAnimationManager.getInterpolatedWidth();
        int height = org.yanbwe.colortooltips.util.TooltipAnimationManager.getInterpolatedHeight();
        final int borderColor = org.yanbwe.colortooltips.util.TooltipAnimationManager.getInterpolatedBorderColor();
        final int bgColor = org.yanbwe.colortooltips.util.TooltipAnimationManager.getInterpolatedBgColor();
        final int darkenedBorderColor = ColorUtils.darkenColor(borderColor, 0.5f);
        
        float fadeAlpha = org.yanbwe.colortooltips.util.TooltipFadeManager.getFadeAlpha();

        // 缓存当前正在渲染的颜色，供淡出使用
        currentBorderColor = borderColor;
        currentBgColor = bgColor;

        // 使用目标尺寸获取最终位置，确保动画过程中位置稳定，不跳动
        var targetPos = positioner.positionTooltip(graphics.guiWidth(), graphics.guiHeight(), x, y, targetWidth, targetHeight);
        
        final int renderX = targetPos.x();
        final int renderY = targetPos.y();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 400.0);

        int innerX = renderX + borderSize + innerPadding;
        int innerY = renderY + borderSize + innerPadding;

        // 开启裁剪以确保超出过渡大小的内容不显示，允许外扩 2 像素以保证 1px 的外边框能正常渲染
        graphics.enableScissor(renderX - 2, renderY - 2, renderX + width + 2, renderY + height + 2);

        // 绘制边框和背景（使用插值后的尺寸和颜色）
        TooltipRenderer.drawOuterBorder(graphics, renderX, renderY, width, height, darkenedBorderColor, fadeAlpha);
        if (Config.BORDER_GRADIENT_ENABLED.get()) {
            graphics.drawManaged(() -> TooltipRenderer.drawGradientScrollingBorder(graphics, renderX, renderY, width, height, borderColor, fadeAlpha));
        } else {
            TooltipRenderer.drawRarityBorder(graphics, renderX, renderY, width, height, borderColor, fadeAlpha);
        }
        TooltipRenderer.drawInnerBorder(graphics, renderX, renderY, width, height, darkenedBorderColor, fadeAlpha);

        graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, renderX, renderY, width, height, bgColor, fadeAlpha));
        if (Config.TITLEBAR_GRADIENT_ENABLED.get()) {
            graphics.drawManaged(() -> TooltipRenderer.drawGradientTitleBar(graphics, renderX, renderY, width, height, borderColor, fadeAlpha));
        }

        int componentX = innerX;
        int componentY = innerY;

        for (int i = 0; i < components.size(); i++) {
            if (i == 1) {
                componentY += titleBarExtraHeight;
            }
            ClientTooltipComponent component = components.get(i);
            
            // 如果处于淡出/淡入过程,通过设置 Shader Color 和积极刷新来应用透明度
            if (fadeAlpha < 1.0f) {
                graphics.flush();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, fadeAlpha);
                graphics.setColor(1.0f, 1.0f, 1.0f, fadeAlpha);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }

            // 渲染文字和图片
            component.renderText(font, componentX, componentY, graphics.pose().last().pose(), graphics.bufferSource());
            component.renderImage(font, componentX, componentY, graphics);

            if (fadeAlpha < 1.0f) {
                graphics.flush();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.disableBlend();
            }
            
            componentY += component.getHeight();
        }

        if (org.yanbwe.colortooltips.util.TooltipAnimationManager.isAnimating()) {
            float progress = org.yanbwe.colortooltips.util.TooltipAnimationManager.getProgress();
            final int finalRenderX = renderX;
            final int finalRenderY = renderY;
            // 对于入场动画，如果正在淡出也需要应用透明度
            graphics.drawManaged(() -> TooltipRenderer.drawEntryAnimation(graphics, finalRenderX, finalRenderY, width, height, progress, fadeAlpha));
        }

        // 核心修复：必须在 disableScissor 之前调用 flush，否则所有的缓冲绘制（包括文字）都不会被裁剪
        graphics.flush();
        graphics.disableScissor();

        graphics.pose().popPose();

        return true;
    }
}