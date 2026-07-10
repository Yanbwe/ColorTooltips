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
    /** 小物品模型宽度：10px */
    private static final int ITEM_SIZE_SMALL = 10;
    /** 大物品模型宽度：20px */
    private static final int ITEM_SIZE_LARGE = 20;
    /** 小物品模型水平偏移量：10 + 4 = 14 */
    private static final int ITEM_OFFSET_SMALL = ITEM_SIZE_SMALL + SPACING;
    /** 大物品模型水平偏移量：20 + 4 = 24 */
    private static final int ITEM_OFFSET_LARGE = ITEM_SIZE_LARGE + SPACING;
    /** 单行文本标题栏高度 */
    private static final int SINGLE_LINE_BAR_HEIGHT = 11;
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
    private int itemNameColor = -1;
    private boolean itemModelEnabled = true;
    /** 大模型时额外文本内嵌渲染（与物品名同样右移），否则由原生文本行处理 */
    private boolean renderExtraInHeader;
    private String extraContent;
    private int extraContentColor = -1;

    /**
     * 构造标题栏组件。
     *
     * @param itemStack 关联的物品栈
     */
    public ColorHeaderComponent(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.nameText = itemStack.getHoverName();
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
            this.itemNameColor = -1;
            this.itemModelEnabled = false;
            this.renderExtraInHeader = false;
            return;
        }

        // ── 标题栏高度：twoHeight 或 bigSize 任一开启 → 24px ──
        StyleDefinition.TitleBarConfig titleBar = style.getTitleBar();
        this.twoHeight = titleBar.isTwoHeight();

        // ── 物品模型：bigSize 决定渲染尺寸和文本偏移量 ──
        StyleDefinition.ItemModelConfig itemModel = style.getItemModel();
        this.itemModelEnabled = itemModel.isEnabled();
        this.bigSize = itemModelEnabled && itemModel.isBigSize();

        // bigSize=true 强制两行高度，容纳大尺寸模型
        this.titleBarHeight = (twoHeight || bigSize) ? DOUBLE_LINE_BAR_HEIGHT : SINGLE_LINE_BAR_HEIGHT;
        // 物品模型未启用时不右移文本
        this.itemOffset = itemModelEnabled ? (bigSize ? ITEM_OFFSET_LARGE : ITEM_OFFSET_SMALL) : 2;

        // ── 物品名称变色 ──
        StyleDefinition.ItemNameConfig.ChangeColorConfig changeColor = style.getItemName().getChangeColor();
        if (changeColor.isEnabled()) {
            this.itemNameColor = DynamicColorResolver.resolve(changeColor.getColor(), itemStack);
        } else {
            this.itemNameColor = -1;
        }

        // ── 大模型或双行标题栏 + 额外提示 → 内嵌渲染；否则原生文本行 ──
        StyleDefinition.ExtraToolTipConfig extra = style.getExtraToolTip();
        if (extra.isEnabled() && (bigSize || twoHeight)) {
            String content = DynamicColorResolver.resolveDynamicContent(extra.getContent(), itemStack);
            if (content != null && !content.isEmpty()) {
                this.renderExtraInHeader = true;
                this.extraContent = content;
                this.extraContentColor = DynamicColorResolver.resolve(extra.getContentColor(), itemStack);
            } else {
                this.renderExtraInHeader = false;
            }
        } else {
            this.renderExtraInHeader = false;
        }
    }

    // ══════════════════════════════════════════════════════════
    // 布局尺寸查询
    // ══════════════════════════════════════════════════════════

    public int getHeight() {
        if (!itemModelEnabled && !renderExtraInHeader) return 9;
        return titleBarHeight;
    }

    /** @return 同 {@link #getHeight()}，供外部显式查询 */
    public int getTitleBarHeight() {
        return titleBarHeight;
    }

    /** @return 大模型时额外文本是否由 header 内嵌渲染 */
    public boolean isRenderingExtra() { return renderExtraInHeader; }

    /**
     * 计算组件的理想宽度。
     * 无物品模型、无额外文本时与原版一致（不加额外边距）。
     */
    public int getWidth(Font textRenderer) {
        int nameWidth = textRenderer.width(this.nameText);
        if (renderExtraInHeader) {
            int extraWidth = textRenderer.width(net.minecraft.network.chat.Component.literal(extraContent));
            int offset = bigSize ? (itemOffset + SPACING) : 0;
            return Math.max(nameWidth, extraWidth) + offset;
        }
        if (itemModelEnabled) {
            return nameWidth + itemOffset + SPACING;
        }
        return nameWidth;
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
        if (renderExtraInHeader) {
            // 内嵌额外文本（大模型右移，纯双行不右移）
            float startDrawX = bigSize ? (x + itemOffset - 2) : (x + 2);
            drawSingleText(textRenderer, this.nameText, startDrawX, y + 1,
                    itemNameColor, matrix, vertexConsumers);
            drawSingleText(textRenderer, net.minecraft.network.chat.Component.literal(extraContent),
                    startDrawX, y + 1 + textRenderer.lineHeight + 2,
                    extraContentColor, matrix, vertexConsumers);
        } else if (!itemModelEnabled) {
            drawSingleText(textRenderer, this.nameText, x + 2, y + 1,
                    itemNameColor, matrix, vertexConsumers);
        } else {
            float startDrawX = (float) x + itemOffset - 2;
            float startDrawY = y + (titleBarHeight - textRenderer.lineHeight) / 2.0f - 1;
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
        // 使用 Component 重载以保留斜体等样式
        textRenderer.drawInBatch(text, x, y, drawColor, true,
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

        int itemSize = bigSize ? ITEM_SIZE_LARGE : ITEM_SIZE_SMALL;
        int startDrawX = x ;
        int startDrawY = y + ((titleBarHeight - itemSize) / 2)-1;

        float centerX = startDrawX + itemSize / 2f;
        float centerY = startDrawY + itemSize / 2f;

        boolean posePushed = false;
        try {
        context.pose().pushPose();
        context.pose().translate(centerX, centerY, 0);
        context.pose().scale(scale, scale, 1.0f);
        context.pose().translate(-centerX, -centerY, 0);
        posePushed = true;

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
        } finally {
            if (posePushed) {
                context.pose().popPose();
            }
            // 还原全局渲染状态，防止污染后续原版渲染（如附魔台、JEI 的 tooltip）
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            context.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            RenderingContext.endTooltipItemRendering();
        }
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

        boolean useFlatLighting = !bakedModel.usesBlockLight();
        boolean posePushed = false;
        try {
            context.pose().pushPose();
            context.pose().translate(x + half, y + half, 150);
            context.pose().mulPose(new Matrix4f().scaling(1.0f, -1.0f, 1.0f));
            context.pose().scale((float) itemSize, (float) itemSize, (float) itemSize);
            posePushed = true;

            if (useFlatLighting) {
                Lighting.setupForFlatItems();
            }

            TranslucentBufferSource translucentSource = TranslucentBufferSource.wrap(context.bufferSource());
            itemRenderer.render(stack, ItemDisplayContext.GUI, false, context.pose(),
                    translucentSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, bakedModel);
            context.flush();
        } finally {
            // 还原光照与矩阵状态，防止污染后续原版渲染
            if (useFlatLighting) {
                Lighting.setupFor3DItems();
            }
            if (posePushed) {
                context.pose().popPose();
            }
        }
    }
}
