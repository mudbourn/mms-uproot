package info.mudbourn.mmsuproot.mixin;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 1.21.11's humanoid render state had an {@code attackArm} field: the arm of the hand that swings, the main arm for the main hand. 26.3 keeps
 * the swing itself, and this answers the old field from it. A rule of renames.txt turns reads of the field into calls of this method.
 */
@Mixin(HumanoidRenderState.class)
public abstract class HumanoidAttackArmMixin {
	public HumanoidArm mms_uproot$attackArm() {
		HumanoidRenderState state = (HumanoidRenderState) (Object) this;
		LivingEntity.SwingDescription swing = state.currentSwing;
		return swing == null ? state.mainArm : swing.hand().asArm(state.mainArm);
	}
}
