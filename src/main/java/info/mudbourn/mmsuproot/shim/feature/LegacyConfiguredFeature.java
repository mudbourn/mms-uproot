package info.mudbourn.mmsuproot.shim.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A 1.21.11 feature with its settings as a 26.3 {@link Feature}, which is both. Decoded from the old
 * {@code {"type", "config"}} shape by the codec {@link LegacyRegistries} registers for the feature.
 */
public final class LegacyConfiguredFeature<FC extends FeatureConfiguration> implements Feature {
	private final LegacyFeature<FC> feature;
	private final FC config;
	private final MapCodec<LegacyConfiguredFeature<FC>> codec;

	LegacyConfiguredFeature(LegacyFeature<FC> feature, FC config, MapCodec<LegacyConfiguredFeature<FC>> codec) {
		this.feature = feature;
		this.config = config;
		this.codec = codec;
	}

	public FC config() {
		return config;
	}

	@Override
	public MapCodec<? extends Feature> codec() {
		return codec;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		return feature.place(new FeaturePlaceContext<>(level, generator, random, origin, config));
	}
}
