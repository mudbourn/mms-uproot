package info.mudbourn.mmsuproot.shim.entity;

import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The delayed level-up of a villager. 1.21.11 set a timer and a flag when a trade filled the experience bar and
 * levelled the villager up when the timer ran out; 26.3 levels it up in the trade itself and has neither field, so
 * they are kept per villager by mixin.VillagerLevelUpMixin, which also runs the countdown as 1.21.11 did.
 */
public final class LegacyVillagers {
	private LegacyVillagers() { }

	/** A write to {@code Villager.updateMerchantTimer}. */
	public static void updateMerchantTimer(Villager villager, int ticks) {
		((LegacyVillagerLevelUp) villager).mms_uproot$setMerchantTimer(ticks);
	}

	/** A write to {@code Villager.increaseProfessionLevelOnUpdate}. */
	public static void increaseProfessionLevelOnUpdate(Villager villager, boolean increase) {
		((LegacyVillagerLevelUp) villager).mms_uproot$setLevelUpPending(increase);
	}
}
