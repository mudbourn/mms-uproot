package info.mudbourn.mmsuproot.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A cube mob that dies splits into smaller ones, and a split one takes the item the parent held in its off hand. Mob AI
 * Tweaks gives slimes such an item, and did the copy by a wrap of the conversion call in {@code Slime.remove}; 26.3 moved
 * {@code remove} up to {@code AbstractCubeMob}, where a mixin on {@code Slime} cannot reach the call (renames.txt marks that
 * injector optional). Without an item in the off hand, as in the game itself, this changes nothing.
 */
@Mixin(AbstractCubeMob.class)
public abstract class SlimeSplitMixin {
	@SuppressWarnings({"unchecked", "rawtypes"})
	@ModifyArg(method = "remove", index = 3, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/cubemob/AbstractCubeMob;convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;"))
	private ConversionParams.AfterConversion mms_uproot$copyOffhandItem(ConversionParams.AfterConversion after) {
		AbstractCubeMob parent = (AbstractCubeMob) (Object) this;
		ItemStack held = parent.getOffhandItem();
		if (held.isEmpty()) return after;
		return (ConversionParams.AfterConversion<Mob>) child -> {
			after.finalizeConversion(child);
			child.setItemInHand(InteractionHand.OFF_HAND, held.copy());
			child.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
		};
	}
}
