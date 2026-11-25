package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.world.level.levelgen.feature.featuresize.FeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;

/** 1.21.11's settings of the tree feature. Only what a mod passes in is kept; the game reads the mod's JSON. */
public class TreeConfiguration implements FeatureConfiguration {
	public final BlockStateProvider trunkProvider;
	public final TrunkPlacer trunkPlacer;
	public final BlockStateProvider foliageProvider;
	public final FoliagePlacer foliagePlacer;
	public final FeatureSize minimumSize;
	public final BlockStateProvider dirtProvider;

	protected TreeConfiguration(BlockStateProvider trunkProvider, TrunkPlacer trunkPlacer, BlockStateProvider foliageProvider, FoliagePlacer foliagePlacer, FeatureSize minimumSize, BlockStateProvider dirtProvider) {
		this.trunkProvider = trunkProvider;
		this.trunkPlacer = trunkPlacer;
		this.foliageProvider = foliageProvider;
		this.foliagePlacer = foliagePlacer;
		this.minimumSize = minimumSize;
		this.dirtProvider = dirtProvider;
	}

	public static class TreeConfigurationBuilder {
		private final BlockStateProvider trunkProvider;
		private final TrunkPlacer trunkPlacer;
		private final BlockStateProvider foliageProvider;
		private final FoliagePlacer foliagePlacer;
		private final FeatureSize minimumSize;
		private final BlockStateProvider dirtProvider;

		public TreeConfigurationBuilder(BlockStateProvider trunkProvider, TrunkPlacer trunkPlacer, BlockStateProvider foliageProvider, FoliagePlacer foliagePlacer, FeatureSize minimumSize, BlockStateProvider dirtProvider) {
			this.trunkProvider = trunkProvider;
			this.trunkPlacer = trunkPlacer;
			this.foliageProvider = foliageProvider;
			this.foliagePlacer = foliagePlacer;
			this.minimumSize = minimumSize;
			this.dirtProvider = dirtProvider;
		}

		public TreeConfiguration build() {
			return new TreeConfiguration(trunkProvider, trunkPlacer, foliageProvider, foliagePlacer, minimumSize, dirtProvider);
		}
	}
}
