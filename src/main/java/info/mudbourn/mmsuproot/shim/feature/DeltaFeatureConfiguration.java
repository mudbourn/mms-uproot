package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.block.state.BlockState;

/** 1.21.11's settings of the delta feature. */
public record DeltaFeatureConfiguration(BlockState contents, BlockState rim, IntProvider size, IntProvider rimSize) implements FeatureConfiguration { }
