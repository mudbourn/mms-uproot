package info.mudbourn.mmsuproot.shim.compat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;

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

	/**
	 * {@code DataComponentPatch.entrySet()}: one entry per component the patch says something about, holding the value when it sets the
	 * component and an empty optional when it removes it. 26.3 keeps the patch private and offers the same two halves as {@code split()}.
	 */
	public static Set<Map.Entry<DataComponentType<?>, Optional<?>>> entrySet(DataComponentPatch patch) {
		DataComponentPatch.SplitResult split = patch.split();
		Map<DataComponentType<?>, Optional<?>> out = new LinkedHashMap<>();
		for (TypedDataComponent<?> added : split.added()) out.put(added.type(), Optional.of(added.value()));
		for (DataComponentType<?> removed : split.removed()) out.put(removed, Optional.empty());
		return out.entrySet();
	}
}
