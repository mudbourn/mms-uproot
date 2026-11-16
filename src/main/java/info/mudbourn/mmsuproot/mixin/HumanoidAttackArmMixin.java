package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.entity.SwingingArmHolder;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11's humanoid render state had an {@code attackArm} field, set from the entity's {@code swingingArm}: the main arm when that was the main
 * hand, the other arm otherwise. The hand stayed set after a swing ended and was null before the first one, which gave the other arm. 26.3 only
 * keeps the swing going on, so the hand of the last swing is copied from the entity ({@code LegacySwingMixin}) when the state is filled. A rule of
 * renames.txt turns reads of the field into calls of this method.
 */
@Mixin(ArmedEntityRenderState.class)
public abstract class HumanoidAttackArmMixin {
	@Shadow
	public HumanoidArm mainArm;

	@Unique
	private InteractionHand mms_uproot$swingingArm;

	@Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
	private static void mms_uproot$copySwingHand(LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver items, float partialTick, CallbackInfo ci) {
		((HumanoidAttackArmMixin) (Object) state).mms_uproot$swingingArm = ((SwingingArmHolder) entity).mms_uproot$lastSwingHand();
	}

	public HumanoidArm mms_uproot$attackArm() {
		return mms_uproot$swingingArm == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
	}
}
