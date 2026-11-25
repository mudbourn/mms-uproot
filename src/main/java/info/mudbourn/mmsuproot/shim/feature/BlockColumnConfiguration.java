package info.mudbourn.mmsuproot.shim.feature;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/** 1.21.11's settings of the block column feature (vines and kelp-like growths). */
public record BlockColumnConfiguration(List<Layer> layers, Direction direction, BlockPredicate allowedPlacement, boolean prioritizeTip) implements FeatureConfiguration {
	public static Layer layer(IntProvider height, BlockStateProvider state) {
		return new Layer(height, state);
	}

	public record Layer(IntProvider height, BlockStateProvider state) { }
}
