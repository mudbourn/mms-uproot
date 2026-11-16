package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.entity.LegacyEntities;
import info.mudbourn.mmsuproot.shim.entity.SwingingArmHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The swing of a living entity as 1.21.11 mods drive it: they override {@code updateSwingTime()}, which nothing calls now, and
 * set the swing progress in {@code attackAnim}, which 26.3 does not have. See {@link LegacyEntities#tickSwing}. It also keeps the hand of the
 * last swing, which 1.21.11's {@code swingingArm} held on after the swing ended and 26.3's current swing does not.
 */
@Mixin(LivingEntity.class)
public abstract class LegacySwingMixin implements SwingingArmHolder {
	@Unique
	private InteractionHand mms_uproot$lastSwingHand;

	@Override
	public InteractionHand mms_uproot$lastSwingHand() {
		return mms_uproot$lastSwingHand;
	}

	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("RETURN"))
	private void mms_uproot$rememberSwingHand(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) mms_uproot$lastSwingHand = hand;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void mms_uproot$tickSwing(CallbackInfo ci) {
		LegacyEntities.tickSwing((LivingEntity) (Object) this);
	}

	@Inject(method = "getSwingAnimation", at = @At("RETURN"), cancellable = true)
	private void mms_uproot$swingOverride(float partialTick, CallbackInfoReturnable<Float> cir) {
		Float own = LegacyEntities.swingOverride((LivingEntity) (Object) this);
		if (own != null) cir.setReturnValue(own);
	}
}
