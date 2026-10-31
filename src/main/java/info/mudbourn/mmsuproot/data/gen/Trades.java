package info.mudbourn.mmsuproot.data.gen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
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
	private static final List<Villager> VILLAGER = new ArrayList<>();
	private static final List<Wandering> WANDERING = new ArrayList<>();

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

	/** {@code new VillagerTrades.ItemsForEmeralds(Item item, int emeraldCost, int numberOfItems, int maxUses, int villagerXp, float priceMultiplier)} */
	public static Trade itemsForEmeralds(ItemLike item, int emeraldCost, int count, int maxUses, int xp, float priceMultiplier) {
		return () -> List.of(trade(stack(net.minecraft.world.item.Items.EMERALD, emeraldCost), null, stack(item, count), maxUses, xp, priceMultiplier, null, null));
	}

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
	 * {@code WanderingTraderOffersBuilder.addAll(Identifier pool, Collection<ItemListing>)}, also {@code pool(...)} and
	 * {@code addOffersToPool(...)}. The pool is Fabric's id; 26.3 names the pools buying, common and uncommon.
	 */
	public static void addWandering(Identifier pool, Collection<? extends Trade> trades) {
		String name = pool.getPath().contains("buy") ? "buying" : pool.getPath().contains("special") ? "uncommon" : "common";
		synchronized (WANDERING) {
			for (Trade t : trades) WANDERING.add(new Wandering(name, t));
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

	private static Map<String, Object> villagerType(Identifier type) {
		return Gen.obj("type", "minecraft:entity_properties", "entity", "this",
				"predicate", Gen.obj("minecraft:predicates", Gen.obj("minecraft:villager/variant", type.toString())));
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
