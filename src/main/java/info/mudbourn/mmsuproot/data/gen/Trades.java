package info.mudbourn.mmsuproot.data.gen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import info.mudbourn.mmsuproot.shim.compat.LegacyWorld;
import info.mudbourn.mmsuproot.util.Json;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;

/**
 * Villager and wandering trader trades. 1.21.11 mods built listings in code and handed them to
 * Fabric's trade helper; 26.3 reads each trade as a {@code villager_trade} entry and lists them in
 * tags named after the profession and level. The listing constructors become the factories below,
 * which only remember their arguments; the entries and tags are written when the pack is opened.
 * Item ids are resolved then too, so listings may be built before their items are registered.
 */
public final class Trades {
	/** The ids of the three pools the 26.3 wandering trader reads as fixed trade sets; a mod's own pools stay apart. */
	private static final Set<String> VANILLA_POOLS = Set.of("minecraft:buy_items", "minecraft:sell_special_items", "minecraft:sell_common_items");

	private static final List<Villager> VILLAGER = new ArrayList<>();
	private static final List<Wandering> WANDERING = new ArrayList<>();
	private static final Map<Identifier, List<Trade>> WANDERING_POOLS = new LinkedHashMap<>();
	private static final Map<Identifier, Integer> WANDERING_POOL_COUNTS = new LinkedHashMap<>();

	private Trades() { }

	/** One 1.21.11 listing. A listing can stand for several 26.3 entries (one per villager type). */
	public interface Trade {
		List<Map<String, Object>> entries();
	}

	private record Villager(Identifier profession, int level, Trade trade) { }

	private record Wandering(String pool, Trade trade) { }

	// -- the listing constructors ------------------------------------------------------------

	/** {@code new VillagerTrades.EmeraldForItems(ItemLike item, int cost, int maxUses, int villagerXp)} */
	public static Trade emeraldForItems(ItemLike item, int cost, int maxUses, int xp) {
		return () -> List.of(trade(stack(item, cost), null, stack(net.minecraft.world.item.Items.EMERALD, 1), maxUses, xp, 0.05f, null, null));
	}

	/**
	 * {@code new VillagerTrades.ItemsForEmeralds(ItemStack stack, int emeraldCost, int numberOfItems, int maxUses, int villagerXp, float priceMultiplier)}.
	 * The stack keeps its data components, as 1.21.11 stored and offered the whole stack, with only its count set to {@code numberOfItems}.
	 */
	public static Trade itemsForEmeralds(ItemStack stack, int emeraldCost, int count, int maxUses, int xp, float priceMultiplier) {
		return () -> List.of(trade(stack(net.minecraft.world.item.Items.EMERALD, emeraldCost), null, stack(stack, count), maxUses, xp, priceMultiplier, null, null));
	}

	/**
	 * {@code new VillagerTrades.ItemsAndEmeraldsToItems(ItemLike fromItem, int fromCount, int emeraldCost, Item toItem, int toCount, int maxUses,
	 * int villagerXp, float priceMultiplier)}. Emeralds and {@code fromItem} are wanted; {@code toItem} is given with its components kept.
	 */
	public static Trade itemsAndEmeraldsToItems(ItemLike fromItem, int fromCount, int emeraldCost, ItemStack toItem, int toCount, int maxUses, int xp,
			float priceMultiplier) {
		return () -> List.of(trade(stack(net.minecraft.world.item.Items.EMERALD, emeraldCost), stack(fromItem, fromCount), stack(toItem, toCount),
				maxUses, xp, priceMultiplier, null, null));
	}

	/**
	 * {@code VillagerTrades.TypeSpecificTrade}: each wrapped listing is offered only to the villager types it was mapped to. A merchant
	 * predicate on the villager's variant restricts it; the types that share a listing collapse into one entry, as 1.21.11 offered one trade.
	 */
	public static Trade typeSpecific(List<TypedTrade> trades) {
		return () -> {
			List<Map<String, Object>> out = new ArrayList<>();
			for (TypedTrade typed : trades) {
				Object variant = typed.types().size() == 1 ? typed.types().get(0).toString()
						: typed.types().stream().map(Identifier::toString).collect(Collectors.toList());
				Map<String, Object> predicate = villagerVariant(variant);
				for (Map<String, Object> entry : typed.trade().entries()) {
					Map<String, Object> copy = new LinkedHashMap<>(entry);
					copy.putIfAbsent("merchant_predicate", predicate);
					out.add(copy);
				}
			}
			return out;
		};
	}

	/** One {@code TypeSpecificTrade} listing, paired with the villager-type ids it is offered to. */
	public record TypedTrade(List<Identifier> types, Trade trade) { }

	/** {@code new VillagerTrades.EmeraldsForVillagerTypeItem(int cost, int maxUses, int villagerXp, Map<ResourceKey<VillagerType>, Item> trades)} */
	public static Trade emeraldsForVillagerTypeItem(int cost, int maxUses, int xp, Map<ResourceKey<VillagerType>, ? extends ItemLike> items) {
		return () -> {
			List<Map<String, Object>> out = new ArrayList<>();
			items.forEach((type, item) -> out.add(trade(stack(item, 1), null, stack(net.minecraft.world.item.Items.EMERALD, cost), maxUses, xp, 0.05f,
					villagerType(type.identifier()), null)));
			return out;
		};
	}

	/**
	 * {@code new VillagerTrades.TreasureMapForEmeralds(int emeraldCost, TagKey<Structure> destination, String displayName,
	 * Holder<MapDecorationType> destinationType, int maxUses, int villagerXp)}. The structure tag type is left open
	 * because the descriptor only needs the tag's id.
	 */
	public static Trade treasureMapForEmeralds(int emeraldCost, TagKey<?> destination, String displayName, Holder<MapDecorationType> decoration,
			int maxUses, int xp) {
		return () -> {
			Map<String, Object> map = Gen.obj("type", "minecraft:exploration_map", "decoration", decoration.unwrapKey().orElseThrow().identifier().toString(),
					"destination", "#" + destination.location(), "search_radius", 100);
			Map<String, Object> filtered = Gen.obj("type", "minecraft:filtered",
					"item_filter", Gen.obj("predicates", Gen.obj("minecraft:map_id", Gen.obj())),
					"on_fail", Gen.obj("type", "minecraft:discard"));
			Map<String, Object> gives = Gen.obj("id", "minecraft:filled_map", "components", Gen.obj("minecraft:item_name", Gen.obj("translate", displayName)));
			return List.of(trade(stack(net.minecraft.world.item.Items.EMERALD, emeraldCost), stack(net.minecraft.world.item.Items.COMPASS, 1), gives,
					maxUses, xp, 0.2f, null, List.of(map, filtered)));
		};
	}

	// -- registration ------------------------------------------------------------------------

	/**
	 * {@code TradeOfferHelper.registerVillagerOffers(ResourceKey<VillagerProfession> profession, int level, Consumer<List<ItemListing>>)}:
	 * the stand-in runs the consumer and passes what it collected, with {@code profession.identifier()}.
	 */
	public static void addVillager(Identifier profession, int level, Collection<? extends Trade> trades) {
		synchronized (VILLAGER) {
			for (Trade t : trades) VILLAGER.add(new Villager(profession, level, t));
		}
	}

	/**
	 * {@code WanderingTraderOffersBuilder.pool(Identifier pool, int count, ItemListing...)}, also {@code addAll(...)} and
	 * {@code addOffersToPool(...)}. Fabric kept every pool apart, each picking {@code count} of its own offers, so a
	 * modded pool's offers all got a chance to appear. 26.3's wandering trader reads three fixed trade sets, so Uproot
	 * merges the three vanilla pools into them (buying, common, uncommon) but keeps a modded pool as its own trade set,
	 * with its own count, for {@link #wanderingExtraSets()} and {@code WanderingTraderTradesMixin} to roll in addition.
	 */
	public static void addWandering(Identifier pool, int count, Collection<? extends Trade> trades) {
		if (VANILLA_POOLS.contains(pool.toString())) {
			String name = pool.getPath().contains("buy") ? "buying" : pool.getPath().contains("special") ? "uncommon" : "common";
			synchronized (WANDERING) {
				for (Trade t : trades) WANDERING.add(new Wandering(name, t));
			}
			return;
		}
		synchronized (WANDERING_POOLS) {
			WANDERING_POOL_COUNTS.putIfAbsent(pool, count);
			WANDERING_POOLS.computeIfAbsent(pool, k -> new ArrayList<>()).addAll(trades);
		}
	}

	/** The trade set id Uproot gives a mod's wandering-trader pool. */
	private static Identifier wanderingSetId(Identifier pool) {
		return Identifier.fromNamespaceAndPath(Gen.NS, "wandering_trader/" + Gen.flat(pool));
	}

	/**
	 * The trade sets of the modded wandering-trader pools, which {@code WanderingTraderTradesMixin} rolls after the three
	 * vanilla ones. Each keeps its own count, so all of a pool's offers are reached as 1.21.11 reached them.
	 */
	public static List<ResourceKey<TradeSet>> wanderingExtraSets() {
		synchronized (WANDERING_POOLS) {
			List<ResourceKey<TradeSet>> keys = new ArrayList<>();
			for (Identifier pool : WANDERING_POOLS.keySet()) keys.add(ResourceKey.create(Registries.TRADE_SET, wanderingSetId(pool)));
			return keys;
		}
	}

	// -- output ------------------------------------------------------------------------------

	static void files(Map<String, String> out) {
		Map<String, List<String>> tags = new LinkedHashMap<>();
		int n = 0;
		synchronized (VILLAGER) {
			for (Villager v : VILLAGER) {
				if (!v.profession.getNamespace().equals("minecraft")) tradeSet(v.profession, v.level);
				String set = v.profession.getPath() + "/level_" + v.level;
				Identifier tag = Identifier.fromNamespaceAndPath(v.profession.getNamespace(), set);
				for (Map<String, Object> entry : v.trade.entries()) {
					Identifier id = Identifier.fromNamespaceAndPath(Gen.NS, "trade/" + n++);
					out.put(Gen.path(id, "villager_trade"), Gen.json(entry));
					tags.computeIfAbsent(tag.toString(), k -> new ArrayList<>()).add(id.toString());
				}
				if (!v.profession.getNamespace().equals("minecraft")) {
					out.put(Gen.path(tag, "trade_set"), Gen.json(Gen.obj("amount", 2, "random_sequence", Gen.NS + ":trade_set/" + Gen.flat(tag),
							"trades", "#" + tag)));
				}
			}
		}
		synchronized (WANDERING) {
			for (Wandering w : WANDERING) {
				Identifier tag = Identifier.withDefaultNamespace("wandering_trader/" + w.pool);
				for (Map<String, Object> entry : w.trade.entries()) {
					Identifier id = Identifier.fromNamespaceAndPath(Gen.NS, "trade/" + n++);
					out.put(Gen.path(id, "villager_trade"), Gen.json(entry));
					tags.computeIfAbsent(tag.toString(), k -> new ArrayList<>()).add(id.toString());
				}
			}
		}
		synchronized (WANDERING_POOLS) {
			for (Map.Entry<Identifier, List<Trade>> e : WANDERING_POOLS.entrySet()) {
				Identifier set = wanderingSetId(e.getKey());
				for (Trade t : e.getValue()) {
					for (Map<String, Object> entry : t.entries()) {
						Identifier id = Identifier.fromNamespaceAndPath(Gen.NS, "trade/" + n++);
						out.put(Gen.path(id, "villager_trade"), Gen.json(entry));
						tags.computeIfAbsent(set.toString(), k -> new ArrayList<>()).add(id.toString());
					}
				}
				out.put(Gen.path(set, "trade_set"), Gen.json(Gen.obj("amount", WANDERING_POOL_COUNTS.get(e.getKey()),
						"random_sequence", Gen.NS + ":trade_set/" + Gen.flat(set), "trades", "#" + set)));
			}
		}
		tags.forEach((tag, values) -> out.put(Gen.path(Identifier.parse(tag), "tags/villager_trade"), Gen.json(Gen.obj("values", values))));
	}

	/**
	 * A modded profession is built with an empty map of trade sets by level (shim.compat.CtorAdapters), since its id is not known
	 * until it is registered. Each level that has trades gets the set written above, once the profession is in the registry.
	 */
	private static void tradeSet(Identifier profession, int level) {
		VillagerProfession built = BuiltInRegistries.VILLAGER_PROFESSION.getOptional(profession).orElse(null);
		if (built == null) return;
		built.tradeSetsByLevel().putIfAbsent(level, ResourceKey.create(Registries.TRADE_SET,
				Identifier.fromNamespaceAndPath(profession.getNamespace(), profession.getPath() + "/level_" + level)));
	}

	private static Map<String, Object> stack(ItemLike item, int count) {
		Map<String, Object> m = Gen.obj("id", Gen.itemId(item));
		if (count != 1) m.put("count", count);
		return m;
	}

	/** A stack as the item-stack codec writes it, keeping the stack's data components but taking its count from {@code count}. */
	private static Map<String, Object> stack(ItemStack stack, int count) {
		Map<String, Object> m = Gen.obj("id", Gen.itemId(stack.getItem()));
		if (count != 1) m.put("count", count);
		Map<String, Object> components = components(stack);
		if (components != null) m.put("components", components);
		return m;
	}

	/** The stack's non-default components, in the shape the item-stack codec writes under {@code components}, or null when it has none. */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> components(ItemStack stack) {
		DataComponentPatch patch = stack.getComponentsPatch();
		if (patch.isEmpty()) return null;
		RegistryAccess registries = LegacyWorld.registries();
		DynamicOps<JsonElement> ops = registries == null ? JsonOps.INSTANCE : registries.createSerializationContext(JsonOps.INSTANCE);
		JsonElement json = DataComponentPatch.CODEC.encodeStart(ops, patch).getOrThrow();
		return (Map<String, Object>) Json.parse(json.toString());
	}

	private static Map<String, Object> villagerType(Identifier type) {
		return villagerVariant(type.toString());
	}

	/** An {@code entity_properties} merchant predicate on the villager's variant; {@code variant} is a single id string or a list of them. */
	private static Map<String, Object> villagerVariant(Object variant) {
		return Gen.obj("type", "minecraft:entity_properties", "entity", "this",
				"predicate", Gen.obj("minecraft:predicates", Gen.obj("minecraft:villager/variant", variant)));
	}

	private static Map<String, Object> trade(Map<String, Object> wants, Map<String, Object> more, Map<String, Object> gives, int maxUses, int xp,
			float discount, Map<String, Object> merchantPredicate, List<Map<String, Object>> modifier) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("wants", wants);
		if (more != null) m.put("additional_wants", more);
		m.put("gives", gives);
		m.put("max_uses", maxUses);
		m.put("xp", xp);
		m.put("reputation_discount", Gen.clean(discount));
		if (merchantPredicate != null) m.put("merchant_predicate", merchantPredicate);
		if (modifier != null) m.put("given_item_modifier", modifier);
		return m;
	}
}
