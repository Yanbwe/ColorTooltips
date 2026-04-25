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
import org.yanbwe.colortooltips.compat.RarityCoreProxy;
import org.yanbwe.colortooltips.util.TranslucentBufferSource;

public class ColorHeaderComponent implements net.minecraft.world.inventory.tooltip.TooltipComponent {
    private static final int ITEM_OFFSET = 24;
    private static final int SPACING = 4;
    /** 单行文本标题栏高度（无 RarityCore 时使用） */
    private static final int SINGLE_LINE_BAR_HEIGHT = 12;

    private final ItemStack itemStack;
    private final Component nameText;
    private final Component rarityText;
    private final int rarityColor;
    private final boolean hasRarityCore;
    private final int titleBarHeight;

    public ColorHeaderComponent(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.nameText = Component.literal(itemStack.getItem().getName(itemStack).getString());
        this.hasRarityCore = RarityCoreProxy.isLoaded();

        if (hasRarityCore) {
            int rarity = RarityCoreProxy.getNormalizedRarity(itemStack);
            this.rarityColor = RarityCoreProxy.getRarityArgbColor(rarity);
            String rarityName = RarityCoreProxy.getLocalizedRarityTooltip(itemStack.getItem());
            this.rarityText = Component.literal(rarityName);
            this.titleBarHeight = 24;
        } else {
            this.rarityColor = RarityCoreProxy.FALLBACK_BORDER_COLOR;
            this.rarityText = null;
            this.titleBarHeight = SINGLE_LINE_BAR_HEIGHT;
        }
    }

    public int getHeight() {
        return titleBarHeight;
    }

    public int getTitleBarHeight() {
        return titleBarHeight;
    }

    public int getWidth(Font textRenderer) {
        int nameWidth = textRenderer.width(this.nameText);
        if (hasRarityCore && this.rarityText != null) {
            int rarityWidth = textRenderer.width(this.rarityText);
            return Math.max(nameWidth, rarityWidth) + ITEM_OFFSET + SPACING;
        }
        return nameWidth + ITEM_OFFSET + SPACING;
    }

    public int getTitleOffset() {
        return ITEM_OFFSET;
    }

    public void drawText(Font textRenderer, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource vertexConsumers) {
        float startDrawX = (float) x + ITEM_OFFSET;

        if (hasRarityCore && this.rarityText != null) {
            // RarityCore 存在：上方物品名 + 下方稀有度
            float startDrawY = y + 1;
            textRenderer.drawInBatch(this.nameText.getVisualOrderText(), startDrawX, startDrawY, -1, true, matrix, vertexConsumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            startDrawY += textRenderer.lineHeight + 2;
            textRenderer.drawInBatch(this.rarityText.getVisualOrderText(), startDrawX, startDrawY, this.rarityColor | 0xFF000000, true, matrix, vertexConsumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        } else {
            // 无 RarityCore：物品名垂直居中
            float startDrawY = y + (getHeight() - textRenderer.lineHeight) / 2.0f;
            textRenderer.drawInBatch(this.nameText.getVisualOrderText(), startDrawX, startDrawY, -1, true, matrix, vertexConsumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }
    }

    public void drawItems(Font textRenderer, int x, int y, GuiGraphics context) {
        RenderingContext.startTooltipItemRendering();
        int startDrawX = x + 2;
        int startDrawY = y + 3;

        float scale = TooltipAnimationSystem.getItemScale();
        float fadeAlpha = TooltipAnimationSystem.getAlpha();

        context.pose().pushPose();

        context.pose().translate(startDrawX + 8, startDrawY + 8, 0);
        context.pose().scale(scale, scale, 1.0f);
        context.pose().translate(-(startDrawX + 8), -(startDrawY + 8), 0);

        if (fadeAlpha < 0.999f) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, fadeAlpha);
            context.setColor(1.0f, 1.0f, 1.0f, fadeAlpha);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            renderItemWithTranslucentSupport(context, this.itemStack, startDrawX, startDrawY);

            context.flush();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            context.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        } else {
            renderItemWithTranslucentSupport(context, this.itemStack, startDrawX, startDrawY);
        }

        context.pose().popPose();

        RenderingContext.endTooltipItemRendering();
    }

    private void renderItemWithTranslucentSupport(GuiGraphics context, ItemStack stack, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        BakedModel bakedModel = itemRenderer.getModel(stack, minecraft.level, minecraft.player, 0);

        context.pose().pushPose();
        context.pose().translate(x + 8, y + 8, 150 + (bakedModel.isGui3d() ? 0 : 0));
        context.pose().mulPoseMatrix(new org.joml.Matrix4f().scaling(1.0f, -1.0f, 1.0f));
        context.pose().scale(16.0f, 16.0f, 16.0f);

        boolean useFlatLighting = !bakedModel.usesBlockLight();
        if (useFlatLighting) {
            Lighting.setupForFlatItems();
        }

        TranslucentBufferSource translucentSource = TranslucentBufferSource.wrap(context.bufferSource());
        itemRenderer.render(
            stack,
            ItemDisplayContext.GUI,
            false,
            context.pose(),
            translucentSource,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            bakedModel
        );
        context.flush();

        if (useFlatLighting) {
            Lighting.setupFor3DItems();
        }

        context.pose().popPose();
    }
}
