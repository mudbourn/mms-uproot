package info.mudbourn.mmsuproot.data.gen;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;

/**
 * Furnace fuel. 1.21.11 mods added burn times to {@code FuelValues.Builder} in Fabric's build
 * event; in 26.3 an item burns when it carries the cooking fuel component, whose burn time is a
 * {@code context_int_provider} entry. Each distinct time gets one generated provider, shaped like
 * vanilla's (the time divided by the furnace's speed), and the component is set through Fabric's
 * default component event.
 */
public final class Fuel {
	private static final Map<ItemLike, Integer> FUEL = new LinkedHashMap<>();
	private static final java.util.Set<ItemLike> REMOVED = new java.util.LinkedHashSet<>();
	private static final Map<TagKey<Item>, Integer> TAGS = new LinkedHashMap<>();
	private static final Set<TagKey<Item>> REMOVED_TAGS = new LinkedHashSet<>();

	private Fuel() { }

	/** {@code FuelValues.Builder.add(ItemLike item, int burnTicks)Lnet/minecraft/world/level/block/entity/FuelValues$Builder;} */
	public static void add(ItemLike item, int ticks) {
		synchronized (FUEL) {
			REMOVED.remove(item);
			FUEL.put(item, ticks);
		}
	}

	/** {@code FuelValues.Builder.remove(ItemLike)}: take the item out of the furnace's fuel again. */
	public static void remove(ItemLike item) {
		synchronized (FUEL) {
			FUEL.remove(item);
			REMOVED.add(item);
		}
	}

	/** {@code FuelValues.Builder.add(TagKey<Item>, Integer)}: every item in the tag, found when the components are built. */
	public static void add(TagKey<Item> tag, int ticks) {
		synchronized (FUEL) {
			REMOVED_TAGS.remove(tag);
			TAGS.put(tag, ticks);
		}
	}

	/** {@code FuelValues.Builder.remove(TagKey<Item>)}. */
	public static void remove(TagKey<Item> tag) {
		synchronized (FUEL) {
			TAGS.remove(tag);
			REMOVED_TAGS.add(tag);
		}
	}

	private static boolean in(HolderLookup.Provider provider, TagKey<Item> tag, Item item) {
		return provider.lookupOrThrow(Registries.ITEM).get(tag).map(set -> set.contains(item.builtInRegistryHolder())).orElse(false);
	}

	static void apply(DefaultItemComponentEvents.ModifyContext context) {
		synchronized (FUEL) {
			// a tag is read through the lookup the components are built with, the one place its items are known; items named one by one come after
			if (!TAGS.isEmpty() || !REMOVED_TAGS.isEmpty()) {
				context.modify(item -> true, (builder, provider, item) -> {
					synchronized (FUEL) {
						TAGS.forEach((tag, ticks) -> {
							if (in(provider, tag, item)) builder.set(DataComponents.COOKING_FUEL, new CookingFuel(key(ticks), ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER));
						});
						for (TagKey<Item> tag : REMOVED_TAGS) if (in(provider, tag, item)) builder.set(DataComponents.COOKING_FUEL, null);
					}
				});
			}
			FUEL.forEach((item, ticks) -> context.modify(item.asItem(), builder -> builder.set(DataComponents.COOKING_FUEL,
					new CookingFuel(key(ticks), ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER))));
			for (ItemLike item : REMOVED) context.modify(item.asItem(), builder -> builder.set(DataComponents.COOKING_FUEL, null));
		}
	}

	private static ResourceKey<ContextIntProvider> key(int ticks) {
		return ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, Identifier.fromNamespaceAndPath(Gen.NS, "cooking/time_" + ticks));
	}

	static void files(Map<String, String> out) {
		TreeSet<Integer> times;
		synchronized (FUEL) {
			times = new TreeSet<>(FUEL.values());
			times.addAll(TAGS.values());
		}
		for (int ticks : times) {
			out.put(Gen.path(key(ticks).identifier(), "context_int_provider"), Gen.json(Gen.obj("type", "minecraft:div", "left", ticks,
					"right", Gen.obj("type", "minecraft:conditional", "condition", "minecraft:block/fast_cooking",
							"on_false", "minecraft:cooking/normal_burn_time_reduction_factor",
							"on_true", "minecraft:cooking/fast_burn_time_reduction_factor"))));
		}
	}
}
