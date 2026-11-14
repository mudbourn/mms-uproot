package info.mudbourn.mmsuproot.shim.client;

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
}
