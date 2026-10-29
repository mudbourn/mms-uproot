package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedStateProvider;

/** 1.21.11's settings of the disk feature. The provider class is 26.3's, renamed from the old one. */
public record DiskConfiguration(RuleBasedStateProvider stateProvider, BlockPredicate target, IntProvider radius, int halfHeight) implements FeatureConfiguration { }
