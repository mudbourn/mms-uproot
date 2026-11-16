package info.mudbourn.mmsuproot.shim.entity;

import net.minecraft.world.InteractionHand;

/** A living entity that remembers the hand of its last swing, as 1.21.11's {@code LivingEntity.swingingArm} did; {@code mixin.LegacySwingMixin} implements it. */
public interface SwingingArmHolder {
	/** The hand of the last swing that started, kept after the swing ends; null until the entity has swung once. */
	InteractionHand mms_uproot$lastSwingHand();
}
