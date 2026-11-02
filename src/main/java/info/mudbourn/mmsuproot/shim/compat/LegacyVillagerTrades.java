package info.mudbourn.mmsuproot.shim.compat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import info.mudbourn.mmsuproot.data.gen.Trades;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;

/**
 * 1.21.11's {@code VillagerTrades} listings. 26.3 trades are {@code villager_trade} registry
 * entries, so each listing keeps its constructor arguments and describes itself through the
 * {@link Trades} factories, which write the data. A listing is never asked for a live offer.
 *
 * <p>The listings whose offer is fixed are shimmed here. The ones whose {@code getOffer} rolls the
 * result at trade time are not, because they have no fixed data form; each needs a {@code given_item_modifier}
 * in the entry (vanilla 26.3 converts them so): {@code EnchantedItemForEmeralds} and {@code EnchantBookForEmeralds}
 * ({@code enchant_with_levels} / {@code enchant_randomly}, with {@code double_trade_price_enchantments} and a
 * {@code filtered} fallback), {@code TippedArrowForItemsAndEmeralds} ({@code set_random_potion}), and
 * {@code DyedArmorForEmeralds} (random dyes, which has no vanilla modifier). The enchantment-provider
 * constructors of {@link ItemsForEmeralds} and {@link ItemsAndEmeraldsToItems} likewise need an
 * {@code enchant_with_levels}/provider modifier and are left out. {@code FailureItemListing} has a private
 * constructor and makes no offer, so a mod cannot build one.
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
	 * Sells {@code numberOfItems} of a stack for some emeralds. 1.21.11 kept the whole stack, with its data components, and set only its
	 * count to {@code numberOfItems} when it offered it; the constructors that take a block or an item build a plain stack, as they did.
	 */
	public record ItemsForEmeralds(ItemStack stack, int emeraldCost, int numberOfItems, int maxUses, int villagerXp, float priceMultiplier) implements ItemListing {
		public ItemsForEmeralds {
			stack = stack.copy();
		}

		public ItemsForEmeralds(Item item, int emeraldCost, int numberOfItems, int villagerXp) {
			this(new ItemStack(item), emeraldCost, numberOfItems, 12, villagerXp, 0.05F);
		}

		public ItemsForEmeralds(Item item, int emeraldCost, int numberOfItems, int maxUses, int villagerXp) {
			this(new ItemStack(item), emeraldCost, numberOfItems, maxUses, villagerXp, 0.05F);
		}

		public ItemsForEmeralds(Block block, int emeraldCost, int numberOfItems, int maxUses, int villagerXp) {
			this(new ItemStack(block), emeraldCost, numberOfItems, maxUses, villagerXp, 0.05F);
		}

		public ItemsForEmeralds(ItemStack stack, int emeraldCost, int numberOfItems, int maxUses, int villagerXp) {
			this(stack, emeraldCost, numberOfItems, maxUses, villagerXp, 0.05F);
		}

		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			return null;
		}

		@Override
		public List<Map<String, Object>> entries() {
			return Trades.itemsForEmeralds(this.stack, this.emeraldCost, this.numberOfItems, this.maxUses, this.villagerXp, this.priceMultiplier).entries();
		}
	}

	/**
	 * Buys {@code fromCount} of an item together with emeralds for {@code toCount} of another, kept with its components. The constructor
	 * taking an {@code Item} for the given item is 1.21.11's; the enchantment-provider form is not shimmed (see the class note).
	 */
	public record ItemsAndEmeraldsToItems(ItemLike fromItem, int fromCount, int emeraldCost, ItemStack toItem, int toCount, int maxUses,
			int villagerXp, float priceMultiplier) implements ItemListing {
		public ItemsAndEmeraldsToItems {
			toItem = toItem.copy();
		}

		public ItemsAndEmeraldsToItems(ItemLike fromItem, int fromCount, int emeraldCost, Item toItem, int toCount, int maxUses, int villagerXp,
				float priceMultiplier) {
			this(fromItem, fromCount, emeraldCost, new ItemStack(toItem), toCount, maxUses, villagerXp, priceMultiplier);
		}

		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			return null;
		}

		@Override
		public List<Map<String, Object>> entries() {
			return Trades.itemsAndEmeraldsToItems(this.fromItem, this.fromCount, this.emeraldCost, this.toItem, this.toCount, this.maxUses,
					this.villagerXp, this.priceMultiplier).entries();
		}
	}

	/** Sells a suspicious stew carrying fixed effects for one emerald, as 1.21.11 set the effects component straight onto the stew. */
	public record SuspiciousStewForEmerald(Holder<MobEffect> effect, int duration, int villagerXp, float priceMultiplier) implements ItemListing {
		public SuspiciousStewForEmerald(Holder<MobEffect> effect, int duration, int villagerXp) {
			this(effect, duration, villagerXp, 0.05F);
		}

		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			return null;
		}

		@Override
		public List<Map<String, Object>> entries() {
			ItemStack stew = new ItemStack(Items.SUSPICIOUS_STEW);
			stew.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(List.of(new SuspiciousStewEffects.Entry(this.effect, this.duration))));
			return Trades.itemsForEmeralds(stew, 1, 1, 12, this.villagerXp, this.priceMultiplier).entries();
		}
	}

	/** Offers a listing only to given villager types. A villager-variant merchant predicate restricts each wrapped listing. */
	public record TypeSpecificTrade(Map<ResourceKey<VillagerType>, ItemListing> trades) implements ItemListing {
		@SafeVarargs
		public static TypeSpecificTrade oneTradeInBiomes(ItemListing listing, ResourceKey<VillagerType>... types) {
			Map<ResourceKey<VillagerType>, ItemListing> map = new LinkedHashMap<>();
			for (ResourceKey<VillagerType> type : types) map.put(type, listing);
			return new TypeSpecificTrade(map);
		}

		@Override
		public MerchantOffer getOffer(Entity trader, RandomSource random) {
			return null;
		}

		@Override
		public List<Map<String, Object>> entries() {
			// the types that share a listing collapse into one entry, as 1.21.11 offered the one trade to each of them
			Map<ItemListing, List<Identifier>> byListing = new LinkedHashMap<>();
			this.trades.forEach((type, listing) -> byListing.computeIfAbsent(listing, k -> new ArrayList<>()).add(type.identifier()));
			List<Trades.TypedTrade> typed = new ArrayList<>();
			byListing.forEach((listing, ids) -> typed.add(new Trades.TypedTrade(ids, listing)));
			return Trades.typeSpecific(typed).entries();
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
