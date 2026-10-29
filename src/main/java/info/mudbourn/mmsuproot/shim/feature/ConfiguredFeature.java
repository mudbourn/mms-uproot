package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** 1.21.11's feature paired with its settings. Mods build these for datagen; the game reads their JSON instead. */
public final class ConfiguredFeature<FC extends FeatureConfiguration, F extends LegacyFeature<FC>> {
	private final F feature;
	private final FC config;

	public ConfiguredFeature(F feature, FC config) {
		this.feature = feature;
		this.config = config;
	}

	public F feature() {
		return feature;
	}

	public FC config() {
		return config;
	}

	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		return feature.place(new FeaturePlaceContext<>(level, generator, random, origin, config));
	}
}
