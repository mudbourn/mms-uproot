package info.mudbourn.mmsuproot.shim.compat;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import info.mudbourn.mmsuproot.data.gen.Trades;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Fabric's old trade registration. Listings are handed straight to {@link Trades}, which writes
 * them as {@code villager_trade} data and tags. The wandering trader of 26.3 reads three fixed trade sets;
 * listings for the three vanilla pools join those, while a mod's own pool becomes a trade set of its own
 * that {@code WanderingTraderTradesMixin} rolls in addition, keeping the per-pool count Fabric honoured.
 */
public final class TradeOfferHelper {
	private TradeOfferHelper() { }

	public static void registerVillagerOffers(ResourceKey<VillagerProfession> profession, int level, Consumer<List<LegacyVillagerTrades.ItemListing>> factory) {
		registerVillagerOffers(profession, level, (listings, rebalanced) -> factory.accept(listings));
	}

	/** The form that tells the rebalanced trade experiment apart; 26.3 has the one set of trades, so the plain listings are the ones used. */
	public static void registerVillagerOffers(ResourceKey<VillagerProfession> profession, int level, VillagerOffersAdder factory) {
		Objects.requireNonNull(profession, "VillagerProfession may not be null.");
		List<LegacyVillagerTrades.ItemListing> listings = new java.util.ArrayList<>();
		factory.onRegister(listings, false);
		Trades.addVillager(profession.identifier(), level, listings);
	}

	public static synchronized void registerWanderingTraderOffers(Consumer<WanderingTraderOffersBuilder> factory) {
		factory.accept(new WanderingTraderOffersBuilderImpl());
	}

	@FunctionalInterface
	public interface VillagerOffersAdder {
		void onRegister(List<LegacyVillagerTrades.ItemListing> factories, boolean rebalanced);
	}

	/** An interface in Fabric's API, so mods call it with {@code invokeinterface}. */
	public interface WanderingTraderOffersBuilder {
		/** The pool of offers to buy items. */
		Identifier BUY_ITEMS_POOL = Identifier.withDefaultNamespace("buy_items");
		/** The pool of special items on sale. */
		Identifier SELL_SPECIAL_ITEMS_POOL = Identifier.withDefaultNamespace("sell_special_items");
		/** The pool of common items on sale. */
		Identifier SELL_COMMON_ITEMS_POOL = Identifier.withDefaultNamespace("sell_common_items");

		/** Adds a pool of which exactly {@code count} offers are picked. */
		WanderingTraderOffersBuilder pool(Identifier id, int count, LegacyVillagerTrades.ItemListing... factories);

		default WanderingTraderOffersBuilder pool(Identifier id, int count, Collection<? extends LegacyVillagerTrades.ItemListing> factories) {
			return pool(id, count, factories.toArray(LegacyVillagerTrades.ItemListing[]::new));
		}

		/** Adds a pool of its own, of which every offer is made. */
		default WanderingTraderOffersBuilder addAll(Identifier id, Collection<? extends LegacyVillagerTrades.ItemListing> factories) {
			return pool(id, factories.size(), factories);
		}

		default WanderingTraderOffersBuilder addAll(Identifier id, LegacyVillagerTrades.ItemListing... factories) {
			return pool(id, factories.length, factories);
		}

		WanderingTraderOffersBuilder addOffersToPool(Identifier pool, LegacyVillagerTrades.ItemListing... factories);

		default WanderingTraderOffersBuilder addOffersToPool(Identifier pool, Collection<LegacyVillagerTrades.ItemListing> factories) {
			return addOffersToPool(pool, factories.toArray(LegacyVillagerTrades.ItemListing[]::new));
		}
	}

	/** Fabric's builder: the ids of the pools made so far are shared by every call, so an id can be added once. */
	private static final class WanderingTraderOffersBuilderImpl implements WanderingTraderOffersBuilder {
		private static final Set<Identifier> POOLS = new HashSet<>(List.of(BUY_ITEMS_POOL, SELL_SPECIAL_ITEMS_POOL, SELL_COMMON_ITEMS_POOL));

		@Override
		public WanderingTraderOffersBuilder pool(Identifier id, int count, LegacyVillagerTrades.ItemListing... factories) {
			if (factories.length == 0) throw new IllegalArgumentException("cannot add empty pool");
			if (count <= 0) throw new IllegalArgumentException("count must be positive");
			Objects.requireNonNull(id, "id cannot be null");
			if (!POOLS.add(id)) throw new IllegalArgumentException("pool id %s is already registered".formatted(id));
			Trades.addWandering(id, count, Arrays.asList(factories));
			return this;
		}

		@Override
		public WanderingTraderOffersBuilder addOffersToPool(Identifier pool, LegacyVillagerTrades.ItemListing... factories) {
			Trades.addWandering(pool, factories.length, Arrays.asList(factories));
			return this;
		}
	}
}
