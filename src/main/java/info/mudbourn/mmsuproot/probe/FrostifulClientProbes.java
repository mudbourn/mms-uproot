package info.mudbourn.mmsuproot.probe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Frostiful's client side, run without looking at it: the render-path code of its entities, its ice skate layer and its icicle block runs
 * and resolves, and a few screenshots of the same things are saved for a person to look at. A pass here says the code ran, not that it
 * looks right.
 */
final class FrostifulClientProbes {
	private static final String NAMESPACE = "frostiful";

	private FrostifulClientProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("frostiful.client.entity-render-path", FrostifulClientProbes::entities),
			Probe.once("frostiful.client.ice-skate-layer", FrostifulClientProbes::skateLayer),
			Probe.once("frostiful.client.block-models", FrostifulClientProbes::blockModels),
			Probe.steps("frostiful.client.cold-hud", 400, FrostifulClientProbes::coldHud),
			Probe.steps("frostiful.client.shiver-view-shake", 400, FrostifulClientProbes::shiverShake),
			Probe.steps("frostiful.client.screenshots", 700, FrostifulClientProbes::screenshots));
	}

	private static void requireMod() {
		if (!FabricLoader.getInstance().isModLoaded("frostiful")) throw new Probe.Skip("Frostiful is not installed");
	}

	/** Every Frostiful entity type has a renderer, and the renderer extracts a state from a fresh entity and submits it. */
	private static String entities(Probe.Ctx c) {
		requireMod();
		Minecraft mc = c.mc;
		EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
		List<String> done = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
			if (!id.getNamespace().equals(NAMESPACE)) continue;
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
			Entity entity = type.create(mc.level, EntitySpawnReason.COMMAND);
			if (entity == null) throw new Probe.Fail("could not create a client-side " + id);
			entity.setId(-1000 - done.size()); // a client entity that was never added to a level has no id yet
			EntityRenderer<?, ?> renderer = dispatcher.getRenderer(entity);
			if (renderer == null) throw new Probe.Fail("no renderer for " + id);
			EntityRenderState state = dispatcher.extractEntity(entity, 1.0F);
			if (state == null) throw new Probe.Fail("the renderer of " + id + " extracted no state");
			dispatcher.submit(state, new CameraRenderState(), 0.0, 0.0, 0.0, new PoseStack(), new SubmitNodeStorage());
			done.add(id.getPath() + "->" + renderer.getClass().getSimpleName());
		}
		if (done.isEmpty()) throw new Probe.Fail("Frostiful registered no entity types");
		return done.size() + " entity types extracted and submitted: " + done;
	}

	/** The ice skate layer is among the render layers of the player renderer, and the player extracts and submits with it there. */
	private static String skateLayer(Probe.Ctx c) throws Exception {
		requireMod();
		Minecraft mc = c.mc;
		EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
		List<String> where = new ArrayList<>();
		for (Entity entity : List.of(mc.player, net.minecraft.world.entity.EntityTypes.ZOMBIE.create(mc.level, EntitySpawnReason.COMMAND), net.minecraft.world.entity.EntityTypes.SKELETON.create(mc.level, EntitySpawnReason.COMMAND))) {
			EntityRenderer<?, ?> renderer = dispatcher.getRenderer(entity);
			Class<?> living = Class.forName("net.minecraft.client.renderer.entity.LivingEntityRenderer");
			if (!living.isInstance(renderer)) continue;
			Field layers = living.getDeclaredField("layers");
			layers.setAccessible(true);
			boolean has = ((List<?>) layers.get(renderer)).stream().anyMatch(l -> l.getClass().getSimpleName().equals("IceSkateFeatureRenderer"));
			if (has) where.add(entity.getType().builtInRegistryHolder().key().identifier().getPath());
		}
		if (!where.contains("player")) throw new Probe.Fail("the player renderer has no IceSkateFeatureRenderer layer (found on: " + where + ")");
		EntityRenderState state = dispatcher.extractEntity(mc.player, 1.0F);
		dispatcher.submit(state, new CameraRenderState(), 0.0, 0.0, 0.0, new PoseStack(), new SubmitNodeStorage());
		return "the ice skate layer is attached to the renderers of: " + where + "; the player's state extracts and submits through it";
	}

	/** Every state of Frostiful's blocks has a baked model that is not the missing one, the icicle's states included. */
	private static String blockModels(Probe.Ctx c) {
		requireMod();
		BlockStateModelSet models = c.mc.getModelManager().getBlockStateModelSet();
		Object missing = models.missingModel();
		int states = 0;
		int blocks = 0;
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (!id.getNamespace().equals(NAMESPACE)) continue;
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			blocks++;
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				states++;
				if (models.get(state) == missing) throw new Probe.Fail("the model of " + id + " " + state + " is the missing model");
			}
		}
		Block icicle = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(NAMESPACE, "icicle"));
		if (icicle == null || icicle == net.minecraft.world.level.block.Blocks.AIR) throw new Probe.Fail("frostiful:icicle is not registered");
		return blocks + " blocks, " + states + " states resolve to a model other than the missing one (frostiful:icicle has " + icicle.getStateDefinition().getPossibleStates().size() + ")";
	}

	private static final String THERMOO_EVENTS = "com.github.thedeathlycow.thermoo.api.client.StatusBarOverlayRenderEvents";

	/** Draws the HUD a few times into a scratch state and returns how many elements it holds. */
	private static int hudElements(Minecraft mc) {
		GuiRenderState state = new GuiRenderState();
		GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(mc, state, 0, 0);
		for (int i = 0; i < 3; i++) mc.gui.hud.extractRenderState(graphics, mc.getDeltaTracker());
		int most = 0;
		for (GuiRenderState.TraverseRange range : GuiRenderState.TraverseRange.values()) {
			int[] count = {0};
			state.forEachElement(e -> count[0]++, range);
			most = Math.max(most, count[0]);
		}
		return most;
	}

	/**
	 * With the player cold (through Thermoo's API) the HUD's health bar hook runs: Thermoo's event after the health bar is called with the
	 * heart positions the hook captured, and the HUD holds more elements than with the player at a neutral temperature (Thermoo's cold
	 * hearts, Frostiful's frost overlay). A screenshot of that HUD is saved for a person to judge.
	 */
	private static String coldHud(Probe.Ctx c, int tick) throws Exception {
		requireMod();
		Minecraft mc = c.mc;
		if (mc.player == null) return null;
		Class<?> events = Class.forName(THERMOO_EVENTS, true, FrostifulClientProbes.class.getClassLoader());
		Class<?> callback = Class.forName(THERMOO_EVENTS + "$RenderHealthBarCallback", true, FrostifulClientProbes.class.getClassLoader());
		Class<?> aware = Class.forName("com.github.thedeathlycow.thermoo.api.temperature.TemperatureAware", true, FrostifulClientProbes.class.getClassLoader());
		java.lang.reflect.Method setTemperature = aware.getMethod("thermoo$setTemperature", int.class);
		java.lang.reflect.Method getTemperature = aware.getMethod("thermoo$getTemperature");
		java.lang.reflect.Method minTemperature = aware.getMethod("thermoo$getMinTemperature");
		int[] events1 = (int[]) c.state.computeIfAbsent("hits", k -> new int[2]); // {calls, positions of the last call}
		if (tick == 1) {
			neutralise(c, setTemperature);
			Object listener = java.lang.reflect.Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[] {callback}, (proxy, method, args) -> {
				if (method.getName().equals("render")) {
					events1[0]++;
					events1[1] = ((java.util.Collection<?>) args[2].getClass().getMethod("positions").invoke(args[2])).size();
				}
				return null;
			});
			@SuppressWarnings("unchecked")
			net.fabricmc.fabric.api.event.Event<Object> event = (net.fabricmc.fabric.api.event.Event<Object>) events.getField("AFTER_HEALTH_BAR").get(null);
			event.register(listener);
			var server = c.server();
			// the health bar is only drawn for a player in survival or adventure
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamemode survival @p"));
			c.state.put("survivalSince", 0);
			return null;
		}
		if (!c.state.containsKey("baselineElements")) {
			if ((int) getTemperature.invoke(mc.player) != 0 && tick < 100) return null;
			if (mc.gameMode == null || !mc.gameMode.canHurtPlayer()) {
				if (tick > 200) throw new Probe.Fail("the player never entered survival");
				return null;
			}
			// measured before the cold: temperature is neutral for the first ticks
			c.state.put("baselineElements", hudElements(mc));
			c.state.put("baselineCalls", events1[0]);
			var server = c.server();
			Object serverPlayer = c.serverPlayer();
			server.execute(() -> {
				try {
					setTemperature.invoke(serverPlayer, minTemperature.invoke(serverPlayer));
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			});
			return null;
		}
		if ((int) getTemperature.invoke(mc.player) >= 0) {
			if (tick > 300) throw new Probe.Fail("the client player never became cold (temperature " + getTemperature.invoke(mc.player) + ")");
			return null;
		}
		if (!c.state.containsKey("measured")) {
			int before = events1[0];
			int elements = hudElements(mc);
			c.state.put("measured", true);
			c.state.put("coldTick", tick);
			c.state.put("coldElements", elements);
			c.state.put("calls", events1[0] - before);
			mc.options.pauseOnLostFocus = false;
			c.state.put("camera", mc.options.getCameraType());
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		}
		int shot = (int) c.state.get("coldTick") + 30;
		if (tick == shot) {
			Path dir = FabricLoader.getInstance().getGameDir().resolve("mms-uproot").resolve("screenshots");
			Files.createDirectories(dir);
			Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
				try (image) {
					image.writeToFile(dir.resolve("frostiful-cold-hud.png"));
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			});
		}
		if (tick < shot + 10) return null;
		// the camera was the player's choice (and Perspective API keeps it in its config): put it back, and the temperature to neutral
		mc.options.setCameraType((CameraType) c.state.get("camera"));
		var serverPlayer = c.serverPlayer();
		c.server().execute(() -> {
			try {
				setTemperature.invoke(serverPlayer, 0);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
		int calls = (int) c.state.get("calls");
		int baseline = (int) c.state.get("baselineElements");
		int cold = (int) c.state.get("coldElements");
		if (calls == 0) throw new Probe.Fail("Thermoo's after-health-bar event was not called while drawing the HUD with a cold player");
		if (events1[1] <= 0) throw new Probe.Fail("the event got no heart positions");
		if (cold <= baseline) throw new Probe.Fail("the cold HUD has " + cold + " elements, no more than the " + baseline + " of the neutral HUD");
		return "cold player (temperature " + getTemperature.invoke(mc.player) + "): Thermoo's event ran " + calls + " time(s) with " + events1[1] + " heart positions; the HUD holds "
			+ cold + " elements against " + baseline + " when neutral; screenshot run/mms-uproot/screenshots/frostiful-cold-hud.png is for a person to judge";
	}

	/**
	 * Frostiful's hook around the view bob: the handler merged into the game renderer shakes the pose it is given when the player is shivering
	 * (and leaves it alone when not), and then makes the real call with the arguments the game's bobView takes now. It is run directly with a
	 * recording stand-in for the call.
	 */
	private static String shiverShake(Probe.Ctx c, int tick) throws Exception {
		requireMod();
		Minecraft mc = c.mc;
		if (mc.player == null) return null;
		ClassLoader loader = FrostifulClientProbes.class.getClassLoader();
		Class<?> aware = Class.forName("com.github.thedeathlycow.thermoo.api.temperature.TemperatureAware", true, loader);
		java.lang.reflect.Method setTemperature = aware.getMethod("thermoo$setTemperature", int.class);
		java.lang.reflect.Method getTemperature = aware.getMethod("thermoo$getTemperature");
		java.lang.reflect.Method minTemperature = aware.getMethod("thermoo$getMinTemperature");
		if (tick == 1) {
			neutralise(c, setTemperature);
			return null;
		}
		if (!c.state.containsKey("neutral")) {
			if ((int) getTemperature.invoke(mc.player) != 0 && tick < 100) return null;
			// first with a neutral player: the pose stays as it was
			c.state.put("neutral", shakeOnce(mc));
			Object serverPlayer = c.serverPlayer();
			c.server().execute(() -> {
				try {
					setTemperature.invoke(serverPlayer, minTemperature.invoke(serverPlayer));
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			});
			return null;
		}
		if ((int) getTemperature.invoke(mc.player) >= 0) {
			if (tick > 300) throw new Probe.Fail("the client player never became cold");
			return null;
		}
		Object[] neutral = (Object[]) c.state.get("neutral");
		Object[] cold = shakeOnce(mc);
		Object serverPlayer = c.serverPlayer();
		c.server().execute(() -> {
			try {
				setTemperature.invoke(serverPlayer, 0);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
		if (!(boolean) neutral[1] || !(boolean) cold[1]) throw new Probe.Fail("the wrapped call was not made with (renderer, camera state, pose): neutral " + neutral[1] + ", cold " + cold[1]);
		if ((boolean) neutral[0]) throw new Probe.Fail("the pose of a player who is not shivering was moved");
		if (!(boolean) cold[0]) throw new Probe.Fail("the pose of a shivering player was not moved by the hook");
		return "with a neutral player the pose is untouched; with a cold one the hook translates it, and both pass (renderer, camera state, pose) to the real bobView";
	}

	/** Puts the server player at a neutral temperature, whatever an earlier run left in the world. */
	private static void neutralise(Probe.Ctx c, java.lang.reflect.Method setTemperature) {
		Object serverPlayer = c.serverPlayer();
		c.server().execute(() -> {
			try {
				setTemperature.invoke(serverPlayer, 0);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	/** Runs the merged handler once; {pose changed, call arguments as the game's bobView wants them}. */
	private static Object[] shakeOnce(Minecraft mc) throws Exception {
		Class<?> renderer = net.minecraft.client.renderer.GameRenderer.class;
		java.lang.reflect.Method handler = null;
		for (java.lang.reflect.Method m : renderer.getDeclaredMethods()) if (m.getName().contains("shakeViewWhenShivering")) handler = m;
		if (handler == null) throw new Probe.Fail("GameRenderer has no merged handler shakeViewWhenShivering");
		handler.setAccessible(true);
		PoseStack pose = new PoseStack();
		CameraRenderState camera = new CameraRenderState();
		org.joml.Matrix4f before = new org.joml.Matrix4f(pose.last().pose());
		boolean[] shape = {false};
		com.llamalad7.mixinextras.injector.wrapoperation.Operation<Object> real = args -> {
			shape[0] = args.length == 3 && args[0] == mc.gameRenderer && args[1] == camera && args[2] == pose;
			return null;
		};
		handler.invoke(mc.gameRenderer, mc.gameRenderer, camera, pose, real, camera);
		return new Object[] {!before.equals(pose.last().pose()), shape[0]};
	}

	private static final String[][] SCENES = {
		{"icicles", "0 -3 30"},
		{"entities", "0 0 -150"},
		{"skates", "0 0 0"}
	};

	private static void run(Probe.Ctx c, String command) {
		var server = c.server();
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute as @p at @s run " + command));
	}

	/** Builds a small scene in front of the player, waits for it to draw, and saves the framebuffer: icicles, then the entities, then the skates. */
	private static String screenshots(Probe.Ctx c, int tick) throws IOException {
		requireMod();
		Minecraft mc = c.mc;
		Path dir = FabricLoader.getInstance().getGameDir().resolve("mms-uproot").resolve("screenshots");
		@SuppressWarnings("unchecked")
		List<String> saved = (List<String>) c.state.computeIfAbsent("saved", k -> new ArrayList<String>());
		if (tick == 1) {
			Files.createDirectories(dir);
			c.state.put("camera", mc.options.getCameraType());
			mc.options.pauseOnLostFocus = false;
			run(c, "gamemode creative @p");
			// the test world's spawn sits under a large platform, so build the scene in open sky at noon to keep it lit
			var p = mc.player.position();
			c.state.put("home", p.x + " " + p.y + " " + p.z);
			run(c, "time set noon");
			run(c, "weather clear");
			run(c, "gamerule advance_time false");
			run(c, "tp @p ~ 300 ~");
			run(c, "fill ~-8 ~-1 ~-8 ~8 ~-1 ~16 minecraft:smooth_stone");
			run(c, "fill ~-8 ~ ~-8 ~8 ~6 ~16 minecraft:air");
			run(c, "tp @p ~ ~ ~ 0 5");
			return null;
		}
		int[] stage = {20, 100, 180};
		if (tick == stage[0] - 5) {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			// icicles: hanging ones on a ceiling bar, standing ones on the floor, every thickness
			run(c, "fill ~-4 ~4 ~3 ~4 ~4 ~3 minecraft:stone");
			int x = -4;
			for (String thickness : List.of("tip", "frustum", "middle", "base")) {
				run(c, "setblock ~" + x + " ~3 ~3 frostiful:icicle[thickness=" + thickness + ",vertical_direction=down]");
				run(c, "setblock ~" + (x + 5) + " ~ ~3 frostiful:icicle[thickness=" + thickness + ",vertical_direction=up]");
				x++;
			}
			run(c, "setblock ~-2 ~ ~6 frostiful:packed_snow_block");
			run(c, "setblock ~2 ~ ~6 frostiful:brittle_ice");
		}
		if (tick == stage[1] - 5) {
			run(c, "kill @e[type=!minecraft:player,distance=..30]");
			int x = -6;
			for (String entity : List.of("frostologer", "chillager", "biter")) {
				run(c, "summon frostiful:" + entity + " ~" + x + " ~ ~5 {NoAI:1b}");
				x += 6;
			}
			run(c, "summon frostiful:packed_snowball ~-3 ~2 ~8");
			run(c, "summon frostiful:thrown_icicle ~3 ~2 ~8");
			run(c, "summon frostiful:freezing_wind ~0 ~1 ~11 {NoAI:1b}");
		}
		if (tick == stage[2] - 5) {
			run(c, "kill @e[type=!minecraft:player,distance=..30]");
			run(c, "item replace entity @p armor.feet with frostiful:ice_skates");
			run(c, "summon minecraft:zombie ~-2 ~ ~4 {NoAI:1b,equipment:{feet:{id:\"frostiful:armored_ice_skates\",count:1}}}");
			run(c, "summon minecraft:armor_stand ~2 ~ ~4 {equipment:{feet:{id:\"frostiful:ice_skates\",count:1}}}");
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
		}
		for (int i = 0; i < stage.length; i++) {
			if (tick == stage[i] + 40) {
				String name = SCENES[i][0];
				Path out = dir.resolve("frostiful-" + name + ".png");
				boolean[] done = {false};
				Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
					try (image) {
						image.writeToFile(out);
						synchronized (saved) {
							saved.add(out.toString());
						}
					} catch (IOException e) {
						throw new IllegalStateException(e);
					} finally {
						done[0] = true;
					}
				});
			}
		}
		if (tick < stage[2] + 120) return null;
		mc.options.setCameraType((CameraType) c.state.get("camera"));
		run(c, "kill @e[type=!minecraft:player,distance=..30]");
		run(c, "gamerule advance_time true");
		run(c, "tp @p " + c.state.get("home"));
		if (saved.size() < SCENES.length) throw new Probe.Fail("only " + saved.size() + " of " + SCENES.length + " screenshots were written: " + saved);
		return "saved for a person to look at (not judged here): " + saved;
	}
}
