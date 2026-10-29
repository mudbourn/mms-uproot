package info.mudbourn.mmsuproot.shim.feature;

/** The 1.21.11 marker interface for a feature's settings. 26.3 has none, so mods built for 1.21.11 are pointed here. */
public interface FeatureConfiguration {
	NoneFeatureConfiguration NONE = NoneFeatureConfiguration.INSTANCE;
}
