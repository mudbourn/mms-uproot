package info.mudbourn.mmsuproot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import info.mudbourn.mmsuproot.shim.client.LegacyRender;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.2 held the picture-in-picture render's targets in {@code RenderSystem.outputColorTextureOverride} and
 * {@code outputDepthTextureOverride} while it drew; 26.3 passes them straight to the render pass. This sets the two
 * fields of {@link LegacyRender} around the draw, for the mods that read the old ones, and clears them when the draw throws too.
 */
@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {
	@Shadow
	private GpuTextureView textureView;
	@Shadow
	private GpuTextureView depthTextureView;

	@WrapOperation(method = "prepare", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/pip/PictureInPictureRenderer;renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
	private void mms_uproot$legacyOutput(PictureInPictureRenderer<?> self, PictureInPictureRenderState state, PoseStack pose, SubmitNodeCollector collector, Operation<Void> original) {
		LegacyRender.outputColorTextureOverride = textureView;
		LegacyRender.outputDepthTextureOverride = depthTextureView;
		try {
			original.call(self, state, pose, collector);
		} finally {
			LegacyRender.outputColorTextureOverride = null;
			LegacyRender.outputDepthTextureOverride = null;
		}
	}
}
