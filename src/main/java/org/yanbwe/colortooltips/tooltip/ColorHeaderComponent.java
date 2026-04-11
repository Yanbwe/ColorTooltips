package org.yanbwe.colortooltips.tooltip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.yanbwe.colortooltips.RenderingContext;
import org.yanbwe.colortooltips.animation.TooltipAnimationSystem;
import org.yanbwe.raritycore.registry.RarityRegistry;
import org.yanbwe.raritycore.util.RarityColorUtil;

public class ColorHeaderComponent implements net.minecraft.world.inventory.tooltip.TooltipComponent {
    private static final int ITEM_OFFSET = 24;
    private static final int SPACING = 4;

    private final ItemStack itemStack;
    private final Component nameText;
    private final Component rarityText;
    private final int rarityColor;

    public ColorHeaderComponent(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.nameText = Component.literal(itemStack.getItem().getName(itemStack).getString());
        int rarity = RarityRegistry.getNormalizedRarity(itemStack);
        this.rarityColor = RarityColorUtil.getRarityArgbColor(rarity);
        String rarityName = RarityRegistry.getLocalizedRarityTooltip(itemStack.getItem());
        this.rarityText = Component.literal(rarityName);
    }

    public int getHeight() {
        return 24;
    }

    public int getWidth(Font textRenderer) {
        int nameWidth = textRenderer.width(this.nameText);
        int rarityWidth = textRenderer.width(this.rarityText);
        return Math.max(nameWidth, rarityWidth) + ITEM_OFFSET + SPACING;
    }

    public int getTitleOffset() {
        return ITEM_OFFSET;
    }

    public void drawText(Font textRenderer, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource vertexConsumers) {
        float startDrawX = (float) x + ITEM_OFFSET;
        float startDrawY = y + 1;
        
        textRenderer.drawInBatch(this.nameText.getVisualOrderText(), startDrawX, startDrawY, -1, true, matrix, vertexConsumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        startDrawY += textRenderer.lineHeight + 2;
        textRenderer.drawInBatch(this.rarityText.getVisualOrderText(), startDrawX, startDrawY, this.rarityColor | 0xFF000000, true, matrix, vertexConsumers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
    }

    public void drawItems(Font textRenderer, int x, int y, GuiGraphics context) {
        RenderingContext.startTooltipItemRendering();
        int startDrawX = x + 2;
        int startDrawY = y + 3;

        float scale = TooltipAnimationSystem.getItemScale();
        float fadeAlpha = TooltipAnimationSystem.getAlpha();

        context.pose().pushPose();

        // 将原点移到物品中心以便缩放
        context.pose().translate(startDrawX + 8, startDrawY + 8, 0);
        context.pose().scale(scale, scale, 1.0f);
        context.pose().translate(-(startDrawX + 8), -(startDrawY + 8), 0);

        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var itemRenderer = minecraft.getItemRenderer();

        // 始终使用着色器透明度控制，确保淡入淡出效果一致
        context.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, fadeAlpha);

        var bufferSource = context.bufferSource();
        MultiBufferSource wrapped = rt -> {
            RenderType target = rt;
            String rtName = rt.toString();
            if (rtName.contains("solid") || rtName.contains("cutout")) {
                target = RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS);
            }
            return bufferSource.getBuffer(target);
        };

        itemRenderer.renderStatic(this.itemStack, ItemDisplayContext.GUI, 0xF000F0, OverlayTexture.NO_OVERLAY, context.pose(), wrapped, minecraft.level, 0);
        context.renderItemDecorations(textRenderer, this.itemStack, startDrawX, startDrawY);

        context.flush();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();

        context.pose().popPose();

        RenderingContext.endTooltipItemRendering();
    }
}
