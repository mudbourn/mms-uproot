package info.mudbourn.mmsuproot.shim.feature;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** 1.21.11's feature paired with its settings. Mods build these for datagen; the game reads their JSON instead. */
public final class ConfiguredFeature<FC extends FeatureConfiguration, F extends LegacyFeature<FC>> {
	private final F feature;
	private final FC config;
	private final Feature delegate;

	public ConfiguredFeature(F feature, FC config) {
		this.feature = feature;
		this.config = config;
		this.delegate = null;
	}

	private ConfiguredFeature(Feature delegate) {
		this(delegate, null);
	}

	private ConfiguredFeature(Feature delegate, FC config) {
		this.feature = null;
		this.config = config;
		this.delegate = delegate;
	}

	/**
	 * A cast to the old class for a value out of a 26.3 registry, which holds the feature itself (it
	 * carries its settings). The result places through it.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static ConfiguredFeature cast(Object o) {
		if (o instanceof Feature f) return new ConfiguredFeature(f);
		return (ConfiguredFeature) o;
	}

	/**
	 * What a flower or patch of 26.3 looks like to code that expects 1.21.11's configured patch: a random patch
	 * configuration whose placed feature places the feature once.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	public static ConfiguredFeature patchOf(Feature feature) {
		PlacedFeature placed = new PlacedFeature(Holder.direct(feature), List.of());
		return new ConfiguredFeature(feature, new RandomPatchConfiguration(1, 0, 0, Holder.direct(placed)));
	}

	public F feature() {
		return feature;
	}

	public FC config() {
		return config;
	}

	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (delegate != null) return delegate.place(level, generator, random, origin);
		return feature.place(new FeaturePlaceContext<>(level, generator, random, origin, config));
	}
}
