package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Method;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;

/** Cobweb (a registry library) and Prometheus (custom fire types), 1.21.11 builds. */
final class CrystalnestProbes {
	private static final String FIRE_MANAGER = "it.crystalnest.prometheus.api.FireManager";

	private CrystalnestProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("cobweb.registers-prometheus-entries", CrystalnestProbes::cobwebRegisters),
			Probe.once("prometheus.fire-types-registered", CrystalnestProbes::fireTypes),
			Probe.once("prometheus.set-on-fire-keeps-fire-type", CrystalnestProbes::setOnFire),
			Probe.once("prometheus.fire-light-level", CrystalnestProbes::lightLevel),
			Probe.eyes("prometheus.flame-visuals", "set a mob and yourself on fire (flint and steel, then a soul campfire): the flames on the body use the fire type's texture; known gap: a burning mob shows vanilla flames in 26.3, see dead-hooks.txt"));
	}

	private static Class<?> manager() throws ClassNotFoundException {
		if (!FabricLoader.getInstance().isModLoaded("prometheus")) throw new Probe.Skip("Prometheus is not installed");
		return Class.forName(FIRE_MANAGER, true, CrystalnestProbes.class.getClassLoader());
	}

	/** Cobweb's register helper put Prometheus's own custom campfire block entity type into the game registry under Prometheus's namespace. */
	private static String cobwebRegisters(Probe.Ctx c) {
		if (!FabricLoader.getInstance().isModLoaded("cobweb")) throw new Probe.Skip("Cobweb is not installed");
		Identifier id = Identifier.fromNamespaceAndPath("prometheus", "custom_campfire");
		if (!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id)) throw new Probe.Fail("the block entity type " + id + " is not registered");
		return id + " is registered through Cobweb";
	}

	/** The fires Prometheus ships answer with the vanilla damage of default and soul fire. */
	private static String fireTypes(Probe.Ctx c) throws Exception {
		Class<?> manager = manager();
		List<?> fires = (List<?>) manager.getMethod("getFires").invoke(null);
		if (fires.size() < 3) throw new Probe.Fail("only " + fires.size() + " fires are registered: " + fires);
		Object soul = manager.getMethod("getFire", Identifier.class).invoke(null, manager.getField("SOUL_FIRE_TYPE").get(null));
		Object fire = manager.getMethod("getFire", Identifier.class).invoke(null, manager.getField("DEFAULT_FIRE_TYPE").get(null));
		float soulDamage = (float) soul.getClass().getMethod("getDamage").invoke(soul);
		float damage = (float) fire.getClass().getMethod("getDamage").invoke(fire);
		if (soulDamage != 2.0F || damage != 1.0F) throw new Probe.Fail("soul fire damage " + soulDamage + ", fire damage " + damage + ", want 2.0 and 1.0");
		return fires.size() + " fires registered; fire damage " + damage + ", soul fire damage " + soulDamage;
	}

	/** Setting an entity on fire through the API leaves it burning with the fire type that was asked for (an entity mixin of the mod stores it). */
	private static String setOnFire(Probe.Ctx c) throws Exception {
		Class<?> manager = manager();
		Identifier soul = (Identifier) manager.getField("SOUL_FIRE_TYPE").get(null);
		Method setOnFire = manager.getMethod("setOnFire", Entity.class, float.class, Identifier.class);
		Class<?> typed = Class.forName("it.crystalnest.prometheus.api.type.FireTyped", true, CrystalnestProbes.class.getClassLoader());
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			Entity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			setOnFire.invoke(null, zombie, 5.0F, soul);
			Object type = typed.getMethod("getFireType").invoke(zombie);
			if (!soul.equals(type)) throw new Probe.Fail("the zombie's fire type is " + type + ", want " + soul);
			if (zombie.getRemainingFireTicks() <= 0) throw new Probe.Fail("the zombie is not burning after setOnFire");
			return "a zombie set on fire with " + soul + " burns for " + zombie.getRemainingFireTicks() + " ticks and reports that type";
		});
	}

	/** The light a fire gives goes through the mod's table: soul fire is 10, default fire 15. */
	private static String lightLevel(Probe.Ctx c) throws Exception {
		Class<?> manager = manager();
		Method light = manager.getMethod("light", Identifier.class);
		int soul = (int) light.invoke(null, manager.getField("SOUL_FIRE_TYPE").get(null));
		int fire = (int) light.invoke(null, manager.getField("DEFAULT_FIRE_TYPE").get(null));
		if (soul != 10 || fire != 15) throw new Probe.Fail("light of soul fire " + soul + " and of fire " + fire + ", want 10 and 15");
		return "light of soul fire " + soul + ", of fire " + fire;
	}
}
