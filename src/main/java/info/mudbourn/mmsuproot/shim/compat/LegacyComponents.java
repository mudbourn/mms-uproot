package info.mudbourn.mmsuproot.shim.compat;

import java.util.Optional;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;

/** Data component calls whose shape changed. */
public final class LegacyComponents {
	private LegacyComponents() { }

	/**
	 * {@code DataComponentPatch.get(type)}: null when the patch says nothing about the type, an empty optional when it removes the type,
	 * the value when it sets it. 26.3 reads a patch against the item's prototype components, so this reads what the patch says by itself.
	 */
	public static <T> Optional<? extends T> get(DataComponentPatch patch, DataComponentType<? extends T> type) {
		DataComponentPatch.SplitResult split = patch.split();
		if (split.removed().contains(type)) return Optional.empty();
		T added = split.added().get(type);
		return added == null ? null : Optional.of(added);
	}
}
