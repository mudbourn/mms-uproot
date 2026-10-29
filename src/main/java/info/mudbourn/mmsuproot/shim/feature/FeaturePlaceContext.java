package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** 1.21.11's bundle of what a feature places with. */
public final class FeaturePlaceContext<FC extends FeatureConfiguration> {
	private final WorldGenLevel level;
	private final ChunkGenerator generator;
	private final RandomSource random;
	private final BlockPos origin;
	private final FC config;

	public FeaturePlaceContext(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin, FC config) {
		this.level = level;
		this.generator = generator;
		this.random = random;
		this.origin = origin;
		this.config = config;
	}

	public WorldGenLevel level() {
		return level;
	}

	public ChunkGenerator chunkGenerator() {
		return generator;
	}

	public RandomSource random() {
		return random;
	}

	public BlockPos origin() {
		return origin;
	}

	public FC config() {
		return config;
	}
}
