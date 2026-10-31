package info.mudbourn.mmsuproot.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11 villagers had a delayed level-up: a timer counted down in {@code customServerAiStep}, and at zero the villager
 * levelled up (if flagged) and got regeneration. 26.3 does both at once in {@code rewardTradeXp} and dropped the two fields.
 * Mob AI Tweaks sets the same two fields when a villager finishes a special service (renames.txt makes them fields of its own
 * mixin, which Mixin merges into {@code Villager}); this runs the countdown for them. The fields are declared here too, so
 * whichever mixin is applied first adds them, and nothing else sets them: for a game without that mod this does nothing.
 */
@Mixin(Villager.class)
public abstract class VillagerLevelUpMixin {
	private int updateMerchantTimer;
	private boolean increaseProfessionLevelOnUpdate;

	@Shadow
	private void increaseMerchantCareer(ServerLevel level) { }

	@Inject(method = "customServerAiStep", at = @At("HEAD"))
	private void mms_uproot$delayedLevelUp(ServerLevel level, CallbackInfo ci) {
		if (updateMerchantTimer <= 0) return;
		if (--updateMerchantTimer == 0) {
			if (increaseProfessionLevelOnUpdate) {
				increaseMerchantCareer(level);
				increaseProfessionLevelOnUpdate = false;
			}
			((Villager) (Object) this).addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
		}
	}
}
