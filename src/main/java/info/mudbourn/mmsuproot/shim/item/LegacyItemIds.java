package info.mudbourn.mmsuproot.shim.item;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * An item built without an id. 1.21.1 made an item first and gave it a name when it was registered; 1.21.2 wants the id in the item's properties,
 * because the item's description, its model and its component initialisers are made from it when the item is. For a class of a mod built in the 1.21.1
 * era, an item whose properties name no id is made under a placeholder (mixin.ItemPropertiesMixin), with its initialiser held back (mixin.ItemMixin),
 * and the call that registers it ({@code Registry.register}, sent here by renames-1211.txt) gives it the real id: the properties take it, the
 * description id follows, and the initialiser is added under it.
 */
public final class LegacyItemIds {
	private LegacyItemIds() { }

	/** The key an item without an id is made under, until it is registered. */
	public static final ResourceKey<Item> PLACEHOLDER = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("mms_uproot", "legacy_item"));

	/** Items made under the placeholder: the properties each was made with. */
	private static final Map<Item, Item.Properties> PENDING = java.util.Collections.synchronizedMap(new WeakHashMap<>());

	/** Called from {@code Item}'s constructor when the properties carry the placeholder: keeps them for {@link #bind}. */
	public static void pending(Item item, Item.Properties properties) {
		PENDING.put(item, properties);
	}

	/**
	 * {@code Registry.register(Registry, Identifier, Object)}: an item made without an id learns the one it is registered under, then registers as it always did.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Object register(Registry registry, Identifier id, Object value) {
		if (registry == BuiltInRegistries.ITEM && value instanceof Item item) bind(item, id);
		return Registry.register(registry, id, value);
	}

	private static void bind(Item item, Identifier id) {
		Item.Properties properties = PENDING.remove(item);
		if (properties == null) return;
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		properties.setId(key);
		item.descriptionId = properties.effectiveDescriptionId();
		DataComponentInitializers initializers = BuiltInRegistries.DATA_COMPONENT_INITIALIZERS;
		initializers.add(key, properties.finalizeInitializer(Component.translatable(item.descriptionId), properties.effectiveModel()));
	}
}
