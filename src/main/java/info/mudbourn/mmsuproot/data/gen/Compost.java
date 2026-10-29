package info.mudbourn.mmsuproot.data.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;

/**
 * Compost chances. 1.21.11 mods called Fabric's {@code CompostingChanceRegistry} with a chance per
 * item; in 26.3 an item composts when it carries the compostable component, which names a
 * {@code context_int_provider} for the layers it adds. Vanilla's five tiers are reused when the
 * chance matches one; any other chance gets a generated provider of the same shape.
 */
public final class Compost {
	private static final Map<ItemLike, Float> CHANCES = new LinkedHashMap<>();
	private static final int[] TIERS = {30, 50, 65, 85};
	private static final String[] TIER_NAMES = {"low", "low_medium", "medium", "medium_high"};

	private Compost() { }

	/** {@code CompostingChanceRegistry.INSTANCE.add(ItemLike item, Float chance)} (an {@code Item2ObjectMap} method). */
	public static void add(ItemLike item, float chance) {
		synchronized (CHANCES) {
			CHANCES.put(item, chance);
		}
	}

	/** {@code CompostingChanceRegistry.INSTANCE.remove(ItemLike)}. */
	public static void remove(ItemLike item) {
		synchronized (CHANCES) {
			CHANCES.put(item, 0f);
		}
	}

	/** {@code CompostingChanceRegistry.INSTANCE.get(ItemLike)}: what mods registered, null when nothing. */
	public static Float get(ItemLike item) {
		synchronized (CHANCES) {
			return CHANCES.get(item);
		}
	}

	static void apply(DefaultItemComponentEvents.ModifyContext context) {
		synchronized (CHANCES) {
			CHANCES.forEach((item, chance) -> {
				int percent = percent(chance);
				if (percent <= 0) context.modify(item.asItem(), builder -> builder.set(DataComponents.COMPOSTABLE, null));
				else context.modify(item.asItem(), builder -> builder.set(DataComponents.COMPOSTABLE, new Compostable(key(percent))));
			});
		}
	}

	private static int percent(float chance) {
		return Math.max(0, Math.min(100, Math.round(chance * 100)));
	}

	/** Vanilla's provider for the tiers it has, else one of ours. */
	private static ResourceKey<ContextIntProvider> key(int percent) {
		Identifier id;
		if (percent == 100) id = Identifier.withDefaultNamespace("compostable/always_add_one");
		else {
			int tier = -1;
			for (int i = 0; i < TIERS.length; i++) if (TIERS[i] == percent) tier = i;
			id = tier >= 0 ? Identifier.withDefaultNamespace("compostable/" + TIER_NAMES[tier]) : Identifier.fromNamespaceAndPath(Gen.NS, "compostable/" + percent);
		}
		return ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, id);
	}

	static void files(Map<String, String> out) {
		TreeSet<Integer> percents = new TreeSet<>();
		synchronized (CHANCES) {
			for (float chance : CHANCES.values()) percents.add(percent(chance));
		}
		for (int percent : percents) {
			ResourceKey<ContextIntProvider> key = key(percent);
			if (percent <= 0 || !key.identifier().getNamespace().equals(Gen.NS)) continue;
			List<Object> distribution = new ArrayList<>();
			distribution.add(Gen.obj("data", 1, "weight", percent));
			distribution.add(Gen.obj("data", 0, "weight", 100 - percent));
			out.put(Gen.path(key.identifier(), "context_int_provider"), Gen.json(Gen.obj("type", "minecraft:number_dispatcher",
					"cases", List.of(Gen.obj("condition", Gen.obj("type", "minecraft:match_block", "blocks", "minecraft:composter",
							"state", Gen.obj("level", "0")), "value", 1)),
					"default", Gen.obj("type", "minecraft:weighted_list", "distribution", distribution))));
		}
	}
}
