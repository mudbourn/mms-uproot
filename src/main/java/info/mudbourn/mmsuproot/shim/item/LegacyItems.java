package info.mudbourn.mmsuproot.shim.item;

import java.lang.reflect.Constructor;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Item calls whose result needs state that 26.3 only has later than a mod's init. */
public final class LegacyItems {
	private static Constructor<ItemStack> unbound;

	private LegacyItems() { }

	/**
	 * {@code Item.getDefaultInstance()}. 26.3 binds an item's default components after the mods have
	 * initialised, and an {@code ItemStack} reads them when it is made. A mod that only asks the stack
	 * which item it holds gets one with no components until then.
	 */
	public static ItemStack getDefaultInstance(Item item) {
		try {
			return item.getDefaultInstance();
		} catch (NullPointerException e) {
			return withoutComponents(item);
		}
	}

	private static synchronized ItemStack withoutComponents(Item item) {
		try {
			if (unbound == null) {
				unbound = ItemStack.class.getDeclaredConstructor(Holder.class, int.class, PatchedDataComponentMap.class);
				unbound.setAccessible(true);
			}
			return unbound.newInstance(BuiltInRegistries.ITEM.wrapAsHolder(item), 1, new PatchedDataComponentMap(DataComponentMap.EMPTY));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Uproot could not make an item stack before components are bound", e);
		}
	}
}
