package org.yanbwe.colortooltips.tooltip;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.yanbwe.colortooltips.RenderingContext;
import org.yanbwe.colortooltips.animation.TooltipAnimationSystem;
import org.yanbwe.colortooltips.config.DynamicColorResolver;
import org.yanbwe.colortooltips.config.StyleDefinition;
import org.yanbwe.colortooltips.util.TranslucentBufferSource;

/**
 * 彩色标题栏组件 — 负责标题栏内物品名、额外提示文本和物品模型的布局与渲染。
 * <p>
 * 标题栏高度由样式 {@code titleBar.twoHeight} 决定：单行 12px，双行 24px。
 * 物品模型大小由 {@code itemModel.bigSize} 决定：小 16px，大 32px，
 * 并影响文本的水平偏移量 {@code itemOffset}。
 * <p>
 * 额外提示文本 ({@code extraToolTip.enabled}) 决定文本布局：
 * 双行有额外信息时物品名在上、额外信息在下自然排列；
 * 双行无额外信息时物品名垂直居中独占两行。
 * <p>
 * 纯文本提示框模式下强制禁用所有装饰（titleBar、itemModel、extraToolTip、物品名变色）。
 */
public class ColorHeaderComponent implements net.minecraft.world.inventory.tooltip.TooltipComponent {

    /** 文本与物品模型之间的水平间距 */
    private static final int SPACING = 4;
    /** 小物品模型水平偏移量：16 (模型) + 4 (间距) = 20 */
    private static final int ITEM_OFFSET_SMALL = 20;
    /** 大物品模型水平偏移量：32 (模型) + 4 (间距) = 36 */
    private static final int ITEM_OFFSET_LARGE = 36;
    /** 单行文本标题栏高度 */
    private static final int SINGLE_LINE_BAR_HEIGHT = 12;
    /** 双行文本标题栏高度 */
    private static final int DOUBLE_LINE_BAR_HEIGHT = 24;

    // ── 不可变数据 ──
    private final ItemStack itemStack;
    private final Component nameText;

    // ── 通过 setStyleDefinition 注入的配置参数 ──
    private int itemOffset = ITEM_OFFSET_LARGE;
    private int titleBarHeight = DOUBLE_LINE_BAR_HEIGHT;
    private boolean twoHeight = true;
    private boolean bigSize = true;
    private boolean hasExtra;
    private String extraContent;
    private int extraContentColor = -1;
    private int itemNameColor = -1;     // -1 = 使用默认白色
    private boolean itemModelEnabled = true;

    /**
     * 构造标题栏组件。
     *
     * @param itemStack 关联的物品栈
     */
    public ColorHeaderComponent(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.nameText = Component.literal(itemStack.getHoverName().getString());
    }

    /**
     * 根据样式定义配置此组件的所有渲染参数。
     * <p>
     * 调用此方法后，{@link #getHeight()}、{@link #getWidth(Font)} 等布局方法
     * 将返回基于样式计算的正确值。
     * <p>
     * 纯文本提示框模式 ({@code isTextOnly=true}) 下强制禁用所有装饰：
     * titleBar、itemModel、extraToolTip 以及物品名变色。
     *
     * @param style      样式定义，为 null 时使用 {@link StyleDefinition#createDefault()}
     * @param isTextOnly 是否为纯文本提示框模式
     */
    public void setStyleDefinition(StyleDefinition style, boolean isTextOnly) {
        if (style == null) {
            style = StyleDefinition.createDefault();
        }

        if (isTextOnly) {
            // 纯文本模式：标题栏回退为单行、禁用物品模型和额外信息、物品名使用默认白色
            this.twoHeight = false;
            this.bigSize = false;
            this.itemOffset = ITEM_OFFSET_SMALL;
            this.titleBarHeight = SINGLE_LINE_BAR_HEIGHT;
            this.hasExtra = false;
            this.extraContent = null;
            this.itemNameColor = -1;
            this.itemModelEnabled = false;
            return;
        }

        // ── 标题栏 ──
        StyleDefinition.TitleBarConfig titleBar = style.getTitleBar();
        this.twoHeight = titleBar.isTwoHeight();
        this.titleBarHeight = twoHeight ? DOUBLE_LINE_BAR_HEIGHT : SINGLE_LINE_BAR_HEIGHT;

        // ── 物品模型：bigSize 决定偏移量和渲染尺寸 ──
        StyleDefinition.ItemModelConfig itemModel = style.getItemModel();
        this.itemModelEnabled = itemModel.isEnabled();
        this.bigSize = itemModel.isBigSize();
        this.itemOffset = bigSize ? ITEM_OFFSET_LARGE : ITEM_OFFSET_SMALL;

        // ── 物品名称变色 ──
        StyleDefinition.ItemNameConfig.ChangeColorConfig changeColor = style.getItemName().getChangeColor();
        if (changeColor.isEnabled()) {
            this.itemNameColor = DynamicColorResolver.resolve(changeColor.getColor(), itemStack);
        } else {
            this.itemNameColor = -1;
        }

        // ── 额外提示文本 ──
        StyleDefinition.ExtraToolTipConfig extra = style.getExtraToolTip();
        if (extra.isEnabled()) {
            String content = DynamicColorResolver.resolveDynamicContent(extra.getContent(), itemStack);
            if (content != null && !content.isEmpty()) {
                this.hasExtra = true;
                this.extraContent = content;
                this.extraContentColor = DynamicColorResolver.resolve(extra.getContentColor(), itemStack);
            } else {
                this.hasExtra = false;
                this.extraContent = null;
            }
        } else {
            this.hasExtra = false;
            this.extraContent = null;
        }
    }

    // ══════════════════════════════════════════════════════════
    // 布局尺寸查询
    // ══════════════════════════════════════════════════════════

    /** @return 标题栏高度：单行 12px / 双行 24px */
    public int getHeight() {
        return titleBarHeight;
    }

    /** @return 同 {@link #getHeight()}，供外部显式查询 */
    public int getTitleBarHeight() {
        return titleBarHeight;
    }

    /**
     * 计算组件的理想宽度。
     * 取物品名宽度与额外信息宽度的较大值，加上物品模型偏移和间距。
     */
    public int getWidth(Font textRenderer) {
        int nameWidth = textRenderer.width(this.nameText);
        if (hasExtra && extraContent != null) {
            int extraWidth = textRenderer.width(Component.literal(extraContent));
            return Math.max(nameWidth, extraWidth) + itemOffset + SPACING;
        }
        return nameWidth + itemOffset + SPACING;
    }

    /** @return 文本起始位置的水平偏移（取决于 bigSize） */
    public int getTitleOffset() {
        return itemOffset;
    }

    // ══════════════════════════════════════════════════════════
    // 文本渲染
    // ══════════════════════════════════════════════════════════

    /**
     * 渲染标题栏内的文本（物品名 + 可选额外信息）。
     * <ul>
     *   <li>两行 + 有额外信息：物品名在上，额外信息在下，自然排列</li>
     *   <li>两行 + 无额外信息：物品名垂直居中，独占两行</li>
     *   <li>单行 ± 有/无额外信息：物品名垂直居中（bigSize 时自然右移）</li>
     * </ul>
     */
    public void drawText(Font textRenderer, int x, int y, Matrix4f matrix,
                          MultiBufferSource.BufferSource vertexConsumers) {
        float startDrawX = (float) x + itemOffset;

        if (hasExtra && extraContent != null && twoHeight) {
            // 两行有额外信息：物品名在第一行，额外信息在第二行
            float startDrawY = y + 1;
            drawSingleText(textRenderer, this.nameText, startDrawX, startDrawY,
                    itemNameColor, matrix, vertexConsumers);
            startDrawY += textRenderer.lineHeight + 2;
            drawSingleText(textRenderer, Component.literal(extraContent), startDrawX, startDrawY,
                    extraContentColor, matrix, vertexConsumers);
        } else {
            // 单行或两行无额外信息：物品名垂直居中
            float startDrawY = y + (getHeight() - textRenderer.lineHeight) / 2.0f;
            drawSingleText(textRenderer, this.nameText, startDrawX, startDrawY,
                    itemNameColor, matrix, vertexConsumers);
        }
    }

    /**
     * 绘制单行文本。
     * color = -1 表示使用默认白色；否则确保 alpha 通道后使用自定义颜色。
     */
    private void drawSingleText(Font textRenderer, Component text, float x, float y,
                                 int color, Matrix4f matrix,
                                 MultiBufferSource.BufferSource vertexConsumers) {
        int drawColor = (color == -1) ? -1 : (color | 0xFF000000);
        textRenderer.drawInBatch(text.getVisualOrderText(), x, y, drawColor, true,
                matrix, vertexConsumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }

    // ══════════════════════════════════════════════════════════
    // 物品模型渲染
    // ══════════════════════════════════════════════════════════

    /**
     * 渲染物品模型图标。
     * 物品尺寸由 {@link #bigSize} 决定：32px (大) 或 16px (小)。
     * 在标题栏高度内垂直居中。
     */
    public void drawItems(Font textRenderer, int x, int y, GuiGraphics context) {
        if (!itemModelEnabled) return;

        RenderingContext.startTooltipItemRendering();

        float scale = TooltipAnimationSystem.getItemScale();
        float fadeAlpha = TooltipAnimationSystem.getAlpha();

        int itemSize = bigSize ? 32 : 16;
        int startDrawX = x + 2;
        int startDrawY = y + (titleBarHeight - itemSize) / 2;

        float centerX = startDrawX + itemSize / 2.0f;
        float centerY = startDrawY + itemSize / 2.0f;

        context.pose().pushPose();
        context.pose().translate(centerX, centerY, 0);
        context.pose().scale(scale, scale, 1.0f);
        context.pose().translate(-centerX, -centerY, 0);

        // Bug 6 fix: 背景/边框绘制后 RenderSystem.enableDepthTest() 使深度测试保持开启，
        // ItemRenderer 使用的 renderType（如 translucentItemSheet）在深度测试开启时可能
        // 因 z-fighting 导致物品模型不可见。显式禁用深度测试确保物品模型正确渲染。
        if (fadeAlpha < 0.999f) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, fadeAlpha);
            context.setColor(1.0f, 1.0f, 1.0f, fadeAlpha);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            renderItemAtSize(context, itemStack, startDrawX, startDrawY, itemSize);
            context.flush();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            context.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        } else {
            RenderSystem.disableDepthTest();
            renderItemAtSize(context, itemStack, startDrawX, startDrawY, itemSize);
            RenderSystem.enableDepthTest();
        }

        context.pose().popPose();
        RenderingContext.endTooltipItemRendering();
    }

    /**
     * 以指定像素尺寸渲染物品模型。
     * 支持可变尺寸的 itemSize 参数，替代原有的固定 16px 渲染。
     *
     * @param context  渲染上下文
     * @param stack    物品栈
     * @param x        渲染起始 X 坐标
     * @param y        渲染起始 Y 坐标
     * @param itemSize 物品模型的目标像素尺寸
     */
    private void renderItemAtSize(GuiGraphics context, ItemStack stack, int x, int y, int itemSize) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        BakedModel bakedModel = itemRenderer.getModel(stack, minecraft.level, minecraft.player, 0);

        float half = itemSize / 2.0f;

        context.pose().pushPose();
        context.pose().translate(x + half, y + half, 150);
        context.pose().mulPoseMatrix(new Matrix4f().scaling(1.0f, -1.0f, 1.0f));
        context.pose().scale((float) itemSize, (float) itemSize, (float) itemSize);

        boolean useFlatLighting = !bakedModel.usesBlockLight();
        if (useFlatLighting) {
            Lighting.setupForFlatItems();
        }

        TranslucentBufferSource translucentSource = TranslucentBufferSource.wrap(context.bufferSource());
        itemRenderer.render(stack, ItemDisplayContext.GUI, false, context.pose(),
                translucentSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, bakedModel);
        context.flush();

        if (useFlatLighting) {
            Lighting.setupFor3DItems();
        }

        context.pose().popPose();
    }
}
