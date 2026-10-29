package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.util.valueproviders.IntProvider;

/** 1.21.11's settings of the basalt columns feature. */
public record ColumnFeatureConfiguration(IntProvider reach, IntProvider height) implements FeatureConfiguration { }
