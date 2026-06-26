package org.yanbwe.colortooltips.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.yanbwe.colortooltips.animation.TooltipAnchor;
import org.yanbwe.colortooltips.animation.TooltipAnimationSystem;
import org.yanbwe.colortooltips.animation.TooltipLockManager;
import org.yanbwe.colortooltips.animation.TooltipState;
import org.yanbwe.colortooltips.animation.TooltipTarget;
import org.yanbwe.colortooltips.config.ConfigManager;
import org.yanbwe.colortooltips.config.ColorFlowAnimator;
import org.yanbwe.colortooltips.config.DynamicColorResolver;
import org.yanbwe.colortooltips.config.StyleDefinition;
import org.yanbwe.colortooltips.tooltip.ColorHeaderComponent;
import org.yanbwe.colortooltips.tooltip.TooltipRenderPolicy;
import org.yanbwe.colortooltips.util.ColorUtils;
import org.yanbwe.colortooltips.util.TooltipRenderer;

import java.util.ArrayList;
import java.util.List;

public class TooltipEventHandler {
    private static ItemStack cachedStack = ItemStack.EMPTY;
    private static List<ClientTooltipComponent> cachedComponents = null;
    private static Font cachedFont = null;
    private static ClientTooltipPositioner cachedPositioner = null;
    private static boolean renderedThisFrame = false;
    private static boolean isReplayPhase = false;
    private static int tooltipsRenderedThisFrame = 0;
    private static int lastBorderColor = 0xFFFFFFFF;
    private static int lastBgColor = 0xFF000000;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        if (!ConfigManager.getInstance().isEnabled()) return;

        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) return;

        if (isVirtualItem(itemStack)) return;

        StyleDefinition style = ConfigManager.getInstance().getStyleForStack(itemStack, false);
        boolean hasHeaderContent = style.getItemModel().isEnabled()
                || style.getTitleBar().isTwoHeight();
        if (!hasHeaderContent) {
            addExtraTooltipLine(event, itemStack, style);
            return;
        }

        var tooltipElements = event.getTooltipElements();

        // NeoForge 1.21.1: 第一个元素 now Either<FormattedText, TooltipComponent>
        boolean hasTextComponent = !tooltipElements.isEmpty() && tooltipElements.get(0).left().isPresent();

        if (hasTextComponent) {
            tooltipElements.remove(0);
        }

        ColorHeaderComponent header = new ColorHeaderComponent(itemStack);
        header.setStyleDefinition(style, false);
        tooltipElements.add(0, Either.right(net.minecraft.world.inventory.tooltip.TooltipComponent.class.cast(header)));

        if (!header.isRenderingExtra()) {
            addExtraTooltipLine(event, itemStack, style);
        }
    }

    private static void addExtraTooltipLine(RenderTooltipEvent.GatherComponents event, ItemStack itemStack, StyleDefinition style) {
        if (!style.getExtraToolTip().isEnabled()) return;
        String content = DynamicColorResolver.resolveDynamicContent(style.getExtraToolTip().getContent(), itemStack);
        if (content == null || content.isEmpty()) return;
        int color = DynamicColorResolver.resolve(style.getExtraToolTip().getContentColor(), itemStack);
        // NeoForge 1.21.1: Either<FormattedText, TooltipComponent>
        FormattedText text = Component.literal(content)
                .withStyle(net.minecraft.network.chat.Style.EMPTY
                        .withColor(net.minecraft.network.chat.TextColor.fromRgb(color & 0xFFFFFF)));
        event.getTooltipElements().add(1, Either.left(text));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderTooltipColor(RenderTooltipEvent.Color event) {
        if (!ConfigManager.getInstance().isEnabled()) return;

        var itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) return;

        StyleDefinition style = ConfigManager.getInstance().getStyleForStack(itemStack, false);

        List<StyleDefinition.FillColorEntry> borderEntries = style.getBorder().getFillColor();
        int borderColor = borderEntries.isEmpty()
            ? DynamicColorResolver.resolve("@rarityCore", itemStack)
            : DynamicColorResolver.resolve(borderEntries.get(0), itemStack);

        List<StyleDefinition.FillColorEntry> bgEntries = style.getBackGround().getFillColor();
        int rawBgColor = bgEntries.isEmpty()
            ? DynamicColorResolver.resolve("@rarityCore", itemStack)
            : DynamicColorResolver.resolve(bgEntries.get(0), itemStack);
        int bgColor = ColorUtils.withAlpha(rawBgColor, (float) style.getBackGround().getOpacity());

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
        tooltipsRenderedThisFrame = 0;
        TooltipAnimationSystem.reset();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!ConfigManager.getInstance().isEnabled()) return;
        if (!renderedThisFrame) {
            TooltipAnimationSystem.onFrameWithoutLiveItem();
        }
        if (!renderedThisFrame
                && TooltipAnimationSystem.shouldRenderCachedTooltip()
                && cachedComponents != null
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
        if (tooltipsRenderedThisFrame > 1) {
            TooltipAnimationSystem.setNeedsAnimatorReset(true);
        }
        renderedThisFrame = false;
        tooltipsRenderedThisFrame = 0;
    }

    public static boolean renderCustomTooltip(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, ItemStack stack) {
        return renderCustomTooltip(graphics, font, components, x, y, positioner, stack, true);
    }

    public static boolean renderCustomTooltip(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, ItemStack stack, boolean liveRender) {
        ConfigManager cm = ConfigManager.getInstance();
        if (!cm.isEnabled()) return false;
        if (components.isEmpty()) return false;

        boolean isVirtual = isVirtualItem(stack);
        if (isVirtual) return false;

        boolean hasValidItem = TooltipRenderPolicy.hasValidItemStack(stack);
        boolean isTextOnly = !hasValidItem;

        if (isTextOnly && !cm.isOnlyTextTooltipsEnabled()) return false;

        StyleDefinition style = cm.getStyleForStack(stack, isTextOnly);
        TooltipAnimationSystem.applyStyle(style, stack);

        String currentStyleName = cm.getStyleNameForStack(stack, isTextOnly);
        String lastStyleName = TooltipAnimationSystem.getLastStyleName();
        boolean styleChanged = currentStyleName != null
                && !currentStyleName.equals(lastStyleName)
                && lastStyleName != null;

        int[] borderFillColors = resolveFillColors(style.getBorder().getFillColor(), stack);
        int[] bgFillColors = resolveFillColors(style.getBackGround().getFillColor(), stack);
        int[] titleBarFillColors = resolveFillColors(style.getTitleBar().getFillColor(), stack);

        int targetRawBorderColor = borderFillColors.length > 0
            ? borderFillColors[0]
            : DynamicColorResolver.resolve("@rarityCore", stack);

        ColorFlowAnimator animator = cm.getColorFlowAnimator();
        animator.update();
        float flowFraction = animator.getInterpolatedFlowFraction(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));

        if (liveRender && !isReplayPhase) {
            renderedThisFrame = true;
            tooltipsRenderedThisFrame++;

            if (tooltipsRenderedThisFrame == 1) {
                if (hasValidItem) {
                    TooltipAnimationSystem.onLiveItemObserved(stack);
                } else {
                    TooltipAnimationSystem.onTextOnlyTooltipObserved();
                }
            }

            cachedStack = (stack != null && !stack.isEmpty()) ? stack.copy() : ItemStack.EMPTY;
            cachedComponents = new ArrayList<>(components);
            cachedFont = font;
            cachedPositioner = positioner;
        }

        int contentWidth = 0;
        int contentHeight = 0;
        for (ClientTooltipComponent component : components) {
            contentWidth = Math.max(contentWidth, component.getWidth(font));
            contentHeight += component.getHeight();
        }

        boolean hasExtraContent = hasValidItem && components.size() > 1;
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
        float rawAnchorY = targetPos.y();

        TooltipLockManager.onAnchorPositionUpdated(rawAnchorY);
        float anchorY = TooltipLockManager.getLockedAnchorY() + TooltipLockManager.getOffsetY();

        ItemStack animationStack = hasValidItem ? stack : ItemStack.EMPTY;

        TooltipTarget target = new TooltipTarget(
                targetWidth, targetHeight,
                anchorX, anchorY, anchor,
                targetRawBorderColor,
                TooltipAnimationSystem.computeTargetAlpha()
        );

        TooltipState state;
        if (tooltipsRenderedThisFrame > 1) {
            state = TooltipAnimationSystem.createInstantState(target);
        } else {
            state = TooltipAnimationSystem.update(animationStack, target);
        }

        int width = state.getWidthInt();
        int height = state.getHeightInt();
        int renderX = state.getRenderX();
        int renderY = state.getRenderY();
        int borderColor = state.colorArgb;

        int bgColor = bgFillColors.length > 0
            ? bgFillColors[0]
            : ColorUtils.withAlpha(DynamicColorResolver.resolve("@rarityCore", stack), 0.8f);
        int darkenedBorderColor = ColorUtils.darkenColor(borderColor, 0.5f);

        float fadeAlpha = state.alpha;

        if (styleChanged && liveRender && !isReplayPhase) {
            TooltipAnimationSystem.startCrossfade(currentStyleName,
                    lastBorderColor, lastBgColor,
                    borderColor, bgColor);
        }

        float crossfadeProgress = TooltipAnimationSystem.getCrossfadeProgress();
        boolean crossfading = crossfadeProgress >= 0.0f;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 400.0);

        int innerX = renderX + borderSize + innerPadding;
        int innerY = renderY + borderSize + innerPadding;

        graphics.enableScissor(renderX - 2, renderY - 2, renderX + width + 2, renderY + height + 2);

        if (crossfading) {
            float oldAlpha = fadeAlpha * (1.0f - crossfadeProgress);
            float newAlpha = fadeAlpha * crossfadeProgress;
            int oldBorder = TooltipAnimationSystem.getCrossfadeOldBorderColor();
            int oldBg = TooltipAnimationSystem.getCrossfadeOldBgColor();
            int newBorder = TooltipAnimationSystem.getCrossfadeNewBorderColor();
            int newBg = TooltipAnimationSystem.getCrossfadeNewBgColor();
            int oldDark = ColorUtils.darkenColor(oldBorder, 0.5f);
            int newDark = ColorUtils.darkenColor(newBorder, 0.5f);

            TooltipRenderer.drawOuterBorder(graphics, renderX, renderY, width, height, oldDark, oldAlpha);
            TooltipRenderer.drawRarityBorder(graphics, renderX, renderY, width, height, oldBorder, oldAlpha);
            TooltipRenderer.drawInnerBorder(graphics, renderX, renderY, width, height, oldDark, oldAlpha);
            graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, renderX, renderY, width, height, oldBg, oldAlpha));

            TooltipRenderer.drawOuterBorder(graphics, renderX, renderY, width, height, newDark, newAlpha);
            TooltipRenderer.drawRarityBorder(graphics, renderX, renderY, width, height, newBorder, newAlpha);
            TooltipRenderer.drawInnerBorder(graphics, renderX, renderY, width, height, newDark, newAlpha);
            graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, renderX, renderY, width, height, newBg, newAlpha));
        } else {
            float borderOpacity = (float) style.getBorder().getOpacity();
            if (style.getBorder().getColorFlowSpeed() > 0 && borderFillColors.length > 0) {
                int targetW = TooltipAnimationSystem.getTargetWidthInt();
                int targetH = TooltipAnimationSystem.getTargetHeightInt();
                graphics.drawManaged(() -> TooltipRenderer.drawDarkenedGradientScrollingBorder(
                    graphics, renderX - 1, renderY - 1, width + 2, height + 2,
                    borderFillColors, fadeAlpha, targetW, targetH, style.getBorder(), flowFraction, 0.5f));
                graphics.drawManaged(() -> TooltipRenderer.drawGradientScrollingBorder(
                    graphics, renderX, renderY, width, height,
                    borderFillColors, fadeAlpha, targetW, targetH, style.getBorder(), flowFraction));
                graphics.drawManaged(() -> TooltipRenderer.drawDarkenedGradientScrollingBorder(
                    graphics, renderX + 1, renderY + 1, width - 2, height - 2,
                    borderFillColors, fadeAlpha, targetW, targetH, style.getBorder(), flowFraction, 0.5f));
            } else {
                int alphaBorder = (int) (((borderColor >> 24) & 0xFF) * borderOpacity);
                int borderColorStatic = (borderColor & 0x00FFFFFF) | (alphaBorder << 24);
                int alphaDark = (int) (((darkenedBorderColor >> 24) & 0xFF) * borderOpacity);
                int darkenedBorderStatic = (darkenedBorderColor & 0x00FFFFFF) | (alphaDark << 24);
                TooltipRenderer.drawOuterBorder(graphics, renderX, renderY, width, height, darkenedBorderStatic, fadeAlpha);
                TooltipRenderer.drawRarityBorder(graphics, renderX, renderY, width, height, borderColorStatic, fadeAlpha);
                TooltipRenderer.drawInnerBorder(graphics, renderX, renderY, width, height, darkenedBorderStatic, fadeAlpha);
            }

            if (bgFillColors.length > 0) {
                graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, renderX, renderY, width, height, bgFillColors, fadeAlpha, style.getBackGround(), flowFraction));
            } else {
                graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, renderX, renderY, width, height, bgColor, fadeAlpha));
            }
        }

        if (hasValidItem && style.getTitleBar().isEnabled() && titleBarFillColors.length > 0) {
            int titleBarH = style.getTitleBar().isTwoHeight() ? 24 : 11;
            float titleAlpha = crossfading ? fadeAlpha * crossfadeProgress : fadeAlpha;
            graphics.drawManaged(() -> TooltipRenderer.drawGradientTitleBar(graphics, renderX, renderY, width, height, titleBarFillColors, titleAlpha, TooltipAnimationSystem.getTargetWidthInt(), TooltipAnimationSystem.getTargetHeightInt(), titleBarH, style.getTitleBar(), flowFraction));
        }

        int componentX = innerX;
        int componentY = innerY;

        for (int i = 0; i < components.size(); i++) {
            if (i == 1) componentY += titleBarExtraHeight;
            ClientTooltipComponent component = components.get(i);

            if (fadeAlpha < 0.999f) {
                graphics.flush();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, fadeAlpha);
                graphics.setColor(1.0f, 1.0f, 1.0f, fadeAlpha);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }

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
            int flashColor = TooltipAnimationSystem.getSwitchFlashColor();
            StyleDefinition.AnimationConfig.SwitchEffectConfig switchEffect = style.getAnimation().getSwitchEffect();
            graphics.drawManaged(() -> TooltipRenderer.drawEntryAnimation(graphics, renderX, renderY, width, height, switchFlashProgress, fadeAlpha, isLeft, switchEffect, flashColor));
        }

        graphics.flush();
        graphics.disableScissor();
        graphics.pose().popPose();

        if (liveRender && !isReplayPhase && !crossfading) {
            lastBorderColor = borderColor;
            lastBgColor = bgColor;
        }

        return true;
    }

    public static boolean isVirtualItem(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getTooltipImage().isPresent();
    }

    private static int[] resolveFillColors(List<StyleDefinition.FillColorEntry> entries, ItemStack itemStack) {
        if (entries == null || entries.isEmpty()) return new int[0];
        int[] colors = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            colors[i] = DynamicColorResolver.resolve(entries.get(i), itemStack);
        }
        return colors;
    }
}
