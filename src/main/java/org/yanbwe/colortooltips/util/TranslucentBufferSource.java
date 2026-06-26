package org.yanbwe.colortooltips.util;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.SequencedMap;

/**
 * 半透明缓冲区源 —— 继承 {@link MultiBufferSource.BufferSource} 以确保 BEWLR 的
 * {@code BufferSource} 强转不抛 {@link ClassCastException}，同时将实体物品的
 * 渲染类型重映射为半透明等价版本。
 * <p>
 * NeoForge 1.21.1 适配：使用 Java 原生反射替代已移除的 ObfuscationReflectionHelper。
 */
public class TranslucentBufferSource extends MultiBufferSource.BufferSource {

    /** 被包装的原始 BufferSource */
    private final MultiBufferSource.BufferSource inner;

    /** 实体渲染类型 → 同图集的半透明版本映射表 */
    private static final Map<RenderType, RenderType> TRANSLUCENT_REMAPS = new HashMap<>();

    static {
        final RenderType blockTrans = Sheets.translucentItemSheet();

        TRANSLUCENT_REMAPS.put(Sheets.solidBlockSheet(),             blockTrans);
        TRANSLUCENT_REMAPS.put(Sheets.cutoutBlockSheet(),            blockTrans);
        TRANSLUCENT_REMAPS.put(Sheets.translucentCullBlockSheet(),   blockTrans);

        TRANSLUCENT_REMAPS.put(Sheets.bedSheet(),         RenderType.entityTranslucent(Sheets.BED_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.shulkerBoxSheet(),  RenderType.entityTranslucent(Sheets.SHULKER_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.chestSheet(),       RenderType.entityTranslucent(Sheets.CHEST_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.signSheet(),        RenderType.entityTranslucent(Sheets.SIGN_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.bannerSheet(),      RenderType.entityTranslucent(Sheets.BANNER_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.shieldSheet(),      RenderType.entityTranslucent(Sheets.SHIELD_SHEET));
        TRANSLUCENT_REMAPS.put(Sheets.armorTrimsSheet(false),  RenderType.entityTranslucent(Sheets.ARMOR_TRIMS_SHEET));
    }

    /**
     * 创建半透明包装器。
     * <p>
     * NeoForge 1.21.1 适配：使用 Java 原生反射访问 BufferSource 的
     * private 字段，替代已移除的 ObfuscationReflectionHelper。
     */
    @SuppressWarnings("unchecked")
    public TranslucentBufferSource(MultiBufferSource.BufferSource inner) {
        super(
            getPrivateField(inner, "sharedBuffer", ByteBufferBuilder.class),
            (SequencedMap<RenderType, ByteBufferBuilder>) getPrivateField(inner, "fixedBuffers", SequencedMap.class)
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

    public static TranslucentBufferSource wrap(MultiBufferSource.BufferSource source) {
        return new TranslucentBufferSource(source);
    }

    /**
     * NeoForge 1.21.1 适配：Java 原生反射访问私有字段，替代 ObfuscationReflectionHelper。
     */
    @SuppressWarnings("unchecked")
    private static <T> T getPrivateField(Object target, String fieldName, Class<T> fieldType) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return (T) field.get(target);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to access field: " + fieldName, e);
        }
        throw new RuntimeException("Field not found: " + fieldName);
    }
}
