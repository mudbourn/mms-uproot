package info.mudbourn.mmsuproot.shim.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/** 1.21.11's settings of a feature that has none. */
public final class NoneFeatureConfiguration implements FeatureConfiguration {
	public static final NoneFeatureConfiguration INSTANCE = new NoneFeatureConfiguration();
	public static final Codec<NoneFeatureConfiguration> CODEC = MapCodec.unit(INSTANCE).codec();

	public NoneFeatureConfiguration() { }
}
