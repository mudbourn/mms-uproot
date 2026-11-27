package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * mms-origins Floran leaf form: walks a leaf-form player into a one-block dirt ledge on land and into a dirt wall
 * under water, and traces height, collisions and whether Apoli's phasing is active on each side. Dirt is in
 * mms_origins:leaf_passable, so a working leaf form passes through both without rising or stopping.
 */
final class LeafFormProbes {
	private static final String POWERS = "info.mudbourn.mmsorigins.MmsOriginsPowers";
	private static final String HOLDER = "io.github.apace100.apoli.component.PowerHolderComponent";
	private static final String PHASING = "io.github.apace100.apoli.power.PhasingPower";
	private static final int FLOOR = 119;

	private LeafFormProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.steps("leafform.land-ledge", 500, (c, t) -> walk(c, t, 0, false)),
			Probe.steps("leafform.water-wall", 500, (c, t) -> walk(c, t, 40, true)),
			Probe.steps("leafform.water-ledge", 500, (c, t) -> walk(c, t, 80, null)),
			Probe.steps("leafform.grass-ledge", 500, (c, t) -> walk(c, t, 120, false, true)),
			Probe.steps("leafform.sneak-sink", 400, (c, t) -> sink(c, t, 160)));
	}

	/**
	 * {@code water} true: a full-height dirt wall under water. false: a dirt ledge on land. null: a one-deep pool
	 * with a stone block at the water line, which a swimmer has to climb onto (stone is not passable).
	 */
	private static String walk(Probe.Ctx c, int tick, int x0, Boolean water) throws Exception {
		return walk(c, tick, x0, water, false);
	}

	/**
	 * {@code real} true plays it as a player does: a grass floor (itself passable) and the form turned on without the
	 * manual power sync, so the client only knows what Apoli syncs on its own.
	 */
	private static String walk(Probe.Ctx c, int tick, int x0, Boolean water, boolean real) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("mms_origins")) throw new Probe.Skip("mms-origins is not installed");
		MinecraftServer server = c.server();
		int y = FLOOR + 1;
		if (tick == 1) {
			c.mc.options.keyUp.setDown(false);
			run(server, "gamemode survival @p");
			// switch away first: an unchanged origin is not re-granted, so a save that lost its powers would stay empty
			run(server, "origin set @p origins:origin mms_origins:human");
			run(server, "origin set @p origins:origin mms_origins:floran");
			run(server, "forceload add " + (x0 - 2) + " -2 " + (x0 + 6) + " 16");
			run(server, "fill " + (x0 - 1) + " " + FLOOR + " -1 " + (x0 + 5) + " " + (y + 4) + " 15 minecraft:air");
			run(server, "fill " + x0 + " " + FLOOR + " 0 " + (x0 + 4) + " " + FLOOR + " 14 minecraft:" + (real ? "grass_block" : "stone"));
			if (water == null) {
				run(server, "fill " + (x0 - 1) + " " + y + " -1 " + (x0 + 5) + " " + y + " 15 minecraft:glass");
				run(server, "fill " + x0 + " " + y + " 0 " + (x0 + 4) + " " + y + " 7 minecraft:water");
				run(server, "fill " + x0 + " " + y + " 8 " + (x0 + 4) + " " + y + " 14 minecraft:stone");
			} else if (water) {
				run(server, "fill " + (x0 - 1) + " " + y + " -1 " + (x0 + 5) + " " + (y + 3) + " 15 minecraft:glass hollow");
				run(server, "fill " + x0 + " " + y + " 0 " + (x0 + 4) + " " + (y + 2) + " 14 minecraft:water");
				run(server, "fill " + x0 + " " + y + " 8 " + (x0 + 4) + " " + (y + 2) + " 8 minecraft:dirt");
			} else {
				run(server, "fill " + x0 + " " + y + " 8 " + (x0 + 4) + " " + y + " 8 minecraft:" + (real ? "grass_block" : "dirt"));
			}
			run(server, "tp @p " + (x0 + 2.5) + " " + y + " 2.5 0 0");
			return null;
		}
		LocalPlayer client = c.mc.player;
		if (!c.state.containsKey("trace")) {
			// The server can be busy generating terrain right after the join; retry until the form is set.
			if (tick < 40 || tick % 10 != 0) return null;
			boolean set;
			try {
				set = c.onServer(() -> setLeafForm(c.serverPlayer(), true, !real));
			} catch (Probe.Fail busy) {
				if (tick >= 200) throw busy;
				return null;
			}
			if (!set) {
				if (tick >= 200) throw new Probe.Fail("could not turn leaf form on: " + c.onServer(() -> describe(c.serverPlayer())));
				return null;
			}
			boolean done = complete(client) && c.onServer(() -> complete(c.serverPlayer()));
			if (!done) {
				if (tick >= 200) throw new Probe.Fail("leaf form did not finish morphing: client " + complete(client)
					+ ", server " + c.onServer(() -> complete(c.serverPlayer())));
				return null;
			}
			c.state.put("trace", new ArrayList<String>());
			c.state.put("maxY", client.getY());
			client.setYRot(0.0F);
			c.state.put("walkStart", tick);
		}
		c.mc.options.keyUp.setDown(true);
		@SuppressWarnings("unchecked") List<String> trace = (List<String>) c.state.get("trace");
		ServerPlayer sp = c.serverPlayer();
		double sy = c.onServer(() -> sp.getY());
		double sz = c.onServer(() -> sp.getZ());
		boolean sPhase = c.onServer(() -> phasing(sp));
		c.state.put("maxY", Math.max((double) c.state.get("maxY"), client.getY()));
		if (tick % 5 == 0 || client.horizontalCollision) {
			trace.add(String.format("t%d c(y%.2f z%.2f hc%s climb%s phase%s ground%s water%s) s(y%.2f z%.2f phase%s)", tick,
				client.getY(), client.getZ(), b(client.horizontalCollision), b(client.onClimbable()), b(phasing(client)),
				b(client.onGround()), b(client.isInWater()), sy, sz, b(sPhase)));
		}
		boolean through = client.getZ() > 9.5;
		if (through || tick >= (int) c.state.get("walkStart") + 150) {
			c.mc.options.keyUp.setDown(false);
			double maxY = (double) c.state.get("maxY");
			c.onServer(() -> setLeafForm(c.serverPlayer(), false));
			String summary = String.format("through=%s maxY=%.2f (start %d) | %s", through, maxY, y,
				String.join(" ; ", trace.subList(Math.max(0, trace.size() - 14), trace.size())));
			if (!through) throw new Probe.Fail("stuck before the dirt: " + summary);
			if (water == null) {
				if (client.getY() < y + 0.95) throw new Probe.Fail("did not climb out onto the ledge: " + summary);
				return summary;
			}
			if (maxY > y + 0.6) throw new Probe.Fail("rose over the dirt instead of phasing: " + summary);
			return summary;
		}
		return null;
	}

	/** Stands a leaf-form player on a grass-topped, three-deep dirt floor, holds sneak and expects them to sink into it. */
	private static String sink(Probe.Ctx c, int tick, int x0) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("mms_origins")) throw new Probe.Skip("mms-origins is not installed");
		MinecraftServer server = c.server();
		int y = FLOOR + 1;
		if (tick == 1) {
			c.mc.options.keyShift.setDown(false);
			run(server, "gamemode survival @p");
			run(server, "origin set @p origins:origin mms_origins:human");
			run(server, "origin set @p origins:origin mms_origins:floran");
			run(server, "forceload add " + (x0 - 2) + " -2 " + (x0 + 6) + " 8");
			run(server, "fill " + (x0 - 1) + " " + (FLOOR - 3) + " -1 " + (x0 + 5) + " " + (y + 4) + " 7 minecraft:air");
			run(server, "fill " + x0 + " " + (FLOOR - 3) + " 0 " + (x0 + 4) + " " + (FLOOR - 3) + " 6 minecraft:stone");
			run(server, "fill " + x0 + " " + (FLOOR - 2) + " 0 " + (x0 + 4) + " " + FLOOR + " 6 minecraft:dirt");
			run(server, "fill " + x0 + " " + FLOOR + " 0 " + (x0 + 4) + " " + FLOOR + " 6 minecraft:grass_block");
			run(server, "tp @p " + (x0 + 2.5) + " " + y + " 2.5 0 0");
			return null;
		}
		LocalPlayer client = c.mc.player;
		if (!c.state.containsKey("sinkStart")) {
			if (tick < 40 || tick % 10 != 0) return null;
			boolean set;
			try {
				set = c.onServer(() -> setLeafForm(c.serverPlayer(), true, false));
			} catch (Probe.Fail busy) {
				if (tick >= 200) throw busy;
				return null;
			}
			boolean done = set && complete(client) && c.onServer(() -> complete(c.serverPlayer()));
			if (!done) {
				if (tick >= 200) throw new Probe.Fail("leaf form did not finish: set " + set + ", client " + complete(client)
					+ ", client phasing " + phasing(client) + ", server " + c.onServer(() -> complete(c.serverPlayer())));
				return null;
			}
			c.state.put("sinkStart", tick);
		}
		c.mc.options.keyShift.setDown(true);
		int start = (int) c.state.get("sinkStart");
		ServerPlayer sp = c.serverPlayer();
		String now = String.format("c(y%.2f shift%s phase%s ground%s) s(y%.2f phase%s)", client.getY(), b(client.isShiftKeyDown()),
			b(phasing(client)), b(client.onGround()), c.onServer(() -> sp.getY()), b(c.onServer(() -> phasing(sp))));
		if (client.getY() < y - 1.0) {
			c.mc.options.keyShift.setDown(false);
			c.onServer(() -> setLeafForm(c.serverPlayer(), false));
			return "sank: " + now;
		}
		if (tick >= start + 60) {
			c.mc.options.keyShift.setDown(false);
			c.onServer(() -> setLeafForm(c.serverPlayer(), false));
			throw new Probe.Fail("did not sink into the dirt while sneaking: " + now);
		}
		return null;
	}

	private static String b(boolean v) {
		return v ? "Y" : "n";
	}

	private static void run(MinecraftServer server, String command) {
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
	}

	private static boolean complete(Entity entity) throws Exception {
		Class<?> powers = Class.forName(POWERS, true, LeafFormProbes.class.getClassLoader());
		return (boolean) powers.getMethod("leafFormComplete", Entity.class).invoke(null, entity);
	}

	/**
	 * Sets the leaf form toggle on the server, syncing the power holder to the client when it flips.
	 * Returns whether the toggle now matches {@code on}; false while the origin has not applied yet.
	 */
	private static boolean setLeafForm(Player player, boolean on) throws Exception {
		return setLeafForm(player, on, true);
	}

	private static boolean setLeafForm(Player player, boolean on, boolean sync) throws Exception {
		ClassLoader loader = LeafFormProbes.class.getClassLoader();
		Object type = Class.forName(POWERS, true, loader).getField("LEAF_FORM").get(null);
		Object power = type.getClass().getMethod("get", Entity.class).invoke(type, player);
		if (power == null) return false;
		if ((boolean) type.getClass().getMethod("isActive", Entity.class).invoke(type, player) == on) return true;
		power.getClass().getMethod("onUse").invoke(power);
		if (!sync) return (boolean) type.getClass().getMethod("isActive", Entity.class).invoke(type, player) == on;
		Field key = Class.forName(HOLDER, true, loader).getField("KEY");
		Object componentKey = key.get(null);
		componentKey.getClass().getMethod("sync", Object.class).invoke(componentKey, player);
		return (boolean) type.getClass().getMethod("isActive", Entity.class).invoke(type, player) == on;
	}

	/** The player's power ids, for a failure message. */
	private static String describe(Player player) throws Exception {
		ClassLoader loader = LeafFormProbes.class.getClassLoader();
		Object type = Class.forName(POWERS, true, loader).getField("LEAF_FORM").get(null);
		Object holder = Class.forName(HOLDER, true, loader).getField("KEY").get(null);
		Object component = holder.getClass().getMethod("get", Object.class).invoke(holder, player);
		return "leaf_form=" + type + " power=" + type.getClass().getMethod("get", Entity.class).invoke(type, player)
			+ " holder=" + component;
	}

	/** Whether any of the entity's Apoli phasing powers is active. */
	private static boolean phasing(Entity entity) throws Exception {
		ClassLoader loader = LeafFormProbes.class.getClassLoader();
		Class<?> phasing = Class.forName(PHASING, true, loader);
		Method getPowers = Class.forName(HOLDER, true, loader).getMethod("getPowers", Entity.class, Class.class);
		for (Object power : (List<?>) getPowers.invoke(null, entity, phasing)) {
			if ((boolean) power.getClass().getMethod("isActive").invoke(power)) return true;
		}
		return false;
	}
}
