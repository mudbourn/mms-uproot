package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.data.gen.Trades;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11 built the wandering trader's offers from {@code VillagerTrades.WANDERING_TRADER_TRADES}, a list of
 * {@code (ItemListing[], count)} pools each rolled on its own, and Fabric let a mod add its own pools to that list.
 * 26.3 dropped the list: {@code updateTrades} rolls exactly the three vanilla trade sets (buying, uncommon, common),
 * so a mod's pool, merged into one of those, only competes for that set's fixed slots and shows up at random.
 * Uproot keeps each modded pool as a trade set of its own (see {@link Trades}); this rolls those after the vanilla
 * three, with each pool's own count, so every modded pool gets its own picks again.
 *
 * <p>{@code getOffers} and {@code addOffersFromTradeSet} are declared on {@code AbstractVillager}, not on the trader,
 * so the mixin extends that class to call them instead of shadowing them.
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderTradesMixin extends AbstractVillager {
	private WanderingTraderTradesMixin(EntityType<? extends AbstractVillager> type, Level level) {
		super(type, level);
	}

	@Inject(method = "updateTrades", at = @At("TAIL"))
	private void mms_uproot$addModdedPools(ServerLevel level, CallbackInfo ci) {
		MerchantOffers offers = getOffers();
		for (ResourceKey<TradeSet> set : Trades.wanderingExtraSets()) addOffersFromTradeSet(level, offers, set);
	}
}
