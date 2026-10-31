package info.mudbourn.mmsuproot.shim.entity;

/** The two fields 1.21.11 had on a villager for its delayed level-up; mixin.VillagerLevelUpMixin implements this on {@code Villager}. */
public interface LegacyVillagerLevelUp {
	void mms_uproot$setMerchantTimer(int ticks);

	void mms_uproot$setLevelUpPending(boolean pending);
}
