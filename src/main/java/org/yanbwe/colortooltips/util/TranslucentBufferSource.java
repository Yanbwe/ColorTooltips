package org.yanbwe.colortooltips.util;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;

import java.util.Map;
import java.util.Optional;

public class TranslucentBufferSource implements MultiBufferSource {
    private final MultiBufferSource.BufferSource inner;
    private final RenderType translucentReplacement;

    public TranslucentBufferSource(MultiBufferSource.BufferSource inner) {
        this.inner = inner;
        this.translucentReplacement = Sheets.translucentItemSheet();
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        RenderType actualType = remapRenderType(renderType);
        return inner.getBuffer(actualType);
    }

    private RenderType remapRenderType(RenderType renderType) {
        if (renderType == Sheets.cutoutBlockSheet()) {
            return translucentReplacement;
        }
        if (renderType == Sheets.translucentCullBlockSheet()) {
            return translucentReplacement;
        }
        return renderType;
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
