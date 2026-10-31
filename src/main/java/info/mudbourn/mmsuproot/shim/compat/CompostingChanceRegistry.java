package info.mudbourn.mmsuproot.shim.compat;

import info.mudbourn.mmsuproot.data.gen.Compost;
import net.fabricmc.fabric.api.util.Item2ObjectMap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/**
 * Fabric's old compost registry, a map from item to chance. Entries go to {@link Compost}, which
 * sets the compostable component on the items when default components are built.
 */
public interface CompostingChanceRegistry extends Item2ObjectMap<Float> {
	CompostingChanceRegistry INSTANCE = new CompostingChanceRegistry() { };

	@Override
	default Float get(ItemLike item) {
		return Compost.get(item);
	}

	@Override
	default void add(ItemLike item, Float chance) {
		Compost.add(item, chance);
	}

	@Override
	default void remove(ItemLike item) {
		Compost.remove(item);
	}

	@Override
	default void clear(ItemLike item) {
		Compost.remove(item);
	}

	/** A tag is resolved when the item components are built, since its items are not known before the data is loaded. */
	@Override
	default void add(TagKey<Item> tag, Float chance) {
		Compost.add(tag, chance);
	}

	@Override
	default void remove(TagKey<Item> tag) {
		Compost.remove(tag);
	}

	@Override
	default void clear(TagKey<Item> tag) {
		Compost.remove(tag);
	}
}
