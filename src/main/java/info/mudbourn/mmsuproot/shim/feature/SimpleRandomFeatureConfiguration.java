package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** 1.21.11's settings of the simple random selector. */
public record SimpleRandomFeatureConfiguration(HolderSet<PlacedFeature> features) implements FeatureConfiguration { }
