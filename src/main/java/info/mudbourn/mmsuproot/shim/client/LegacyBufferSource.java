package info.mudbourn.mmsuproot.shim.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * 1.21.11's {@code MultiBufferSource}: something that hands out the vertex consumer of a render type. 26.3 has no immediate-mode
 * buffer sources (geometry is submitted to a {@code SubmitNodeCollector}), so this keeps the type and its one method for the mods
 * that take one as a parameter; whoever supplies an instance supplies the consumer.
 */
public interface LegacyBufferSource {
	VertexConsumer getBuffer(RenderType type);
}
