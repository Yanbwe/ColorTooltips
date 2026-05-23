package org.yanbwe.colortooltips.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;

import java.util.HashMap;
import java.util.Map;

/**
 * 半透明缓冲区源包装器 —— 将实体物品（BEWLR）的渲染类型重映射为半透明等价版本。
 * <p>
 * 床（entitySolid）、潜影盒（entityCutoutNoCull）、箱子（entityCutout）等物品
 * 使用的实体渲染类型默认禁用 Alpha 混合，导致淡出动画时模型瞬间消失。
 * 本类为每种实体渲染类型创建同纹理图集的半透明版本，确保淡出动画正确的同时
 * 纹理映射不被打乱。
 */
public class TranslucentBufferSource implements MultiBufferSource {
    private final MultiBufferSource.BufferSource inner;
    private final RenderType translucentReplacement;

    /** 实体渲染类型 → 同图集的半透明版本映射表 */
    private static final Map<RenderType, RenderType> TRANSLUCENT_REMAPS = new HashMap<>();

    static {
        final RenderType blockTrans = Sheets.translucentItemSheet(); // LOCATION_BLOCKS 图集

        // 方块图集类型（共享 LOCATION_BLOCKS）→ translucentItemSheet
        TRANSLUCENT_REMAPS.put(Sheets.solidBlockSheet(),             blockTrans);
        TRANSLUCENT_REMAPS.put(Sheets.cutoutBlockSheet(),            blockTrans);
        TRANSLUCENT_REMAPS.put(Sheets.translucentCullBlockSheet(),   blockTrans);

        // 实体渲染类型（各自独立图集）→ entityTranslucent(对应图集)
        TRANSLUCENT_REMAPS.put(Sheets.bedSheet(),         RenderType.entityTranslucent(Sheets.BED_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.shulkerBoxSheet(),  RenderType.entityTranslucent(Sheets.SHULKER_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.chestSheet(),       RenderType.entityTranslucent(Sheets.CHEST_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.signSheet(),        RenderType.entityTranslucent(Sheets.SIGN_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.bannerSheet(),      RenderType.entityTranslucent(Sheets.BANNER_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.shieldSheet(),      RenderType.entityTranslucent(Sheets.SHIELD_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.armorTrimsSheet(),  RenderType.entityTranslucent(Sheets.ARMOR_TRIMS_SHEET));
    }

    public TranslucentBufferSource(MultiBufferSource.BufferSource inner) {
        this.inner = inner;
        this.translucentReplacement = Sheets.translucentItemSheet();
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        RenderType actualType = TRANSLUCENT_REMAPS.getOrDefault(renderType, renderType);
        return inner.getBuffer(actualType);
    }

    public void endBatch() {
        inner.endBatch();
    }

    public void endBatch(RenderType renderType) {
        inner.endBatch(renderType);
    }

    public static TranslucentBufferSource wrap(MultiBufferSource.BufferSource source) {
        return new TranslucentBufferSource(source);
    }
}
