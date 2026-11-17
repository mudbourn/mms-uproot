package info.mudbourn.mmsuproot.shim.feature;

import java.util.List;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import info.mudbourn.mmsuproot.shim.noise.LegacyNoise;
import info.mudbourn.mmsuproot.shim.noise.NoiseParameters;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderOwner;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.NoiseBasedStateProvider;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.placement.BlockPredicateFilter;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.timeline.Timeline;

/**
 * 1.21.11's world generation helpers whose shape changed in 26.3, for the mods that build settings in Java
 * (see {@link LegacyFeature}). Each body follows the 1.21.11 bytecode of the method it stands for; the
 * constructor adapters return the 26.3 arguments in order, for {@code ctoradapt}.
 */
public final class LegacyWorldgen {
	/** The overworld clock, named by key: 1.21.11 had one day time, which 26.3 keeps in this clock (the registry's holder is found by key). */
	private static final Holder<WorldClock> OVERWORLD_CLOCK = Holder.Reference.createStandAlone(new HolderOwner<>() { }, WorldClocks.OVERWORLD);

	private LegacyWorldgen() { }

	// FeatureUtils: a patch of a feature, as the shim's settings

	/** {@code FeatureUtils.simpleRandomPatchConfiguration}: seven across, three up and down. */
	public static RandomPatchConfiguration simpleRandomPatchConfiguration(int tries, Holder<PlacedFeature> feature) {
		return new RandomPatchConfiguration(tries, 7, 3, feature);
	}

	/** {@code FeatureUtils.simplePatchConfiguration(feature, config)}: 96 tries on air. */
	public static RandomPatchConfiguration simplePatchConfiguration(LegacyFeature<?> feature, FeatureConfiguration config) {
		return simplePatchConfiguration(feature, config, List.of(), 96);
	}

	/** {@code FeatureUtils.simplePatchConfiguration(feature, config, blocks, tries)}. */
	public static RandomPatchConfiguration simplePatchConfiguration(LegacyFeature<?> feature, FeatureConfiguration config, List<Block> allowedOn, int tries) {
		return simpleRandomPatchConfiguration(tries, filtered(feature, config, simplePatchPredicate(allowedOn)));
	}

	private static BlockPredicate simplePatchPredicate(List<Block> allowedOn) {
		if (allowedOn.isEmpty()) return BlockPredicate.ONLY_IN_AIR_PREDICATE;
		return BlockPredicate.allOf(BlockPredicate.ONLY_IN_AIR_PREDICATE, BlockPredicate.matchesBlocks(Direction.DOWN.getUnitVec3i(), allowedOn));
	}

	// PlacementUtils: a placed feature around a feature made in place

	/** {@code PlacementUtils.inlinePlaced(feature, config, modifiers)}. */
	public static Holder<PlacedFeature> inlinePlaced(LegacyFeature<?> feature, FeatureConfiguration config, PlacementModifier... modifiers) {
		return PlacementUtils.inlinePlaced(Holder.direct(configured(feature, config)), modifiers);
	}

	/** {@code PlacementUtils.onlyWhenEmpty(feature, config)}. */
	public static Holder<PlacedFeature> onlyWhenEmpty(LegacyFeature<?> feature, FeatureConfiguration config) {
		return filtered(feature, config, BlockPredicate.ONLY_IN_AIR_PREDICATE);
	}

	private static Holder<PlacedFeature> filtered(LegacyFeature<?> feature, FeatureConfiguration config, BlockPredicate predicate) {
		return inlinePlaced(feature, config, BlockPredicateFilter.forPredicate(predicate));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Feature configured(LegacyFeature feature, FeatureConfiguration config) {
		MapCodec codec = LegacyRegistries.adapter(feature);
		return new LegacyConfiguredFeature(feature, config, codec);
	}

	// VegetationFeatures: a private helper that mods reach through an access widener

	/** {@code VegetationFeatures.grassPatch(provider, tries)}: the provider's block on a patch of empty ground. */
	public static RandomPatchConfiguration grassPatch(BlockStateProvider provider, int tries) {
		return simpleRandomPatchConfiguration(tries, onlyWhenEmpty(LegacyFeature.SIMPLE_BLOCK, new SimpleBlockConfiguration(provider)));
	}

	// Biome spawns: the spawner data counts with an int provider, and the category came off it onto the builder

	/** {@code new SpawnerData(type, min, max)}: the count is a provider now, constant when the two agree (as {@code Builder.addSpawn(type, weight, min, max)} makes it). */
	public static Object[] spawnerData(EntityType<?> type, int minCount, int maxCount) {
		return new Object[] {type, minCount == maxCount ? ConstantInt.of(minCount) : UniformInt.of(minCount, maxCount)};
	}

	/** {@code Builder.addSpawn(category, weight, data)}: 26.3 takes the type and count out of the data and the category apart from it. */
	public static MobSpawnSettings.Builder addSpawn(MobSpawnSettings.Builder builder, MobCategory category, int weight, MobSpawnSettings.SpawnerData data) {
		return builder.addSpawn(data.type(), category, weight, data.count());
	}

	// State providers and tree decorators: a provider that was held directly is held through a holder now

	/**
	 * {@code NoiseBasedStateProvider(seed, parameters, scale)}: the provider takes the made noise. A subclass that samples the provider's own noise
	 * (the field {@code noise}, which is the made noise in 1.21.11) reads it as the description now (a field rule), so the description also
	 * answers {@code getValue} through {@link LegacyNoise}, with the noise 26.3's constructor makes from the same seed.
	 */
	public static Object[] noiseBasedStateProvider(long seed, NoiseParameters parameters, float scale) {
		NormalNoise description = parameters.toNoise();
		LegacyNoise.share(description, description.create(new WorldgenRandom(new LegacyRandomSource(seed))));
		return new Object[] {seed, description, scale};
	}

	/** {@code NoiseBasedStateProvider.noiseCodec}: the same fields, with the noise as the {@link NoiseParameters} a 1.21.11 provider's constructor takes. */
	public static <P extends NoiseBasedStateProvider> Products.P3<RecordCodecBuilder.Mu<P>, Long, NoiseParameters, Float> noiseCodec(RecordCodecBuilder.Instance<P> instance) {
		return instance.group(
				Codec.LONG.fieldOf("seed").forGetter(provider -> provider.seed),
				NormalNoise.DIRECT_CODEC.xmap(NoiseParameters::cast, NoiseParameters::toNoise).fieldOf("noise").forGetter(provider -> NoiseParameters.cast(provider.parameters)),
				ExtraCodecs.POSITIVE_FLOAT.fieldOf("scale").forGetter(provider -> provider.scale));
	}

	/** {@code NoiseBasedStateProvider.getNoiseValue(pos, scale)}, which answered a double. */
	public static double noiseValue(NoiseBasedStateProvider provider, BlockPos pos, double scale) {
		return provider.getNoiseValue(pos, scale);
	}

	/** What a provider that names its {@code type()} answers 26.3's {@code codec()} with: the codec the type holds. */
	public static MapCodec<?> providerCodec(BlockStateProviderType<?> type) {
		return type.codec();
	}

	/** {@code RuleBasedBlockStateProvider(fallback, rules)}. */
	public static Object[] ruleBasedStateProvider(BlockStateProvider fallback, List<?> rules) {
		return new Object[] {Holder.direct(fallback), rules};
	}

	/** {@code RuleBasedBlockStateProvider.Rule(ifTrue, then)}. */
	public static Object[] ruleBasedStateProviderRule(BlockPredicate ifTrue, BlockStateProvider then) {
		return new Object[] {ifTrue, Holder.direct(then)};
	}

	/** {@code AttachedToLogsDecorator(probability, provider, directions)}. */
	public static Object[] attachedToLogsDecorator(float probability, BlockStateProvider provider, List<Direction> directions) {
		return new Object[] {probability, Holder.direct(provider), directions};
	}

	// Timeline: a timeline runs on a clock now

	/** {@code Timeline.builder()}: the old timelines all ran on the overworld's day. */
	public static Timeline.Builder timeline() {
		return Timeline.builder(OVERWORLD_CLOCK);
	}
}
