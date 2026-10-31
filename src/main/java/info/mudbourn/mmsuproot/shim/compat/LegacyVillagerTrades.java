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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
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

	/** Buys {@code cost} of an item for {@code emeraldAmount} emeralds (one when the constructor has no such argument, as in 1.21.11). */
	public record EmeraldForItems(ItemLike item, int cost, int maxUses, int villagerXp, int emeraldAmount) implements ItemListing {
		public EmeraldForItems(ItemLike item, int cost, int maxUses, int villagerXp) {
			this(item, cost, maxUses, villagerXp, 1);
		}

		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			return null;
		}

		@Override
		public List<Map<String, Object>> entries() {
			List<Map<String, Object>> entries = Trades.emeraldForItems(this.item, this.cost, this.maxUses, this.villagerXp).entries();
			// the factory writes one emerald; the stack it wrote is the trade's own, so the amount goes into it
			if (this.emeraldAmount != 1) {
				for (Map<String, Object> entry : entries) {
					@SuppressWarnings("unchecked")
					Map<String, Object> gives = (Map<String, Object>) entry.get("gives");
					gives.put("count", this.emeraldAmount);
				}
			}
			return entries;
		}
	}

	/**
	 * Sells {@code numberOfItems} of an item for some emeralds. The constructors are 1.21.11's: the ones taking a block
	 * or a stack use the item and drop the stack's components, which {@code villager_trade} data would need to write
	 * out, and the count of a stack is replaced by {@code numberOfItems}, as it was.
	 */
	public record ItemsForEmeralds(Item item, int emeraldCost, int numberOfItems, int maxUses, int villagerXp, float priceMultiplier) implements ItemListing {
		public ItemsForEmeralds(Item item, int emeraldCost, int numberOfItems, int villagerXp) {
			this(item, emeraldCost, numberOfItems, 12, villagerXp, 0.05F);
		}

		public ItemsForEmeralds(Item item, int emeraldCost, int numberOfItems, int maxUses, int villagerXp) {
			this(item, emeraldCost, numberOfItems, maxUses, villagerXp, 0.05F);
		}

		public ItemsForEmeralds(Block block, int emeraldCost, int numberOfItems, int maxUses, int villagerXp) {
			this(block.asItem(), emeraldCost, numberOfItems, maxUses, villagerXp, 0.05F);
		}

		public ItemsForEmeralds(ItemStack stack, int emeraldCost, int numberOfItems, int maxUses, int villagerXp) {
			this(stack.getItem(), emeraldCost, numberOfItems, maxUses, villagerXp, 0.05F);
		}

		public ItemsForEmeralds(ItemStack stack, int emeraldCost, int numberOfItems, int maxUses, int villagerXp, float priceMultiplier) {
			this(stack.getItem(), emeraldCost, numberOfItems, maxUses, villagerXp, priceMultiplier);
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
