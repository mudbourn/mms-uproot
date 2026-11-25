package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.block.Block;

/** 1.21.11's settings of the multiface growth feature (glow lichen and the like). */
public record MultifaceGrowthConfiguration(Block placeBlock, int searchRange, boolean canPlaceOnFloor, boolean canPlaceOnCeiling, boolean canPlaceOnWall, float chanceOfSpreading, HolderSet<Block> canBePlacedOn) implements FeatureConfiguration { }
