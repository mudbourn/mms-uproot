package info.mudbourn.mmsuproot.shim.surface;

import info.mudbourn.mmsuproot.shim.noise.LegacyNoise;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Stand-in for 1.21.11's surface system, which 26.3 folded into {@code MaterialSystem}. A rule source
 * of a mod reads the seeded random factory and the secondary surface noise from it, and a mod's mixin
 * on the old class can be applied to this one, so these names and types are the old class's. The rest
 * of the old class is not offered.
 */
public class SurfaceSystem {
	private static final Identifier SURFACE = Identifier.withDefaultNamespace("surface");
	public final PositionalRandomFactory noiseRandom;
	private final NormalNoise surfaceSecondaryNoise;

	SurfaceSystem(RandomState random) {
		this.noiseRandom = random.getOrCreateRandomFactory(SURFACE);
		// stands for the secondary surface noise: LegacyNoise samples the noise made for this object, one per system so each world has its own
		this.surfaceSecondaryNoise = LegacyNoise.share(NormalNoise.createParity(-1000, 1.0), random.getOrCreateNoise(Noises.SURFACE_SECONDARY));
	}

	/**
	 * 1.21.11's pillar extension of eroded badlands. A mod's mixin shadows it to call it from an injector on
	 * {@code buildSurface}, which this class does not have; 26.3's {@code MaterialSystem} does the extension itself.
	 */
	protected void erodedBadlandsExtension(BlockColumn column, int x, int z, int height, LevelHeightAccessor level) { }
}
