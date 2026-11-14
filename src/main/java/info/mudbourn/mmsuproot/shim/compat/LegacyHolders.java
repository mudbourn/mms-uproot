package info.mudbourn.mmsuproot.shim.compat;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

/** What a holder that is neither of the game's two kinds answers for the components of the entry it stands for. */
public final class LegacyHolders {
	private LegacyHolders() { }

	/** The registered entry the holder stands for, or null when it has no key or the registry has no such entry yet. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Holder.Reference<?> registered(Holder<?> holder) {
		Optional<? extends ResourceKey<?>> key = holder.unwrapKey();
		if (key.isEmpty()) return null;
		Registry registry = BuiltInRegistries.REGISTRY.getValue((ResourceKey) key.get().registryKey());
		if (registry == null) return null;
		Optional<Holder.Reference<?>> found = registry.get((ResourceKey) key.get());
		return found.orElse(null);
	}

	/** {@code Holder.components()} of the registered entry, nothing before it is registered. */
	public static DataComponentMap components(Holder<?> holder) {
		Holder.Reference<?> entry = registered(holder);
		return entry == null || !entry.areComponentsBound() ? DataComponentMap.EMPTY : entry.components();
	}

	/** {@code Holder.areComponentsBound()} of the registered entry. */
	public static boolean areComponentsBound(Holder<?> holder) {
		Holder.Reference<?> entry = registered(holder);
		return entry != null && entry.areComponentsBound();
	}
}
