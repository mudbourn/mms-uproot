package info.mudbourn.mmsuproot.shim.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.mojang.renderpearl.api.textures.GpuTextureView;

/**
 * owo-lib's cube map component draws a cube map into a texture of its own instead of the main target: it names the textures in a static
 * field of its render state ({@code CubeMapElementRenderState.outputOverride}) for as long as it draws, and a hook of its own in
 * {@code CubeMap.render} swaps them into the render pass. 26.3's render pass takes a clear colour as a vector, not an ARGB int, so the
 * hook is rebuilt here (see CubeMapOutputMixin) and reads the field through reflection (Uproot has no build dependency on owo).
 */
public final class OwoCubeMap {
	private static final String STATE = "io.wispforest.owo.ui.renderstate.CubeMapElementRenderState";
	private static volatile MethodHandle field;
	private static volatile MethodHandle color, depth, resetColor;
	private static volatile boolean absent;

	/** The textures the cube map draws into now: colour, depth and the ARGB colour to clear to. */
	public record Output(GpuTextureView color, GpuTextureView depth, int resetColor) { }

	private OwoCubeMap() { }

	/** The output owo asked for, or null when owo is absent or is not drawing a cube map into a texture of its own. */
	public static Output output() {
		if (absent) return null;
		try {
			if (field == null) lookup();
			Object override = field.invoke();
			if (override == null) return null;
			return new Output((GpuTextureView) color.invoke(override), (GpuTextureView) depth.invoke(override), (int) resetColor.invoke(override));
		} catch (ClassNotFoundException | NoClassDefFoundError | NoSuchFieldException | NoSuchMethodException e) {
			absent = true; // owo is not there, or has no such component
			return null;
		} catch (Throwable e) {
			throw new IllegalStateException("cannot read owo's cube map output", e);
		}
	}

	private static synchronized void lookup() throws ReflectiveOperationException {
		if (field != null) return;
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		Class<?> state = Class.forName(STATE, true, OwoCubeMap.class.getClassLoader());
		Class<?> override = Class.forName(STATE + "$OutputOverride", true, OwoCubeMap.class.getClassLoader());
		color = lookup.findVirtual(override, "color", MethodType.methodType(GpuTextureView.class));
		depth = lookup.findVirtual(override, "depth", MethodType.methodType(GpuTextureView.class));
		resetColor = lookup.findVirtual(override, "resetColor", MethodType.methodType(int.class));
		field = lookup.findStaticGetter(state, "outputOverride", override);
	}
}
