package info.mudbourn.mmsuproot.shim.client;

import net.minecraft.client.renderer.BindGroupLayouts;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.nio.ByteBuffer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.pipeline.UniformType;

/** Stand-ins for GPU abstraction calls that 26.3 reshaped, answered for the mods built for 26.2 and 1.21.11. */
public final class LegacyGpu {
	private LegacyGpu() { }

	/** {@code CommandEncoder.writeToTexture(texture, pixels, format, ...)}: 26.3 reads the pixels in the texture's own format, so the pixel format is dropped. */
	public static void writeToTexture(CommandEncoder encoder, GpuTexture texture, ByteBuffer pixels, NativeImage.Format format,
			int mipLevel, int depthOrLayer, int x, int y, int width, int height) {
		encoder.writeToTexture(texture, pixels, mipLevel, depthOrLayer, x, y, width, height);
	}

	/** {@code BindGroupLayout.Builder.withSampler(name)}: 26.3 declares a sampler as a combined image sampler uniform. */
	public static BindGroupLayout.Builder withSampler(BindGroupLayout.Builder builder, String name) {
		return builder.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
	}

	/**
	 * 1.21.11's {@code RenderPipelines.MATRICES_PROJECTION_SNIPPET}: the projection and dynamic transform blocks a GUI pipeline reads. 26.3 holds
	 * them as bind group layouts, and its shaders also read the globals block, so that is part of this snippet.
	 */
	public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET = RenderPipeline.builder()
			.withBindGroupLayout(BindGroupLayouts.GLOBALS)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.buildSnippet();

	/** {@code RenderPipeline.Builder.withVertexFormat(format, mode)}: 26.3 binds the format to buffer 0 and sets the topology on its own. */
	public static RenderPipeline.Builder withVertexFormat(RenderPipeline.Builder builder, VertexFormat format, PrimitiveTopology topology) {
		return builder.withVertexBinding(0, format).withPrimitiveTopology(topology);
	}

	/** {@code RenderPipeline.Builder.withBlend(blend)}: the blend is part of the colour target state now. */
	public static RenderPipeline.Builder withBlend(RenderPipeline.Builder builder, BlendFunction blend) {
		return builder.withColorTargetState(new ColorTargetState(blend));
	}

	/** {@code RenderPipeline.Builder.withoutBlend()}: the default colour target state has no blend. */
	public static RenderPipeline.Builder withoutBlend(RenderPipeline.Builder builder) {
		return builder.withColorTargetState(ColorTargetState.DEFAULT);
	}

	/** {@code RenderPipeline.Builder.withSampler(name)}: a sampler is a combined image sampler in a bind group layout of its own. */
	public static RenderPipeline.Builder withSampler(RenderPipeline.Builder builder, String name) {
		return builder.withBindGroupLayout(BindGroupLayout.builder().withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER).build());
	}

	/** {@code RenderPipeline.Builder.withUniform(name, type)}: a uniform is declared in a bind group layout of its own. */
	public static RenderPipeline.Builder withUniform(RenderPipeline.Builder builder, String name, UniformType type) {
		return builder.withBindGroupLayout(BindGroupLayout.builder().withUniform(name, type).build());
	}

	/**
	 * {@code RenderPipeline.Builder.build()}: a 1.21.11 pipeline always drew to the colour target and only blended when it said so, while a 26.3
	 * pipeline draws to as many colour targets as it names. A pipeline built without naming any is given the one that writes all four channels, unblended.
	 */
	public static RenderPipeline build(RenderPipeline.Builder builder) {
		if (builder.activeColorTargetStateCount == 0) builder.withColorTargetState(ColorTargetState.DEFAULT);
		return builder.build();
	}
}
