package info.mudbourn.mmsuproot.shim.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.culling.Frustum;

/**
 * Keeps {@code LevelRenderer.prepareCullFrustum(Matrix4f, Matrix4f, Vec3)} alive for mods that inject into its return. 26.3 builds the culling
 * frustum in {@code Camera} (a void method), so {@code LevelRendererFrustumMixin} gives {@code LevelRenderer} a method of the old name that
 * answers the camera's frustum, and {@code CameraFrustumMixin} calls it right after the camera has built it.
 */
public final class LegacyCullFrustum {
	private LegacyCullFrustum() { }

	/** What {@code LevelRenderer} gets from {@code LevelRendererFrustumMixin}. */
	public interface Entry {
		Frustum prepareCullFrustum(Matrix4f frustumMatrix, Matrix4f projectionMatrix, Vec3 pos);
	}
}
