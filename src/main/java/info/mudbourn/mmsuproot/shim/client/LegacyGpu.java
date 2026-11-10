package info.mudbourn.mmsuproot.shim.client;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;

/** Stand-ins for GPU abstraction calls that 26.3 reshaped, answered for the mods built for 26.2. */
public final class LegacyGpu {
	private LegacyGpu() { }

	/** {@code BindGroupLayout.Builder.withSampler(name)}: 26.3 declares a sampler as a combined image sampler uniform. */
	public static BindGroupLayout.Builder withSampler(BindGroupLayout.Builder builder, String name) {
		return builder.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
	}
}
