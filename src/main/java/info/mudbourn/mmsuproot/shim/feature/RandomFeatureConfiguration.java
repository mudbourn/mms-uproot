package info.mudbourn.mmsuproot.shim.feature;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** 1.21.11's settings of the random selector. The entries are kept as given, and the fields are public as they were. */
public class RandomFeatureConfiguration implements FeatureConfiguration {
	public final List<?> features;
	public final Holder<PlacedFeature> defaultFeature;

	public RandomFeatureConfiguration(List<?> features, Holder<PlacedFeature> defaultFeature) {
		this.features = features;
		this.defaultFeature = defaultFeature;
	}

	public List<?> features() {
		return features;
	}

	public Holder<PlacedFeature> defaultFeature() {
		return defaultFeature;
	}
}
