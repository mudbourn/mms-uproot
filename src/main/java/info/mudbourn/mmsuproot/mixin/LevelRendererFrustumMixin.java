package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.client.LegacyCullFrustum;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Gives {@code LevelRenderer} the {@code prepareCullFrustum} that 26.3 moved into {@code Camera} (see {@link LegacyCullFrustum}), so a mod
 * injector at its return finds its target and the frustum as the return value. The low priority applies this before the mods' own mixins:
 * Mixin merges a method before it applies the injectors of the mixins that come after.
 */
@Mixin(value = LevelRenderer.class, priority = 100)
public abstract class LevelRendererFrustumMixin implements LegacyCullFrustum.Entry {
	@Override
	public Frustum prepareCullFrustum(Matrix4f frustumMatrix, Matrix4f projectionMatrix, Vec3 pos) {
		return Minecraft.getInstance().gameRenderer.mainCamera().getCullFrustum();
	}
}
