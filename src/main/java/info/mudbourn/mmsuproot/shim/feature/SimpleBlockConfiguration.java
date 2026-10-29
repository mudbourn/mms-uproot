package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/** 1.21.11's settings of the simple block feature. */
public record SimpleBlockConfiguration(BlockStateProvider toPlace) implements FeatureConfiguration { }
