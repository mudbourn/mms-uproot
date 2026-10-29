package info.mudbourn.mmsuproot.shim.surface;

import net.minecraft.world.level.levelgen.Noises;

/** Stand-in for the helpers of 1.21.11's {@code SurfaceRuleData} that mods call. */
public final class SurfaceRuleData {
	private SurfaceRuleData() { }

	/**
	 * Vanilla 1.21.11 compared the surface noise against the argument divided by 8.25; 26.3's data
	 * carries the divided numbers (0.1212 is 1 / 8.25), so this builds the same threshold.
	 */
	public static SurfaceRules.ConditionSource surfaceNoiseAbove(double level) {
		return SurfaceRules.noiseCondition(Noises.SURFACE, level / 8.25, Double.MAX_VALUE);
	}
}
