package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.core.Holder;
import net.minecraft.world.attribute.modifier.ColorModifier;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Constants of the game whose declared type changed in 26.3 although the value is the same kind of object: a field read names the type in its
 * descriptor, so the old read cannot find the new field. The values are the game's own.
 */
public final class LegacyFields {
	private LegacyFields() { }

	/** {@code Potions.AWKWARD}: a holder reference in 26.2, a plain holder in 26.3 (the game registers it as a reference). */
	public static final Holder.Reference<Potion> AWKWARD = reference(Potions.AWKWARD);

	/** {@code Potions.WATER}, likewise. */
	public static final Holder.Reference<Potion> WATER = reference(Potions.WATER);

	/** {@code ColorModifier.MULTIPLY_RGB}: typed as the colour modifier it implements; 26.3 declares the narrower RGB modifier. */
	public static final ColorModifier<?, ?> MULTIPLY_RGB = ColorModifier.MULTIPLY_RGB;

	private static <T> Holder.Reference<T> reference(Holder<T> holder) {
		if (holder instanceof Holder.Reference<T> reference) return reference;
		throw new IllegalStateException("26.3 registers its potions as holder references, but " + holder + " is not one");
	}
}
