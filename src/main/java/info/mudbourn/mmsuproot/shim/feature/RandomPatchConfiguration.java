package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** 1.21.11's settings of the random patch and flower features. */
public record RandomPatchConfiguration(int tries, int xzSpread, int ySpread, Holder<PlacedFeature> feature) implements FeatureConfiguration { }
