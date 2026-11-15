package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Sound constants that 26.3 holds as registry entries where 1.21.11 held the event. */
public final class LegacySounds {
	private LegacySounds() { }

	public static final SoundEvent HOE_TILL = SoundEvents.HOE_TILL.value();
}
