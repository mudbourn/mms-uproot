package info.mudbourn.mmsuproot.shim.compat;

import info.mudbourn.mmsuproot.data.gen.Fuel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1.21.11's fuel table. 26.3 stores burn time as an item component, so the builder hands what a
 * mod's {@link FuelRegistryEvents#BUILD} callback adds to {@link Fuel}.
 */
public final class FuelValues {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");

	private FuelValues() { }

	public static final class Builder {
		public Builder add(ItemLike item, Integer ticks) {
			Fuel.add(item, ticks);
			return this;
		}

		public Builder add(ItemLike item, int ticks) {
			return this.add(item, Integer.valueOf(ticks));
		}

		/** Tag entries cannot be applied per item before tags are bound, so they are skipped. */
		public Builder add(TagKey<Item> tag, Integer ticks) {
			LOGGER.warn("Fuel for tag {} skipped: tag fuel is not converted", tag.location());
			return this;
		}

		public Builder remove(ItemLike item) {
			Fuel.remove(item);
			return this;
		}
	}
}
