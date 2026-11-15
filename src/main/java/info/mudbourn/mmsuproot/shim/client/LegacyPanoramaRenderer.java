package info.mudbourn.mmsuproot.shim.client;

import info.mudbourn.mmsuproot.shim.Approximate;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 1.21.11's {@code PanoramaRenderer}, which drew the title screen's cube map into whatever target was active. 26.3 draws the panorama
 * from the GUI render state in its own pass, so a mod cannot have it drawn into a target of its own.
 */
public final class LegacyPanoramaRenderer {
	private static final LegacyPanoramaRenderer INSTANCE = new LegacyPanoramaRenderer();

	private LegacyPanoramaRenderer() { }

	/** {@code GameRenderer.getPanorama()}. */
	public static LegacyPanoramaRenderer of(net.minecraft.client.renderer.GameRenderer renderer) {
		return INSTANCE;
	}

	/** {@code PanoramaRenderer.render(graphics, width, height, spin)}. */
	@Approximate("the panorama is not drawn: 26.3 draws it from the GUI render state, not into a target a mod picks")
	public void render(GuiGraphicsExtractor graphics, int width, int height, boolean spin) { }
}
