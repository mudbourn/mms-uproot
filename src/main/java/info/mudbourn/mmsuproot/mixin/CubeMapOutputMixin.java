package info.mudbourn.mmsuproot.mixin;

import java.util.Optional;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import info.mudbourn.mmsuproot.shim.client.OwoCubeMap;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * owo-lib's cube map output hook for 26.3: while owo's cube map component draws, the render pass of {@code CubeMap.render} goes to the
 * textures owo names (colour, the clear colour as a vector, depth) instead of the main target. Without a component drawing, owo is absent or
 * not asking, and nothing changes. See {@link OwoCubeMap}.
 */
@Mixin(CubeMap.class)
public abstract class CubeMapOutputMixin {
	@ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"), require = 1)
	private void mms_uproot$outputOverride(Args args) {
		OwoCubeMap.Output output = OwoCubeMap.output();
		if (output == null) return;
		GpuTextureView color = output.color();
		args.set(1, color);
		args.set(2, Optional.of(ARGB.vector4fFromARGB32(output.resetColor())));
		args.set(3, output.depth());
	}
}
