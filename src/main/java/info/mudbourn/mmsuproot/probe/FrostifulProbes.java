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
			Probe.once("frostiful.data-registries", FrostifulProbes::registries),
			Probe.steps("frostiful.water-bottle-soaks", 300, FrostifulProbes::waterBottle),
			Probe.once("frostiful.heart-type-mixin-applied", FrostifulProbes::heartMixin),
			Probe.once("frostiful.loot-condition-encode", FrostifulProbes::lootConditionRoundTrip));
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

	private static final String SOAKABLE = "com.github.thedeathlycow.thermoo.api.temperature.Soakable";

	private static void command(Probe.Ctx c, String command) {
		var server = c.server();
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
	}

	/**
	 * A splashed bottle of water soaks the player it lands on (Frostiful's hook on the water case of a thrown potion, which 26.3 folds into
	 * the method that affects the entities around), and a splashed potion of anything else does not.
	 */
	private static String waterBottle(Probe.Ctx c, int tick) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("frostiful")) throw new Probe.Skip("Frostiful is not installed");
		Class<?> soakable = Class.forName(SOAKABLE, true, FrostifulProbes.class.getClassLoader());
		java.lang.reflect.Method get = soakable.getMethod("thermoo$getWetTicks");
		java.lang.reflect.Method set = soakable.getMethod("thermoo$setWetTicks", int.class);
		Object player = c.serverPlayer();
		String water = "{Item:{id:\"minecraft:splash_potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"minecraft:water\"}}}}";
		String speed = "{Item:{id:\"minecraft:splash_potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"minecraft:swiftness\"}}}}";
		if (tick == 1) {
			c.onServer(() -> {
				set.invoke(player, 0);
				return null;
			});
			command(c, "execute at @p run summon minecraft:splash_potion ~ ~1 ~ " + speed);
			return null;
		}
		if (tick == 30) {
			int wet = c.onServer(() -> (int) get.invoke(player));
			if (wet != 0) throw new Probe.Fail("a splash potion of swiftness soaked the player (" + wet + " wet ticks)");
			c.state.put("afterOther", wet);
			command(c, "effect clear @p");
			command(c, "execute at @p run summon minecraft:splash_potion ~ ~1 ~ " + water);
			return null;
		}
		if (tick == 60) {
			int wet = c.onServer(() -> (int) get.invoke(player));
			c.onServer(() -> {
				set.invoke(player, 0);
				return null;
			});
			if (wet <= 0) throw new Probe.Fail("a splashed bottle of water did not soak the player");
			return "a splashed bottle of water left the player with " + wet + " wet ticks; a splash potion of swiftness left 0";
		}
		return null;
	}

	/** The mixin on the heart types of the health bar (Gui$HeartType in 1.21.11, Hud$HeartType now) merged its handler into the enum. */
	private static String heartMixin(Probe.Ctx c) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("frostiful")) throw new Probe.Skip("Frostiful is not installed");
		Class<?> heartType = Class.forName("net.minecraft.client.gui.Hud$HeartType");
		for (java.lang.reflect.Method m : heartType.getDeclaredMethods()) {
			if (m.getName().contains("frostifulIsFrozen")) return "the handler " + m.getName() + " is merged into " + heartType.getName();
		}
		throw new Probe.Fail(heartType.getName() + " has no merged handler of Frostiful's mixin");
	}

	/** Mod loot conditions encode: their codec() answers, through the type they registered. The conditions go in through JSON and come out the same. */
	private static String lootConditionRoundTrip(Probe.Ctx c) {
		if (!FabricLoader.getInstance().isModLoaded("frostiful")) throw new Probe.Skip("Frostiful is not installed");
		List<String> checked = new java.util.ArrayList<>();
		for (String json : List.of("{\"type\":\"frostiful:wearing_ice_like_item\"}", "{\"type\":\"thermoo:temperature\",\"scale\":{\"max\":-0.1}}")) {
			com.google.gson.JsonElement in = com.google.gson.JsonParser.parseString(json);
			var parsed = net.minecraft.world.level.storage.loot.predicates.LootItemCondition.DIRECT_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, in);
			var condition = parsed.getOrThrow(msg -> new Probe.Fail("decode failed: " + msg));
			var encoded = net.minecraft.world.level.storage.loot.predicates.LootItemCondition.DIRECT_CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, condition)
				.getOrThrow(msg -> new Probe.Fail("encode failed: " + msg));
			// the codec may write fields it defaulted on the way in, so the check is that its own output decodes and encodes to itself
			var again = net.minecraft.world.level.storage.loot.predicates.LootItemCondition.DIRECT_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded)
				.getOrThrow(msg -> new Probe.Fail("decode of the encoded form failed: " + msg));
			var encodedAgain = net.minecraft.world.level.storage.loot.predicates.LootItemCondition.DIRECT_CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, again)
				.getOrThrow(msg -> new Probe.Fail("second encode failed: " + msg));
			if (!encoded.equals(encodedAgain)) throw new Probe.Fail("encoded " + encoded + " then " + encodedAgain);
			checked.add(condition.getClass().getSimpleName());
		}
		return "decode and encode round trips: " + checked;
	}
}
