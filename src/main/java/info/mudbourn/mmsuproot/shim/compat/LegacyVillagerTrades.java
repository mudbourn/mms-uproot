package info.mudbourn.mmsuproot.shim.compat;

import java.util.List;
import java.util.Map;

import info.mudbourn.mmsuproot.data.gen.Trades;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;

/**
 * 1.21.11's {@code VillagerTrades} listings. 26.3 trades are {@code villager_trade} registry
 * entries, so each listing keeps its constructor arguments and describes itself through the
 * {@link Trades} factories, which write the data. A listing is never asked for a live offer.
 */
public final class LegacyVillagerTrades {
	private LegacyVillagerTrades() { }

	/** {@code VillagerTrades.ItemListing}. A mod's own implementation has no data form and adds nothing. */
	public interface ItemListing extends Trades.Trade {
		MerchantOffer getOffer(Entity trader, RandomSource random);

		@Override
		default List<Map<String, Object>> entries() {
			return List.of();
		}
	}

	/** Buys {@code cost} of an item for one emerald. */
	public record EmeraldForItems(ItemLike item, int cost, int maxUses, int villagerXp, float priceMultiplier) implements ItemListing {
		public EmeraldForItems(ItemLike item, int cost, int maxUses, int villagerXp) {
			this(item, cost, maxUses, villagerXp, 0.05F);
		}

		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			return null;
		}

		@Override
		public List<Map<String, Object>> entries() {
			return Trades.emeraldForItems(this.item, this.cost, this.maxUses, this.villagerXp).entries();
		}
	}

	/** Sells {@code numberOfItems} of an item for some emeralds. */
	public record ItemsForEmeralds(Item item, int emeraldCost, int numberOfItems, int maxUses, int villagerXp, float priceMultiplier) implements ItemListing {
		public ItemsForEmeralds(Item item, int emeraldCost, int numberOfItems, int villagerXp) {
			this(item, emeraldCost, numberOfItems, 12, villagerXp, 0.05F);
		}

		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			return null;
		}

		@Override
		public List<Map<String, Object>> entries() {
			return Trades.itemsForEmeralds(this.item, this.emeraldCost, this.numberOfItems, this.maxUses, this.villagerXp, this.priceMultiplier).entries();
		}
	}

	/** Buys an item that depends on the villager's type; one trade per type in the data. */
	public record EmeraldsForVillagerTypeItem(int cost, int maxUses, int villagerXp, Map<ResourceKey<VillagerType>, Item> trades) implements ItemListing {
		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			return null;
		}

		@Override
		public List<Map<String, Object>> entries() {
			return Trades.emeraldsForVillagerTypeItem(this.cost, this.maxUses, this.villagerXp, this.trades).entries();
		}
	}

	/** Sells a map to a structure. */
	public record TreasureMapForEmeralds(int emeraldCost, TagKey<?> destination, String displayName, Holder<MapDecorationType> destinationType,
		int maxUses, int villagerXp) implements ItemListing {
		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			return null;
		}

		@Override
		public List<Map<String, Object>> entries() {
			return Trades.treasureMapForEmeralds(this.emeraldCost, this.destination, this.displayName, this.destinationType, this.maxUses, this.villagerXp).entries();
		}
	}
}
