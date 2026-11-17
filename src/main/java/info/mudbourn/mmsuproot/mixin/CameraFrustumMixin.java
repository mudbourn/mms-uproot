package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.client.LegacyCullFrustum;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Calls the {@code LevelRenderer.prepareCullFrustum} that {@code LevelRendererFrustumMixin} adds once the camera has built its culling frustum (see {@link LegacyCullFrustum}). */
@Mixin(Camera.class)
public abstract class CameraFrustumMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "prepareCullFrustum", at = @At("TAIL"))
	private void mms_uproot$legacyCullFrustum(Matrix4fc frustumMatrix, Matrix4f projectionMatrix, Vec3 pos, CallbackInfo ci) {
		((LegacyCullFrustum.Entry) this.minecraft.levelRenderer).prepareCullFrustum(new Matrix4f(frustumMatrix), projectionMatrix, pos);
	}
}
