package info.mudbourn.mmsuproot.shim.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import info.mudbourn.mmsuproot.shim.Approximate;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * 1.21.11's {@code MultiBufferSource}: something that hands out the vertex consumer of a render type. 26.3 has no immediate-mode
 * buffer sources (geometry is submitted to a {@code SubmitNodeCollector}), so this keeps the type and its one method for the mods
 * that take one as a parameter; whoever supplies an instance supplies the consumer.
 */
public interface LegacyBufferSource {
	VertexConsumer getBuffer(RenderType type);

	/**
	 * 1.21.11's {@code MultiBufferSource.BufferSource}: the batch that vertex consumers of render types were written into and that
	 * {@code endBatch} drew. 26.3 has nothing to draw into this way, so this one hands out no consumer (the call throws, rather than
	 * dropping the vertices) and has nothing to flush: what a mod submits to the current picture-in-picture collector is drawn when that
	 * render returns (see {@link LegacyRender#submitNodeStorage}).
	 */
	final class BufferSource implements LegacyBufferSource {
		public static final BufferSource INSTANCE = new BufferSource();

		@Override
		public VertexConsumer getBuffer(RenderType type) {
			throw new UnsupportedOperationException("26.3 has no immediate-mode vertex consumers: " + type);
		}

		@Approximate("nothing is flushed here: what was submitted is drawn when the picture-in-picture render returns")
		public void endBatch() { }

		@Approximate("nothing is flushed here: what was submitted is drawn when the picture-in-picture render returns")
		public void endBatch(RenderType type) { }

		@Approximate("nothing is flushed here: what was submitted is drawn when the picture-in-picture render returns")
		public void endLastBatch() { }
	}
}
