package org.yanbwe.colortooltips.util;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * 半透明缓冲区源 —— 继承 {@link MultiBufferSource.BufferSource} 以确保 BEWLR 的
 * {@code BufferSource} 强转不抛 {@link ClassCastException}，同时将实体物品的
 * 渲染类型重映射为半透明等价版本。
 * <p>
 * 床（entitySolid）、潜影盒（entityCutoutNoCull）、箱子（entityCutout）等物品
 * 使用的实体渲染类型默认禁用 Alpha 混合，导致淡出动画时模型瞬间消失。
 * 本类为每种实体渲染类型创建同纹理图集的半透明版本，确保淡出动画正确的同时
 * 纹理映射不被打乱。
 * <p>
 * 所有实际操作（getBuffer / endBatch）均委托给被包装的原始 {@link BufferSource}，
 * 仅拦截 {@link #getBuffer(RenderType)} 以重映射渲染类型。
 */
public class TranslucentBufferSource extends MultiBufferSource.BufferSource {

    /** 被包装的原始 BufferSource（所有操作最终委托到这里） */
    private final MultiBufferSource.BufferSource inner;

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

    // BufferSource 中 builder / fixedBuffers 字段的 SRG 名称（来自 MCP→SRG 映射表）
    private static final String SRG_BUILDER       = "f_109904_";
    private static final String SRG_FIXED_BUFFERS = "f_109905_";

    /**
     * 创建半透明包装器。
     * <p>
     * 通过 {@link ObfuscationReflectionHelper} 提取原始 {@link BufferSource} 的
     * {@code builder} 与 {@code fixedBuffers} 字段，传递给父类构造器以满足
     * {@link BufferSource} 的构造要求。
     *
     * @param inner 被包装的原始缓冲区源
     */
    @SuppressWarnings("unchecked")
    public TranslucentBufferSource(MultiBufferSource.BufferSource inner) {
        super(
            ObfuscationReflectionHelper.getPrivateValue(MultiBufferSource.BufferSource.class, inner, SRG_BUILDER),
            (Map<RenderType, BufferBuilder>) ObfuscationReflectionHelper.getPrivateValue(
                MultiBufferSource.BufferSource.class, inner, SRG_FIXED_BUFFERS)
        );
        this.inner = inner;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        RenderType actualType = TRANSLUCENT_REMAPS.getOrDefault(renderType, renderType);
        return inner.getBuffer(actualType);
    }

    @Override
    public void endBatch() {
        inner.endBatch();
    }

    @Override
    public void endBatch(RenderType renderType) {
        inner.endBatch(renderType);
    }

    @Override
    public void endLastBatch() {
        inner.endLastBatch();
    }

    /**
     * 创建半透明缓冲区源包装器。
     *
     * @param source 原始缓冲区源
     * @return 包装后的半透明缓冲区源
     */
    public static TranslucentBufferSource wrap(MultiBufferSource.BufferSource source) {
        return new TranslucentBufferSource(source);
    }
}
