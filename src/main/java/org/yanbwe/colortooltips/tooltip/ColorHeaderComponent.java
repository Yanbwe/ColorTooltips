package org.yanbwe.colortooltips.tooltip;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.yanbwe.colortooltips.RenderingContext;
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

        float scale = org.yanbwe.colortooltips.util.TooltipAnimationManager.getItemScale();
        float fadeAlpha = org.yanbwe.colortooltips.util.TooltipFadeManager.getFadeAlpha();

        context.pose().pushPose();
        
        // 将原点移到物品中心以便缩放
        context.pose().translate(startDrawX + 8, startDrawY + 8, 0);
        context.pose().scale(scale, scale, 1.0f);
        context.pose().translate(-(startDrawX + 8), -(startDrawY + 8), 0);

        // 如果处于淡出/淡入过程,通过包装 MultiBufferSource 强制将 3D 方块的 Solid/Cutout 渲染类型转换为 Translucent
        if (fadeAlpha < 1.0f) {
            context.flush();
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, fadeAlpha);

            var itemRenderer = net.minecraft.client.Minecraft.getInstance().getItemRenderer();
            var bufferSource = context.bufferSource();
            
            net.minecraft.client.renderer.MultiBufferSource wrapped = rt -> {
                net.minecraft.client.renderer.RenderType target = rt;
                String rtName = rt.toString();
                if (rtName.contains("solid") || rtName.contains("cutout")) {
                    target = net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
                }
                return bufferSource.getBuffer(target);
            };

            // 直接调用底层渲染方法以应用包装后的 BufferSource
            itemRenderer.renderStatic(this.itemStack, net.minecraft.world.item.ItemDisplayContext.GUI, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, context.pose(), wrapped, net.minecraft.client.Minecraft.getInstance().level, 0);
            
            // 装饰品通常使用 translucent 的 gui 类型,直接使用 context 渲染即可(受 setShaderColor 影响)
            context.renderItemDecorations(textRenderer, this.itemStack, startDrawX, startDrawY);

            context.flush();
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } else {
            context.renderItem(this.itemStack, startDrawX, startDrawY);
            context.renderItemDecorations(textRenderer, this.itemStack, startDrawX, startDrawY);
        }

        context.pose().popPose();

        RenderingContext.endTooltipItemRendering();
    }
}