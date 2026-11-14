package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/** Conditions for hooks written against 1.21.11's thrown potions, which had a method of their own for the water case. */
public final class LegacyPotions {
	private LegacyPotions() { }

	/** Whether the potion that landed is plain water: the case 1.21.11's {@code onHitAsWater} handled and 26.3 handles inside {@code affectEntitiesAround}. */
	public static boolean isWater(ServerLevel level, PotionContents contents) {
		return contents.is(Potions.WATER);
	}
}
