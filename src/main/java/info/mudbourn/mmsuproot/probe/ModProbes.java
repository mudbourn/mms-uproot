package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Leawind's Third Person (with Perspective API), Locator Border and Absolut Revive. */
final class ModProbes {
	private static final String LEAWIND_PERSPECTIVE = "leawind_third_person.third_person";

	private ModProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.steps("leawind.third-person-camera-offset", 400, ModProbes::thirdPerson),
			Probe.steps("locator-border.waypoint-mob-hud-frames", 400, ModProbes::locator),
			Probe.once("absolutrevive.downed-state", ModProbes::revive),
			Probe.eyes("leawind.camera-look", "press F5 in game: the camera sits close behind the shoulder, collides with walls and does not jitter while walking"),
			Probe.eyes("locator-border.icons", "walk away from a named mob that transmits a waypoint and confirm its icon rides the screen border with the right colour and no overlap with the hotbar"));
	}

	private static String thirdPerson(Probe.Ctx c, int tick) throws Exception {
		Minecraft mc = c.mc;
		if (tick == 1) {
			c.state.put("before", mc.options.getCameraType());
			// a fixed facing, so every run starts the same
			mc.player.setYRot(0.0F);
			mc.player.setXRot(0.0F);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			return null;
		}
		if (tick < 120) return null;
		try {
			Class<?> api = Class.forName("io.github.leawind.perspectiveapi.api.PerspectiveAPI", true, ModProbes.class.getClassLoader());
			Method isCurrent = api.getMethod("isCurrent", String.class);
			boolean leawind = (boolean) isCurrent.invoke(null, LEAWIND_PERSPECTIVE);
			Vec3 camera = mc.gameRenderer.mainCamera().position();
			Vec3 eye = mc.player.getEyePosition(1.0F);
			double distance = camera.distanceTo(eye);
			// vanilla puts the camera on the line straight behind the head; a shoulder offset leaves that line
			Vec3 back = mc.player.getViewVector(1.0F).scale(-1.0);
			double cos = camera.subtract(eye).normalize().dot(back);
			double off = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
			if (mc.options.getCameraType().isFirstPerson()) throw new Probe.Fail("the camera is still first person after setting THIRD_PERSON_BACK");
			if (!leawind) throw new Probe.Fail("Leawind's perspective is not the current one (" + api.getMethod("getCurrent").invoke(null) + ")");
			if (distance < 0.2) throw new Probe.Fail("camera " + camera + " is at the player's eye " + eye + " (distance " + distance + ")");
			// vanilla third person back sits 4 blocks out unless a wall is in the way; Leawind's own distance setting is about 1.1
			// Leawind turns the camera apart from the player's body, so the angle to the player's facing ("off") depends on where each
			// pointed and is information only. The camera must still look back at the player: the eye lies near its own line of sight,
			// off it only by the shoulder offset.
			org.joml.Vector3fc fwd = mc.gameRenderer.mainCamera().forwardVector();
			Vec3 toEye = eye.subtract(camera).normalize();
			double sight = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, toEye.x * fwd.x() + toEye.y * fwd.y() + toEye.z * fwd.z()))));
			if (sight > 45.0) throw new Probe.Fail("the camera does not look back at the player: the eye is " + String.format("%.1f", sight) + " degrees off its line of sight");
			if (sight < 0.5) throw new Probe.Fail("the eye lies on the camera's line of sight (" + String.format("%.2f", sight) + " degrees), so no shoulder offset shows");
			return "Leawind perspective current; camera is " + String.format("%.2f", distance) + " blocks from the eye (vanilla: up to 4.00); the eye is "
				+ String.format("%.1f", sight) + " degrees off the camera's line of sight (shoulder offset); " + String.format("%.1f", off) + " degrees off the player's facing (free camera, information only)";
		} finally {
			mc.options.setCameraType((CameraType) c.state.get("before"));
		}
	}

	private static void hud(Minecraft mc, int frames) {
		GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(mc, new GuiRenderState(), 0, 0);
		for (int i = 0; i < frames; i++) {
			mc.gui.hud.extractRenderState(graphics, mc.getDeltaTracker());
			new LocatorBar(mc).extractRenderState(graphics, mc.getDeltaTracker());
		}
	}

	/**
	 * A mob with {@code waypoint_transmit_range} sends its waypoint to the player; Locator Border then draws it itself. This path once
	 * crashed in {@code getWaypointStyles} and {@code PlayerTeam.getColor}.
	 */
	private static String locator(Probe.Ctx c, int tick) {
		Minecraft mc = c.mc;
		if (tick == 1) {
			c.onServer(() -> {
				ServerPlayer player = c.server().getPlayerList().getPlayers().get(0);
				String name = player.getName().getString();
				c.server().getCommands().performPrefixedCommand(c.server().createCommandSourceStack().withSuppressedOutput(),
					"execute at " + name + " run summon minecraft:zombie ~4 ~ ~ {NoAI:1b,PersistenceRequired:1b,Tags:[\"uprootprobe\"],attributes:[{id:\"minecraft:waypoint_transmit_range\",base:200.0d}]}");
				return null;
			});
			return null;
		}
		try {
			if (!mc.player.connection.getWaypointManager().hasWaypoints()) {
				if (tick < 300) return null;
				throw new Probe.Fail("no waypoint reached the client within 300 ticks of summoning a transmitting mob");
			}
			// the mod's mixin code must be merged into the classes rendered below, or the frames would prove nothing
			if (java.util.Arrays.stream(net.minecraft.client.gui.Hud.class.getDeclaredMethods()).noneMatch(m -> m.getName().contains("renderLocatorBorder"))) throw new Probe.Fail("Locator Border's renderLocatorBorder is not merged into Hud");
			if (java.util.Arrays.stream(LocatorBar.class.getDeclaredMethods()).noneMatch(m -> m.getName().contains("onRender"))) throw new Probe.Fail("Locator Border's onRender is not merged into LocatorBar");
			hud(mc, 30);
			boolean[] seen = {false};
			mc.player.connection.getWaypointManager().forEachWaypoint(mc.player, w -> seen[0] = true);
			return "waypoint tracked on the client" + (seen[0] ? " and visible to forEachWaypoint" : " (not yet in range of forEachWaypoint)") + "; 30 HUD frames + 30 LocatorBar frames extracted without an exception";
		} finally {
			if (tick >= 300 || mc.player.connection.getWaypointManager().hasWaypoints()) {
				c.onServer(() -> {
					c.server().getCommands().performPrefixedCommand(c.server().createCommandSourceStack().withSuppressedOutput(), "kill @e[tag=uprootprobe]");
					return null;
				});
			}
		}
	}

	/** Absolut Revive's public damage model: put the player into the unconscious (downed) state, then revive. */
	private static String revive(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerPlayer player = c.server().getPlayerList().getPlayers().get(0);
			Object model = null;
			try {
				Class<?> utils = Class.forName("goetic.mods.absolutrevive.common.util.CommonUtils", true, ModProbes.class.getClassLoader());
				model = utils.getMethod("getDamageModel", Player.class).invoke(null, player);
				Class<?> type = model.getClass();
				Method isUnconscious = type.getMethod("isUnconscious");
				Method ticks = type.getMethod("getUnconsciousTicks");
				if ((boolean) isUnconscious.invoke(model)) throw new Probe.Fail("the player is already unconscious before the probe");
				type.getMethod("setUnconsciousState", int.class, boolean.class, boolean.class, String.class).invoke(model, 600, true, false, "uproot_probe");
				if (!(boolean) isUnconscious.invoke(model)) throw new Probe.Fail("setUnconsciousState(600) did not make the player unconscious");
				int downedTicks = (int) ticks.invoke(model);
				if (downedTicks <= 0 || downedTicks > 600) throw new Probe.Fail("unconscious ticks " + downedTicks + ", want 1..600");
				if (!(boolean) type.getMethod("canBeRescued").invoke(model)) throw new Probe.Fail("an unconscious player cannot be rescued");
				// the mod's own rescue; its revivePlayer(Player) is an empty method in 1.2.2
				float health = player.getHealth();
				int food = player.getFoodData().getFoodLevel();
				boolean rescued = (boolean) type.getMethod("rescueFromCriticalState", Player.class).invoke(model, player);
				player.setHealth(health);
				player.getFoodData().setFoodLevel(food);
				if (!rescued || (boolean) isUnconscious.invoke(model)) throw new Probe.Fail("rescueFromCriticalState returned " + rescued + " and the player is " + ((boolean) isUnconscious.invoke(model) ? "still" : "no longer") + " unconscious");
				return "downed state set (" + downedTicks + " ticks left, canBeRescued), cleared by rescueFromCriticalState";
			} catch (java.lang.reflect.InvocationTargetException e) {
				throw new Probe.Fail("Absolut Revive threw: " + e.getCause());
			} finally {
				if (model != null) {
					try {
						model.getClass().getMethod("clearUnconsciousState").invoke(model);
					} catch (ReflectiveOperationException e) {
						// best effort
					}
				}
			}
		});
	}
}
