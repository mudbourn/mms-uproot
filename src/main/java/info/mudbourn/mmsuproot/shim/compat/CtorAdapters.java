package info.mudbourn.mmsuproot.shim.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.Item;
import info.mudbourn.mmsuproot.shim.noise.NoiseParameters;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Argument converters for constructors that changed shape in 26.3. Each takes the 1.21.11
 * arguments and returns the 26.3 ones, in order, for the bridge to push in their place.
 */
public final class CtorAdapters {
	private static final ResourceKey<Feature> UNUSED_TREE = ResourceKey.create(Registries.FEATURE, Identifier.fromNamespaceAndPath("uproot", "unused_tree"));

	/** The weight a certain key would get; a chance becomes its share of this. */
	private static final int WEIGHT_SCALE = 100000;

	private CtorAdapters() { }

	/** {@code new ChunkPos(BlockPos)} became the factory {@code ChunkPos.containing}; the record's own constructor takes the chunk coordinates. */
	public static Object[] chunkPos(BlockPos pos) {
		return new Object[] {pos.getX() >> 4, pos.getZ() >> 4};
	}

	/** {@code new SavedDataType(name, constructor, codec, fixType)}: saved data is named by an identifier now; a bare name is one of the game's own. */
	public static Object[] savedDataType(String name, java.util.function.Supplier<?> constructor, com.mojang.serialization.Codec<?> codec, net.minecraft.util.datafix.DataFixTypes fixType) {
		return new Object[] {Identifier.parse(name.contains(":") ? name : "minecraft:" + name), constructor, codec, fixType};
	}

	/** {@code StemBlock}: two support tags were added, filled with the pumpkin stem ones. */
	public static Object[] stemBlock(ResourceKey<Block> fruit, ResourceKey<Block> attachedStem, ResourceKey<Item> seed, BlockBehaviour.Properties properties) {
		return new Object[] {fruit, attachedStem, seed, BlockTags.SUPPORTS_PUMPKIN_STEM, BlockTags.SUPPORTS_PUMPKIN_STEM_FRUIT, properties};
	}

	/**
	 * {@code TreeGrower}: 1.21.11 took six optional feature keys and a chance for the secondary ones; 26.3 takes three
	 * weighted lists and the key of the smallest tree. The lists give each key the chance 1.21.11's
	 * {@code getConfiguredFeature} and {@code getConfiguredMegaFeature} gave it: the secondary tree at {@code megaChance}
	 * (the same chance for the mega tree, and for flowers), the plain one otherwise. Where 1.21.11 found no key at all
	 * (a secondary mega tree but no mega tree, say) it fell back to the plain tree, which the lists cannot say, so
	 * that share goes to the keys that are there. The random draws differ, since 26.3 draws one weighted int.
	 */
	public static Object[] treeGrower(String name, float megaChance, Optional<?> mega, Optional<?> secondaryMega, Optional<?> tree, Optional<?> secondaryTree,
			Optional<?> flowers, Optional<?> secondaryFlowers) {
		Map<Object, Double> megaChances = new LinkedHashMap<>();
		// getConfiguredMegaFeature: the secondary mega tree at the chance, else the mega tree
		if (secondaryMega.isPresent()) {
			add(megaChances, secondaryMega.get(), megaChance);
			add(megaChances, mega.orElse(null), 1.0 - megaChance);
		} else {
			add(megaChances, mega.orElse(null), 1.0);
		}
		Map<Object, Double> plain = featureChances(megaChance, false, tree, secondaryTree, flowers, secondaryFlowers);
		Map<Object, Double> withFlowers = featureChances(megaChance, true, tree, secondaryTree, flowers, secondaryFlowers);
		Object shortest = firstPresent(tree, mega, flowers, secondaryTree, secondaryMega, secondaryFlowers);
		return new Object[] {name, weighted(plain), weighted(megaChances), withFlowers.equals(plain) ? WeightedList.of() : weighted(withFlowers), shortest};
	}

	/** {@code getConfiguredFeature(random, hasFlowers)} as the chance of each key; a draw that finds no key is left out. */
	private static Map<Object, Double> featureChances(float chance, boolean hasFlowers, Optional<?> tree, Optional<?> secondaryTree, Optional<?> flowers,
			Optional<?> secondaryFlowers) {
		Object plain = hasFlowers && flowers.isPresent() ? flowers.get() : tree.orElse(null);
		Object secondary = hasFlowers && secondaryFlowers.isPresent() ? secondaryFlowers.get() : (secondaryTree.isPresent() ? secondaryTree.get() : plain);
		Map<Object, Double> chances = new LinkedHashMap<>();
		add(chances, secondary, chance);
		add(chances, plain, 1.0 - chance);
		return chances;
	}

	private static Object firstPresent(Optional<?>... keys) {
		for (Optional<?> key : keys) if (key.isPresent()) return key.get();
		return UNUSED_TREE;
	}

	private static void add(Map<Object, Double> chances, Object key, double chance) {
		if (key != null && chance > 0) chances.merge(key, chance, Double::sum);
	}

	@SuppressWarnings("unchecked")
	private static WeightedList<ResourceKey<Feature>> weighted(Map<Object, Double> chances) {
		if (chances.size() == 1) return WeightedList.of((ResourceKey<Feature>) chances.keySet().iterator().next());
		WeightedList.Builder<ResourceKey<Feature>> list = WeightedList.builder();
		chances.forEach((key, chance) -> list.add((ResourceKey<Feature>) key, Math.max(1, (int) Math.round(chance * WEIGHT_SCALE))));
		return list.build();
	}

	/**
	 * {@code TreeGrower(String, Optional mega, Optional tree, Optional flowerTree)}: the form a mod uses
	 * when it builds a grower directly. The keys go into the weighted lists; the smallest tree is the plain one.
	 */
	@SuppressWarnings("unchecked")
	public static Object[] treeGrowerKeys(String name, Optional<?> mega, Optional<?> tree, Optional<?> flower) {
		Optional<ResourceKey<Feature>> first = (Optional<ResourceKey<Feature>>) tree;
		ResourceKey<Feature> shortest = first.or(() -> (Optional<ResourceKey<Feature>>) mega).or(() -> (Optional<ResourceKey<Feature>>) flower).orElse(UNUSED_TREE);
		return new Object[] {name, list(tree), list(mega), list(flower), shortest};
	}

	@SuppressWarnings("unchecked")
	private static WeightedList<ResourceKey<Feature>> list(Optional<?> key) {
		return key.isPresent() ? WeightedList.of((ResourceKey<Feature>) key.get()) : WeightedList.of();
	}

	/**
	 * {@code MapDecorationType}: the map colour and exploration flag were dropped. 26.3 has nowhere to keep the colour: the type
	 * is only an asset and two flags, and the {@code map_color} item component is gone.
	 */
	public static Object[] mapDecorationType(Identifier assetId, boolean showOnItemFrame, int mapColor, boolean explorationMapElement, boolean trackCount) {
		return new Object[] {assetId, showOnItemFrame, trackCount};
	}

	/** {@code VillagerProfession}: gained a mutable map of trade sets per level, which data.gen.Trades fills with the levels that have trades. */
	public static Object[] villagerProfession(Component name, Predicate<Holder<PoiType>> heldJobSite, Predicate<Holder<PoiType>> acquirableJobSite,
		ImmutableSet<Item> requestedItems, ImmutableSet<Block> secondaryPoi, SoundEvent workSound) {
		return new Object[] {name, heldJobSite, acquirableJobSite, requestedItems, secondaryPoi, workSound, new Int2ObjectOpenHashMap<>()};
	}

	/** {@code NoiseProvider(seed, parameters, scale, states)}: the provider takes the made noise, not its parameters. */
	public static Object[] noiseProvider(long seed, NoiseParameters parameters, float scale, java.util.List<?> states) {
		return new Object[] {seed, parameters.toNoise(), scale, states};
	}

	/** {@code NoiseThresholdProvider(seed, parameters, scale, threshold, highChance, defaultState, low, high)}, likewise. */
	public static Object[] noiseThresholdProvider(long seed, NoiseParameters parameters, float scale, float threshold, float highChance,
			net.minecraft.world.level.block.state.BlockState defaultState, java.util.List<?> low, java.util.List<?> high) {
		return new Object[] {seed, parameters.toNoise(), scale, threshold, highChance, defaultState, low, high};
	}
}
