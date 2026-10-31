package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.entity.LegacyEntities;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The swing of a living entity as 1.21.11 mods drive it: they override {@code updateSwingTime()}, which nothing calls now, and
 * set the swing progress in {@code attackAnim}, which 26.3 does not have. See {@link LegacyEntities#tickSwing}.
 */
@Mixin(LivingEntity.class)
public abstract class LegacySwingMixin {
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
