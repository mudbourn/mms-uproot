package info.mudbourn.mmsuproot.data.gen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

/**
 * Brewing mixes. 1.21.11 mods called {@code PotionBrewing.Builder} from Fabric's build event; 26.3
 * has a {@code minecraft:brewing} recipe per mix and per bottle type. A mix becomes three recipes
 * (potion, splash, lingering), and a potion that vanilla does not know also gets the gunpowder and
 * dragon's breath recipes that turn its bottle into the other two, as vanilla's builder does.
 */
public final class Brewing {
	private static final List<Mix> MIXES = new ArrayList<>();
	private static final List<Container> CONTAINERS = new ArrayList<>();
	private static final List<Item> BOTTLES_ADDED = new ArrayList<>();
	private static final String[] BOTTLES = {"potion", "splash_potion", "lingering_potion"};

	private Brewing() { }

	private record Mix(Holder<Potion> from, Item reagent, Holder<Potion> to) { }

	private record Container(Item from, Item reagent, Item to) { }

	/** {@code PotionBrewing.Builder.addMix(Holder<Potion> from, Item ingredient, Holder<Potion> to)V} */
	public static void addMix(Holder<Potion> from, Item reagent, Holder<Potion> to) {
		synchronized (MIXES) {
			MIXES.add(new Mix(from, reagent, to));
		}
	}

	/** {@code PotionBrewing.Builder.addStartMix(Item ingredient, Holder<Potion> to)V}: water plus the ingredient. */
	public static void addStartMix(Item reagent, Holder<Potion> to) {
		synchronized (MIXES) {
			MIXES.add(new Mix(net.minecraft.world.item.alchemy.Potions.WATER, reagent, to));
		}
	}

	/** {@code PotionBrewing.Builder.addContainerRecipe(Item from, Item ingredient, Item to)V}: a bottle type that turns into another. */
	public static void addContainerRecipe(Item from, Item reagent, Item to) {
		synchronized (CONTAINERS) {
			CONTAINERS.add(new Container(from, reagent, to));
		}
	}

	/**
	 * {@code PotionBrewing.Builder.addContainer(Item)}: a bottle of a mod that holds potions. It takes every mix that the three bottles of
	 * the game take, which is what 1.21.11's builder did for it.
	 */
	public static void addContainer(Item container) {
		synchronized (BOTTLES_ADDED) {
			BOTTLES_ADDED.add(container);
		}
	}

	static void files(Map<String, String> out) {
		Map<String, Holder<Potion>> known = new LinkedHashMap<>();
		List<Item> added;
		synchronized (BOTTLES_ADDED) {
			added = new ArrayList<>(BOTTLES_ADDED);
		}
		synchronized (MIXES) {
			for (Mix m : MIXES) {
				Identifier from = id(m.from);
				Identifier to = id(m.to);
				known.put(from.toString(), m.from);
				known.put(to.toString(), m.to);
				for (String bottle : BOTTLES) {
					Identifier recipe = Identifier.fromNamespaceAndPath(Gen.NS, "brewing/" + bottle + "_" + Gen.flat(from) + "_" + Gen.flat(to) + "_" + path(m.reagent));
					out.put(Gen.path(recipe, "recipe"), Gen.json(recipe("minecraft:" + bottle, from, Gen.itemId(m.reagent), "minecraft:" + bottle, to)));
				}
				for (Item container : added) {
					String bottle = Gen.itemId(container);
					Identifier recipe = Identifier.fromNamespaceAndPath(Gen.NS, "brewing/" + path(container) + "_" + Gen.flat(from) + "_" + Gen.flat(to) + "_" + path(m.reagent));
					out.put(Gen.path(recipe, "recipe"), Gen.json(recipe(bottle, from, Gen.itemId(m.reagent), bottle, to)));
				}
			}
		}
		for (String potion : known.keySet()) {
			Identifier id = Identifier.parse(potion);
			if (id.getNamespace().equals("minecraft")) continue;
			Identifier splash = Identifier.fromNamespaceAndPath(Gen.NS, "brewing/splash_" + Gen.flat(id));
			out.put(Gen.path(splash, "recipe"), Gen.json(recipe("minecraft:potion", id, "minecraft:gunpowder", "minecraft:splash_potion", id)));
			Identifier lingering = Identifier.fromNamespaceAndPath(Gen.NS, "brewing/lingering_" + Gen.flat(id));
			out.put(Gen.path(lingering, "recipe"), Gen.json(recipe("minecraft:splash_potion", id, "minecraft:dragon_breath", "minecraft:lingering_potion", id)));
		}
		synchronized (CONTAINERS) {
			int n = 0;
			for (Container c : CONTAINERS) {
				// the potion in the bottle stays the same through the change of bottle, so each potion has its own recipe
				for (Holder.Reference<Potion> potion : BuiltInRegistries.POTION.listElements().toList()) {
					Identifier id = potion.key().identifier();
					Identifier recipe = Identifier.fromNamespaceAndPath(Gen.NS, "brewing/container_" + n + "_" + Gen.flat(id));
					out.put(Gen.path(recipe, "recipe"), Gen.json(recipe(Gen.itemId(c.from), id, Gen.itemId(c.reagent), Gen.itemId(c.to), id)));
				}
				n++;
			}
		}
	}

	private static Map<String, Object> recipe(String inBottle, Identifier inPotion, String reagent, String outBottle, Identifier outPotion) {
		return Gen.obj("type", "minecraft:brewing",
				"input", Gen.obj("item", inBottle, "potion_contents", Gen.obj("potions", inPotion.toString())),
				"reagent", Gen.obj("item", reagent),
				"output", Gen.obj("id", outBottle,
						"components", Gen.obj("minecraft:potion_contents", Gen.obj("potion", outPotion.toString()))));
	}

	private static Identifier id(Holder<Potion> potion) {
		return potion.unwrapKey().map(k -> k.identifier()).orElseGet(() -> BuiltInRegistries.POTION.getKey(potion.value()));
	}

	private static String path(Item item) {
		return Gen.flat(BuiltInRegistries.ITEM.getKey(item));
	}
}
