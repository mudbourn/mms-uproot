package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** 1.21.11's settings of the simple random selector; the field is public as it was. */
public class SimpleRandomFeatureConfiguration implements FeatureConfiguration {
	public final HolderSet<PlacedFeature> features;

	public SimpleRandomFeatureConfiguration(HolderSet<PlacedFeature> features) {
		this.features = features;
	}

	public HolderSet<PlacedFeature> features() {
		return features;
	}
}
