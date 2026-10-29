package info.mudbourn.mmsuproot.shim.surface;

import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * Stand-in for 1.21.11's surface system, which 26.3 folded into {@code MaterialSystem}. A rule source
 * of a mod reads the seeded random factory from it; nothing else of the old class is offered.
 */
public class SurfaceSystem {
	public final PositionalRandomFactory noiseRandom;

	SurfaceSystem(PositionalRandomFactory noiseRandom) {
		this.noiseRandom = noiseRandom;
	}
}
