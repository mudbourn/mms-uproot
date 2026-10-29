package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.world.level.block.state.BlockState;

/** 1.21.11's settings holding one block state. */
public record BlockStateConfiguration(BlockState state) implements FeatureConfiguration { }
