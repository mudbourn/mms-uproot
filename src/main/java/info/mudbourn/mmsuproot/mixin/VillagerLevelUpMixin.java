package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.entity.LegacyVillagerLevelUp;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11 villagers had a delayed level-up: {@code customServerAiStep} counted {@code updateMerchantTimer} down while the
 * villager was not trading, and when it reached zero levelled the villager up (if {@code increaseProfessionLevelOnUpdate} was
 * set) and gave it regeneration for 200 ticks. 26.3 does both at once in {@code rewardTradeXp} and dropped the two fields.
 * Mob AI Tweaks writes the same two fields when a villager finishes a special service; renames.txt sends those writes to
 * LegacyVillagers, which stores them here, and this runs the countdown for them. Without a write nothing happens.
 */
@Mixin(Villager.class)
public abstract class VillagerLevelUpMixin implements LegacyVillagerLevelUp {
	@Unique
	private int mms_uproot$merchantTimer;
	@Unique
	private boolean mms_uproot$levelUpPending;

	@Shadow
	private void increaseMerchantCareer(ServerLevel level) { }

	@Override
	public void mms_uproot$setMerchantTimer(int ticks) {
		mms_uproot$merchantTimer = ticks;
	}

	@Override
	public void mms_uproot$setLevelUpPending(boolean pending) {
		mms_uproot$levelUpPending = pending;
	}

	@Inject(method = "customServerAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V", ordinal = 0, shift = At.Shift.AFTER))
	private void mms_uproot$delayedLevelUp(ServerLevel level, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (self.isTrading() || mms_uproot$merchantTimer <= 0) return;
		if (--mms_uproot$merchantTimer > 0) return;
		if (mms_uproot$levelUpPending) {
			increaseMerchantCareer(level);
			mms_uproot$levelUpPending = false;
		}
		self.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
	}
}
