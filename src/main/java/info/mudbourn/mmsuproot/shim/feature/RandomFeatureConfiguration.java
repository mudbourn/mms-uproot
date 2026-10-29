package info.mudbourn.mmsuproot.shim.feature;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** 1.21.11's settings of the random selector. The entries are kept as given. */
public record RandomFeatureConfiguration(List<?> features, Holder<PlacedFeature> defaultFeature) implements FeatureConfiguration { }
