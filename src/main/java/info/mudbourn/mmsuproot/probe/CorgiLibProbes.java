package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import com.google.gson.JsonParser;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** CorgiLib (1.21.11 build): its entity conditions (decode and pass), the structure tracker its mixin puts on entities, and its command. */
final class CorgiLibProbes {
	private static final String CONDITION = "corgitaco.corgilib.entity.condition.Condition";
	private static final String CONTEXT = "corgitaco.corgilib.entity.condition.ConditionContext";
	private static final String TRACKER = "corgitaco.corgilib.entity.IsInsideStructureTracker";

	private CorgiLibProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("corgilib.entity-conditions", CorgiLibProbes::conditions),
			Probe.once("corgilib.entity-structure-tracker", CorgiLibProbes::tracker),
			Probe.once("corgilib.place-all-command", CorgiLibProbes::command));
	}

	private static void requireMod() {
		if (!FabricLoader.getInstance().isModLoaded("corgilib")) throw new Probe.Skip("CorgiLib is not installed");
	}

	private static Class<?> load(String name) throws ClassNotFoundException {
		return Class.forName(name, true, CorgiLibProbes.class.getClassLoader());
	}

	private static boolean passes(Object condition, Level level, LivingEntity entity) throws ReflectiveOperationException {
		Constructor<?> ctor = load(CONTEXT).getConstructor(Level.class, LivingEntity.class, boolean.class, int.class);
		Object context = ctor.newInstance(level, entity, true, 0);
		Method passes = load(CONDITION).getMethod("passes", load(CONTEXT));
		return (boolean) passes.invoke(condition, context);
	}

	/** Conditions decode from JSON through the mod's registry-keyed codec and decide on a baby and an adult zombie as their meaning says. */
	@SuppressWarnings("unchecked")
	private static String conditions(Probe.Ctx c) throws Exception {
		requireMod();
		Codec<Object> codec = (Codec<Object>) load(CONDITION).getField("CODEC").get(null);
		var level = c.mc.level;
		var baby = (net.minecraft.world.entity.Mob) EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		baby.setBaby(true);
		var adult = (LivingEntity) EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		String[][] cases = {
			{"{\"type\":\"corgilib:is_baby\"}", "true", "false"},
			{"{\"type\":\"corgilib:flipped\",\"condition_to_flip\":{\"type\":\"corgilib:is_baby\"}}", "false", "true"},
			{"{\"type\":\"corgilib:chance\",\"chance\":1.0}", "true", "true"},
			{"{\"type\":\"corgilib:chance\",\"chance\":0.0}", "false", "false"}};
		for (String[] one : cases) {
			Object condition = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(one[0])).getOrThrow(msg -> new Probe.Fail("decode of " + one[0] + " failed: " + msg));
			boolean forBaby = passes(condition, level, baby);
			boolean forAdult = passes(condition, level, adult);
			if (forBaby != Boolean.parseBoolean(one[1]) || forAdult != Boolean.parseBoolean(one[2])) {
				throw new Probe.Fail(one[0] + " gave baby " + forBaby + " and adult " + forAdult + ", wanted " + one[1] + " and " + one[2]);
			}
			codec.encodeStart(JsonOps.INSTANCE, condition).getOrThrow(msg -> new Probe.Fail("encode of " + one[0] + " failed: " + msg));
		}
		return cases.length + " conditions decode, encode and decide as expected on a baby and an adult zombie";
	}

	/** Entities carry the structure tracker the mod's mixin adds, and what it holds can be set and read back. */
	private static String tracker(Probe.Ctx c) throws Exception {
		requireMod();
		Class<?> access = load(TRACKER + "$Access");
		Class<?> inside = load(TRACKER + "$IsInside");
		var entity = EntityTypes.ZOMBIE.create(c.mc.level, EntitySpawnReason.COMMAND);
		if (!access.isInstance(entity)) throw new Probe.Fail("a zombie does not implement " + access.getName());
		Object tracker = access.getMethod("getIsInsideStructureTracker").invoke(entity);
		if (tracker == null) throw new Probe.Fail("the entity's structure tracker is null");
		Object state = load(TRACKER).getMethod("getTracker").invoke(tracker);
		return "zombies implement " + access.getSimpleName() + ", their tracker holds " + (state == null ? "no state yet" : state) + " (" + inside.getSimpleName() + ")";
	}

	/** The /corgilib place_all command is registered in the server's command tree. */
	private static String command(Probe.Ctx c) {
		requireMod();
		return c.onServer(() -> {
			var root = c.server().getCommands().getDispatcher().getRoot();
			for (CommandNode<?> top : root.getChildren()) {
				if (top.getName().equals("place_all")) return "command /place_all is registered";
				for (CommandNode<?> sub : top.getChildren()) {
					if (sub.getName().equals("place_all")) return "command /" + top.getName() + " place_all is registered";
				}
			}
			throw new Probe.Fail("no place_all command in the command tree");
		});
	}
}
