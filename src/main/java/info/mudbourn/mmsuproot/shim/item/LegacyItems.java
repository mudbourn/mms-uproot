package info.mudbourn.mmsuproot.shim.item;

import java.lang.reflect.Constructor;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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

	/** {@code DyeItem.byColor(DyeColor)}: dyes are a table of items by colour now. */
	public static DyeItem dyeByColor(DyeColor color) {
		return (DyeItem) Items.DYE.pick(color);
	}

	/** {@code DyeItem.getDyeColor()}: the colour is not stored in the item; it is the one whose dye this is. */
	public static DyeColor dyeColor(DyeItem item) {
		for (DyeColor color : DyeColor.values()) if (Items.DYE.pick(color) == item) return color;
		return DyeColor.WHITE;
	}

	/** {@code ShulkerBoxBlock.getColoredItemStack(DyeColor)}: null is the undyed box. */
	public static ItemStack shulkerBoxStack(DyeColor color) {
		return new ItemStack(color == null ? Items.SHULKER_BOX : Items.DYED_SHULKER_BOX.pick(color));
	}

	/** Constants {@code Items} had for each banner colour, now the entries of {@code Items.BANNER}. */
	public static final class Banners {
		public static final Item CYAN_BANNER = Items.BANNER.cyan();

		private Banners() { }
	}
}
