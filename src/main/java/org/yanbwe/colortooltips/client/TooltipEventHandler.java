package org.yanbwe.colortooltips.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.yanbwe.colortooltips.animation.TooltipAnchor;
import org.yanbwe.colortooltips.animation.TooltipAnimationSystem;
import org.yanbwe.colortooltips.animation.TooltipLockManager;
import org.yanbwe.colortooltips.animation.TooltipState;
import org.yanbwe.colortooltips.animation.TooltipTarget;
import org.yanbwe.colortooltips.compat.ApotheosisCompat;
import org.yanbwe.colortooltips.compat.RarityCoreProxy;
import org.yanbwe.colortooltips.config.ColorFlowAnimator;
import org.yanbwe.colortooltips.config.ConfigManager;
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
    private static int lastBorderColor = 0xFFFFFFFF;
    private static int lastBgColor = 0xFF000000;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        if (!ConfigManager.getInstance().isEnabled()) {
            return;
        }

        ItemStack itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        if (isVirtualItem(itemStack)) {
            return;
        }

        // 神化模组兼容：重铸结果槽 / 回收台不干涉
        if (ApotheosisCompat.shouldSkipTooltip(Minecraft.getInstance().screen, 
            Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> s ? s.getSlotUnderMouse() : null)) {
            return;
        }

        // 从当前样式配置判断标题栏启用状态，未启用或无 RarityCore 时不替换物品名称，保留原版
        StyleDefinition style = ConfigManager.getInstance().getStyleForStack(itemStack, false);
        if (!style.getTitleBar().isEnabled() || !RarityCoreProxy.isLoaded()) {
            return;
        }

        var tooltipElements = event.getTooltipElements();

        boolean hasTextComponent = !tooltipElements.isEmpty() && tooltipElements.get(0).left().isPresent();

        if (hasTextComponent) {
            tooltipElements.remove(0);
        }

        ColorHeaderComponent header = new ColorHeaderComponent(itemStack);
        header.setStyleDefinition(style, false);
        tooltipElements.add(0, Either.right(net.minecraft.world.inventory.tooltip.TooltipComponent.class.cast(header)));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderTooltipColor(RenderTooltipEvent.Color event) {
        if (!ConfigManager.getInstance().isEnabled()) {
            return;
        }

        // 神化模组兼容：重铸结果槽 / 回收台不干涉颜色
        if (ApotheosisCompat.shouldSkipTooltip(Minecraft.getInstance().screen,
            Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> s ? s.getSlotUnderMouse() : null)) {
            return;
        }

        var itemStack = event.getItemStack();
        if (itemStack == null || itemStack.isEmpty()) {
            return;
        }

        // 从样式系统获取边框和背景颜色（通过 DynamicColorResolver 解析，应用 colorModifier）
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
        TooltipAnimationSystem.reset();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!ConfigManager.getInstance().isEnabled()) {
            return;
        }
        if (!renderedThisFrame) {
            TooltipAnimationSystem.onFrameWithoutLiveItem();
        }
        // 支持纯文本tooltip的重播
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
        renderedThisFrame = false;
    }

    public static boolean renderCustomTooltip(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, ItemStack stack) {
        return renderCustomTooltip(graphics, font, components, x, y, positioner, stack, true);
    }

    public static boolean renderCustomTooltip(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, ItemStack stack, boolean liveRender) {
        ConfigManager cm = ConfigManager.getInstance();
        if (!cm.isEnabled()) {
            return false;
        }

        if (components.isEmpty()) {
            return false;
        }

        boolean isVirtual = isVirtualItem(stack);

        if (isVirtual) {
            return false;
        }

        // 智能判断：检查是否有有效物品
        boolean hasValidItem = TooltipRenderPolicy.hasValidItemStack(stack);
        boolean isTextOnly = !hasValidItem;

        // onlyTextTooltips.enabled=false 时不渲染纯文本提示框（让原版系统处理）
        if (isTextOnly && !cm.isOnlyTextTooltipsEnabled()) {
            return false;
        }

        // 关键：根据物品和提示框模式选择当前样式
        StyleDefinition style = cm.getStyleForStack(stack, isTextOnly);

        // 将样式驱动的动画参数同步到动画系统（fadeOutDelay、switchFlashDuration 等）
        TooltipAnimationSystem.applyStyle(style, stack);

        // 跨样式 Crossfade 检测：样式名变化时触发颜色混合过渡
        String currentStyleName = cm.getStyleNameForStack(stack, isTextOnly);
        String lastStyleName = TooltipAnimationSystem.getLastStyleName();
        boolean styleChanged = currentStyleName != null
                && !currentStyleName.equals(lastStyleName)
                && lastStyleName != null; // 首次不计入 crossfade

        // 预解析所有 fillColor 条目（应用 colorModifier + 动态 token 解析）
        int[] borderFillColors = resolveFillColors(style.getBorder().getFillColor(), stack);
        int[] bgFillColors = resolveFillColors(style.getBackGround().getFillColor(), stack);
        int[] titleBarFillColors = resolveFillColors(style.getTitleBar().getFillColor(), stack);

        // 从解析结果中取第一个颜色作为 targetRawBorderColor（供动画系统和单色边框使用）
        int targetRawBorderColor = borderFillColors.length > 0
            ? borderFillColors[0]
            : DynamicColorResolver.resolve("@rarityCore", stack);

        // 更新颜色流动动画引擎（基准 2 秒/周期，帧间插值消除卡顿）
        ColorFlowAnimator animator = cm.getColorFlowAnimator();
        animator.update();
        float flowFraction = animator.getInterpolatedFlowFraction(Minecraft.getInstance().getFrameTime());

        if (liveRender && !isReplayPhase) {
            renderedThisFrame = true;
            // 处理空物品堆
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

        // 标题栏只在有物品时显示（components > 1 表示有ColorHeaderComponent）
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

        // 更新锚点并应用滚轮偏移（按住 Shift + 滚动可上下移动提示框）
        TooltipLockManager.onAnchorPositionUpdated(rawAnchorY);
        float anchorY = TooltipLockManager.getLockedAnchorY() + TooltipLockManager.getOffsetY();

        // 纯文本tooltip使用空物品堆
        ItemStack animationStack = hasValidItem ? stack : ItemStack.EMPTY;
        if (liveRender && !isReplayPhase) {
            if (hasValidItem) {
                TooltipAnimationSystem.onLiveItemObserved(stack);
            } else {
                TooltipAnimationSystem.onTextOnlyTooltipObserved();
            }
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
        TooltipState state = TooltipAnimationSystem.update(animationStack, target);

        int width = state.getWidthInt();
        int height = state.getHeightInt();
        int renderX = state.getRenderX();
        int renderY = state.getRenderY();
        int borderColor = state.colorArgb;

        // 背景颜色：优先使用解析后的 bgFillColors 第一个颜色作为单色回退
        int bgColor = bgFillColors.length > 0
            ? bgFillColors[0]
            : ColorUtils.withAlpha(DynamicColorResolver.resolve("@rarityCore", stack), 0.8f);
        int darkenedBorderColor = ColorUtils.darkenColor(borderColor, 0.5f);

        float fadeAlpha = state.alpha;

        // 跨样式 Crossfade 触发：记录旧颜色后启动过渡
        if (styleChanged && liveRender && !isReplayPhase) {
            TooltipAnimationSystem.startCrossfade(currentStyleName,
                    lastBorderColor, lastBgColor,
                    borderColor, bgColor);
        }

        // 跨样式 Crossfade 进度（0~1，无活动时为 -1）
        float crossfadeProgress = TooltipAnimationSystem.getCrossfadeProgress();
        boolean crossfading = crossfadeProgress >= 0.0f;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 400.0);

        int innerX = renderX + borderSize + innerPadding;
        int innerY = renderY + borderSize + innerPadding;

        // 开启裁剪以确保超出过渡大小的内容不显示,允许外扩 2 像素以保证 1px 的外边框能正常渲染
        graphics.enableScissor(renderX - 2, renderY - 2, renderX + width + 2, renderY + height + 2);

        if (crossfading) {
            // ── 跨样式 Crossfade 渲染：旧层淡出 + 新层淡入 ──
            float oldAlpha = fadeAlpha * (1.0f - crossfadeProgress);
            float newAlpha = fadeAlpha * crossfadeProgress;
            int oldBorder = TooltipAnimationSystem.getCrossfadeOldBorderColor();
            int oldBg = TooltipAnimationSystem.getCrossfadeOldBgColor();
            int newBorder = TooltipAnimationSystem.getCrossfadeNewBorderColor();
            int newBg = TooltipAnimationSystem.getCrossfadeNewBgColor();
            int oldDark = ColorUtils.darkenColor(oldBorder, 0.5f);
            int newDark = ColorUtils.darkenColor(newBorder, 0.5f);

            // 旧层（淡出）
            TooltipRenderer.drawOuterBorder(graphics, renderX, renderY, width, height, oldDark, oldAlpha);
            TooltipRenderer.drawRarityBorder(graphics, renderX, renderY, width, height, oldBorder, oldAlpha);
            TooltipRenderer.drawInnerBorder(graphics, renderX, renderY, width, height, oldDark, oldAlpha);
            graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, renderX, renderY, width, height, oldBg, oldAlpha));

            // 新层（淡入）
            TooltipRenderer.drawOuterBorder(graphics, renderX, renderY, width, height, newDark, newAlpha);
            TooltipRenderer.drawRarityBorder(graphics, renderX, renderY, width, height, newBorder, newAlpha);
            TooltipRenderer.drawInnerBorder(graphics, renderX, renderY, width, height, newDark, newAlpha);
            graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, renderX, renderY, width, height, newBg, newAlpha));
        } else {
            // ── 正常渲染（无 crossfade）──
            // 绘制边框和背景(使用插值后的尺寸和颜色)
            // 边框：从 border.colorFlowSpeed > 0 判断是否启用流动
            float borderOpacity = (float) style.getBorder().getOpacity();
            if (style.getBorder().getColorFlowSpeed() > 0 && borderFillColors.length > 0) {
                // 流动边框路径：drawGradientScrollingBorder 内部已应用 border.opacity
                int alphaDark = (int) (((darkenedBorderColor >> 24) & 0xFF) * borderOpacity);
                int darkenedBorderStatic = (darkenedBorderColor & 0x00FFFFFF) | (alphaDark << 24);
                TooltipRenderer.drawOuterBorder(graphics, renderX, renderY, width, height, darkenedBorderStatic, fadeAlpha);
                graphics.drawManaged(() -> TooltipRenderer.drawGradientScrollingBorder(graphics, renderX, renderY, width, height, borderFillColors, fadeAlpha, TooltipAnimationSystem.getTargetWidthInt(), TooltipAnimationSystem.getTargetHeightInt(), style.getBorder(), flowFraction));
                TooltipRenderer.drawInnerBorder(graphics, renderX, renderY, width, height, darkenedBorderStatic, fadeAlpha);
            } else {
                // 静态边框路径：手动应用 border.opacity 到 alpha 通道
                int alphaBorder = (int) (((borderColor >> 24) & 0xFF) * borderOpacity);
                int borderColorStatic = (borderColor & 0x00FFFFFF) | (alphaBorder << 24);
                int alphaDark = (int) (((darkenedBorderColor >> 24) & 0xFF) * borderOpacity);
                int darkenedBorderStatic = (darkenedBorderColor & 0x00FFFFFF) | (alphaDark << 24);
                TooltipRenderer.drawOuterBorder(graphics, renderX, renderY, width, height, darkenedBorderStatic, fadeAlpha);
                TooltipRenderer.drawRarityBorder(graphics, renderX, renderY, width, height, borderColorStatic, fadeAlpha);
                TooltipRenderer.drawInnerBorder(graphics, renderX, renderY, width, height, darkenedBorderStatic, fadeAlpha);
            }

            // 背景：优先使用多色离散段渲染，回退到单色填充
            if (bgFillColors.length > 0) {
                graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, renderX, renderY, width, height, bgFillColors, fadeAlpha, style.getBackGround(), flowFraction));
            } else {
                graphics.drawManaged(() -> TooltipRenderer.drawBackground(graphics, renderX, renderY, width, height, bgColor, fadeAlpha));
            }
        }

        // 标题栏：从 titleBar.enabled 判断，纯文本提示框(无有效物品)时强制禁用（crossfade 期间也渲染新标题栏）
        if (hasValidItem && style.getTitleBar().isEnabled() && titleBarFillColors.length > 0) {
            int titleBarH = style.getTitleBar().isTwoHeight() ? 24 : 12;
            float titleAlpha = crossfading ? fadeAlpha * crossfadeProgress : fadeAlpha;
            graphics.drawManaged(() -> TooltipRenderer.drawGradientTitleBar(graphics, renderX, renderY, width, height, titleBarFillColors, titleAlpha, TooltipAnimationSystem.getTargetWidthInt(), TooltipAnimationSystem.getTargetHeightInt(), titleBarH, style.getTitleBar(), flowFraction));
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
            int flashColor = TooltipAnimationSystem.getSwitchFlashColor();
            StyleDefinition.AnimationConfig.SwitchEffectConfig switchEffect = style.getAnimation().getSwitchEffect();
            graphics.drawManaged(() -> TooltipRenderer.drawEntryAnimation(graphics, renderX, renderY, width, height, switchFlashProgress, fadeAlpha, isLeft, switchEffect, flashColor));
        }

        // 在关闭裁剪前提交缓冲区，确保裁剪范围生效
        graphics.flush();
        graphics.disableScissor();

        graphics.pose().popPose();

        // 缓存本帧的渲染颜色，供下一帧 crossfade 检测使用
        if (liveRender && !isReplayPhase && !crossfading) {
            lastBorderColor = borderColor;
            lastBgColor = bgColor;
        }

        return true;
    }

    public static boolean isVirtualItem(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getTooltipImage().isPresent();
    }

    /**
     * 批量解析 FillColorEntry 列表，返回已应用 colorModifier 的 ARGB 颜色数组。
     * <p>
     * 对每个 entry 调用 {@link DynamicColorResolver#resolve(StyleDefinition.FillColorEntry, ItemStack)}，
     * 确保动态 token（@rarityCore 等）和 colorModifier（brightness/saturation）都被应用。
     *
     * @param entries   FillColorEntry 列表（可为空）
     * @param itemStack 目标物品栈
     * @return 解析后的 ARGB 颜色数组，entries 为空时返回空数组
     */
    private static int[] resolveFillColors(List<StyleDefinition.FillColorEntry> entries, ItemStack itemStack) {
        if (entries == null || entries.isEmpty()) {
            return new int[0];
        }
        int[] colors = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            colors[i] = DynamicColorResolver.resolve(entries.get(i), itemStack);
        }
        return colors;
    }
}
