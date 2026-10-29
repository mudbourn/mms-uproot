package info.mudbourn.mmsuproot.shim.compat;

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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Argument converters for constructors that changed shape in 26.3. Each takes the 1.21.11
 * arguments and returns the 26.3 ones, in order, for the bridge to push in their place.
 */
public final class CtorAdapters {
	private static final ResourceKey<Feature> UNUSED_TREE = ResourceKey.create(Registries.FEATURE, Identifier.fromNamespaceAndPath("uproot", "unused_tree"));

	private CtorAdapters() { }

	/** {@code new ChunkPos(BlockPos)} became the factory {@code ChunkPos.containing}; the record's own constructor takes the chunk coordinates. */
	public static Object[] chunkPos(BlockPos pos) {
		return new Object[] {pos.getX() >> 4, pos.getZ() >> 4};
	}

	/** {@code StemBlock}: two support tags were added, filled with the pumpkin stem ones. */
	public static Object[] stemBlock(ResourceKey<Block> fruit, ResourceKey<Block> attachedStem, ResourceKey<Item> seed, BlockBehaviour.Properties properties) {
		return new Object[] {fruit, attachedStem, seed, BlockTags.SUPPORTS_PUMPKIN_STEM, BlockTags.SUPPORTS_PUMPKIN_STEM_FRUIT, properties};
	}

	/**
	 * {@code TreeGrower}: 1.21.11 took six optional feature keys and a mega chance; 26.3 takes three
	 * weighted lists and the key of the smallest tree. A subclass supplies its trees by overriding
	 * the getters, so the lists are empty and the key is a placeholder that is never read.
	 */
	public static Object[] treeGrower(String name, float megaChance, Optional<?> a, Optional<?> b, Optional<?> c, Optional<?> d, Optional<?> e, Optional<?> f) {
		return new Object[] {name, WeightedList.of(), WeightedList.of(), WeightedList.of(), UNUSED_TREE};
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

	/** {@code MapDecorationType}: the map colour and exploration flag were dropped. */
	public static Object[] mapDecorationType(Identifier assetId, boolean showOnItemFrame, int mapColor, boolean explorationMapElement, boolean trackCount) {
		return new Object[] {assetId, showOnItemFrame, trackCount};
	}

	/** {@code VillagerProfession}: gained a mutable map of trade sets per level, which Uproot fills in later. */
	public static Object[] villagerProfession(Component name, Predicate<Holder<PoiType>> heldJobSite, Predicate<Holder<PoiType>> acquirableJobSite,
		ImmutableSet<Item> requestedItems, ImmutableSet<Block> secondaryPoi, SoundEvent workSound) {
		return new Object[] {name, heldJobSite, acquirableJobSite, requestedItems, secondaryPoi, workSound, new Int2ObjectOpenHashMap<>()};
	}
}
