package info.mudbourn.mmsuproot.shim.entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The delayed level-up of a villager. 1.21.11 set a timer and a flag when a trade filled the experience bar and
 * levelled the villager up when the timer ran out; 26.3 levels it up in the trade itself, so a mod that sets the flag
 * itself has the level-up done at once.
 */
public final class LegacyVillagers {
	private static MethodHandle increaseCareer;

	private LegacyVillagers() { }

	/** A write to {@code Villager.updateMerchantTimer}: there is no timer any more. */
	public static void updateMerchantTimer(Villager villager, int ticks) { }

	/** A write to {@code Villager.increaseProfessionLevelOnUpdate}: the level-up the flag asked for. */
	public static void increaseProfessionLevelOnUpdate(Villager villager, boolean increase) {
		if (!increase || !(villager.level() instanceof ServerLevel level)) return;
		try {
			if (increaseCareer == null) {
				increaseCareer = MethodHandles.privateLookupIn(Villager.class, MethodHandles.lookup())
						.findVirtual(Villager.class, "increaseMerchantCareer", MethodType.methodType(void.class, ServerLevel.class));
			}
			increaseCareer.invokeExact(villager, level);
		} catch (Throwable t) {
			throw new IllegalStateException("Uproot could not level up a villager", t);
		}
		villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
	}
}
