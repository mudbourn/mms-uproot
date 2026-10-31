package info.mudbourn.mmsuproot.shim.compat;

import info.mudbourn.mmsuproot.data.gen.Fuel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/**
 * 1.21.11's fuel table. 26.3 stores burn time as an item component, so the builder hands what a
 * mod's {@link FuelRegistryEvents#BUILD} callback adds to {@link Fuel}.
 */
public final class FuelValues {
	private FuelValues() { }

	public static final class Builder {
		public Builder add(ItemLike item, Integer ticks) {
			Fuel.add(item, ticks);
			return this;
		}

		public Builder add(ItemLike item, int ticks) {
			return this.add(item, Integer.valueOf(ticks));
		}

		/** A tag is resolved when the item components are built, since its items are not known before the data is loaded. */
		public Builder add(TagKey<Item> tag, Integer ticks) {
			Fuel.add(tag, ticks);
			return this;
		}

		/** 1.21.11's descriptor, {@code (TagKey;I)}. */
		public Builder add(TagKey<Item> tag, int ticks) {
			return this.add(tag, Integer.valueOf(ticks));
		}

		public Builder remove(ItemLike item) {
			Fuel.remove(item);
			return this;
		}

		public Builder remove(TagKey<Item> tag) {
			Fuel.remove(tag);
			return this;
		}
	}
}
