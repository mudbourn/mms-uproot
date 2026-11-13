package info.mudbourn.mmsuproot.probe;

import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;

/** Frostiful and Thermoo (1.21.11 builds): the game rule bridge, and the data they ship loading into the registries of a world. */
final class FrostifulProbes {
	private FrostifulProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("frostiful.game-rule-category", FrostifulProbes::gameRule),
			Probe.once("frostiful.data-registries", FrostifulProbes::registries));
	}

	/** The mod's rule is registered, under the category it made through Fabric's old custom category, with the name it gave it. */
	private static String gameRule(Probe.Ctx c) {
		if (!FabricLoader.getInstance().isModLoaded("frostiful")) throw new Probe.Skip("Frostiful is not installed");
		Identifier id = Identifier.fromNamespaceAndPath("frostiful", "do_passive_freezing");
		GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(id);
		if (rule == null) throw new Probe.Fail("the game rule " + id + " is not registered");
		Identifier category = Identifier.fromNamespaceAndPath("frostiful", "gamerule_category");
		if (!rule.category().id().equals(category)) throw new Probe.Fail("the rule's category is " + rule.category().id() + ", not " + category);
		String label = rule.category().label().getString();
		if (label.isBlank() || label.contains("gamerule.category.frostiful.gamerule_category")) throw new Probe.Fail("the category label is not the mod's name: '" + label + "'");
		return id + " is registered under category " + category + " with label '" + label + "'";
	}

	/** The world the probes run in loaded Frostiful's worldgen, loot and advancement files (a registry error stops a world from loading at all). */
	private static String registries(Probe.Ctx c) {
		if (!FabricLoader.getInstance().isModLoaded("frostiful")) throw new Probe.Skip("Frostiful is not installed");
		return c.onServer(() -> {
			var access = c.server().registryAccess();
			Identifier brittle = Identifier.fromNamespaceAndPath("frostiful", "brittle_ice");
			Identifier lichen = Identifier.fromNamespaceAndPath("frostiful", "sun_lichen_covered_rock");
			var features = access.lookupOrThrow(Registries.FEATURE);
			for (Identifier feature : List.of(brittle, lichen)) {
				if (features.get(feature).isEmpty()) throw new Probe.Fail("worldgen feature " + feature + " did not load");
			}
			Identifier chest = Identifier.fromNamespaceAndPath("frostiful", "chests/chillager_outpost/smith");
			var lootTables = c.server().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE);
			if (lootTables.get(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, chest)).isEmpty()) throw new Probe.Fail("loot table " + chest + " did not load");
			return "features " + brittle + " and " + lichen + " and loot table " + chest + " loaded";
		});
	}
}
