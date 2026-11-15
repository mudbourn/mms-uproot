package info.mudbourn.mmsuproot.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;

import info.mudbourn.mmsuproot.shim.client.LegacyRender;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 held the picture-in-picture render's targets in {@code RenderSystem.outputColorTextureOverride} and
 * {@code outputDepthTextureOverride} while it drew; 26.3 passes them straight to the render pass. This sets the two
 * fields of {@link LegacyRender} around the draw, for the mods that read the old ones.
 */
@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {
	@Shadow
	private GpuTextureView textureView;
	@Shadow
	private GpuTextureView depthTextureView;

	@Inject(method = "prepare", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/pip/PictureInPictureRenderer;renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
	private void mms_uproot$legacyOutputBefore(CallbackInfo ci) {
		LegacyRender.outputColorTextureOverride = textureView;
		LegacyRender.outputDepthTextureOverride = depthTextureView;
	}

	@Inject(method = "prepare", at = @At("RETURN"))
	private void mms_uproot$legacyOutputAfter(CallbackInfo ci) {
		LegacyRender.outputColorTextureOverride = null;
		LegacyRender.outputDepthTextureOverride = null;
	}
}
