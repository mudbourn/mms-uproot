package info.mudbourn.mmsuproot.shim.compat;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import info.mudbourn.mmsuproot.data.gen.Trades;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Fabric's old trade registration. Listings are handed straight to {@link Trades}, which writes
 * them as {@code villager_trade} data and tags.
 */
public final class TradeOfferHelper {
	private TradeOfferHelper() { }

	public static void registerVillagerOffers(ResourceKey<VillagerProfession> profession, int level, Consumer<List<LegacyVillagerTrades.ItemListing>> factory) {
		List<LegacyVillagerTrades.ItemListing> listings = new java.util.ArrayList<>();
		factory.accept(listings);
		Trades.addVillager(profession.identifier(), level, listings);
	}

	public static void registerWanderingTraderOffers(Consumer<WanderingTraderOffersBuilder> factory) {
		factory.accept((pool, listings) -> {
			Trades.addWandering(pool, listings);
			return null;
		});
	}

	/** An interface in Fabric's API, so mods call it with {@code invokeinterface}. */
	@FunctionalInterface
	public interface WanderingTraderOffersBuilder {
		WanderingTraderOffersBuilder addAll(Identifier pool, Collection<LegacyVillagerTrades.ItemListing> listings);
	}
}
