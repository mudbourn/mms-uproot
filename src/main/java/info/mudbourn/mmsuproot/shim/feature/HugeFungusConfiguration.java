package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

/** 1.21.11's settings of the huge fungus feature. */
public record HugeFungusConfiguration(BlockState validBaseState, BlockState stemState, BlockState hatState, BlockState decorState, BlockPredicate replaceableBlocks, boolean planted) implements FeatureConfiguration { }
