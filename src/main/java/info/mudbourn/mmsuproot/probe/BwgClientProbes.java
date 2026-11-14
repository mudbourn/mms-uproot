package info.mudbourn.mmsuproot.probe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Biomes We've Gone's client side: renderers, baked block models, and screenshots of its biomes for a person to look at. */
final class BwgClientProbes {
	private static final String NS = "biomeswevegone";
	/** Biomes to photograph, in order of preference; the first ones that the locate probe found are used. */
	private static final List<String> WANTED = List.of("aspen_boreal", "bayou", "atacama_outback", "araucaria_savanna", "cypress_wetlands",
		"redwood_thicket", "jacaranda_jungle", "skyris_vale", "tropical_rainforest", "rose_fields", "weeping_witch_forest", "allium_shrubland");

	private BwgClientProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("bwg.client.entity-renderers", BwgClientProbes::renderers),
			Probe.once("bwg.client.block-models", BwgClientProbes::blockModels),
			Probe.steps("bwg.client.screenshots", 1800, BwgClientProbes::screenshots),
			Probe.eyes("bwg.client.looks", "open run/mms-uproot/screenshots/bwg-*.png: grass and leaf tint follows each biome, plants are transparent, tree shapes are whole, signs show their wood, and the pumpkin warden, oddion and man o' war look like their 1.21.11 selves"));
	}

	/** Every BWG entity type has a renderer, and the renderer extracts a state from a fresh entity and submits it. */
	private static String renderers(Probe.Ctx c) {
		Minecraft mc = c.mc;
		EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
		List<String> done = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
			if (!id.getNamespace().equals(NS)) continue;
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
			Entity entity = type.create(mc.level, EntitySpawnReason.COMMAND);
			if (entity == null) throw new Probe.Fail("could not create a client-side " + id);
			entity.setId(-2000 - done.size());
			EntityRenderer<?, ?> renderer = dispatcher.getRenderer(entity);
			if (renderer == null) throw new Probe.Fail("no renderer for " + id);
			EntityRenderState state = dispatcher.extractEntity(entity, 1.0F);
			if (state == null) throw new Probe.Fail("the renderer of " + id + " extracted no state");
			dispatcher.submit(state, new CameraRenderState(), 0.0, 0.0, 0.0, new PoseStack(), new SubmitNodeStorage());
			done.add(id.getPath() + "->" + renderer.getClass().getSimpleName());
		}
		if (done.isEmpty()) throw new Probe.Fail("BWG registered no entity types");
		return done.size() + " entity types extracted and submitted: " + done;
	}

	/** Every state of every BWG block has a baked model other than the missing one. */
	private static String blockModels(Probe.Ctx c) {
		BlockStateModelSet models = c.mc.getModelManager().getBlockStateModelSet();
		Object missing = models.missingModel();
		int states = 0, blocks = 0;
		TreeSet<String> bad = new TreeSet<>();
		for (Identifier id : BuiltInRegistries.BLOCK.keySet()) {
			if (!id.getNamespace().equals(NS)) continue;
			Block block = BuiltInRegistries.BLOCK.getValue(id);
			blocks++;
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				states++;
				if (models.get(state) == missing) bad.add(id.getPath());
			}
		}
		if (!bad.isEmpty()) throw new Probe.Fail(bad.size() + " of " + blocks + " BWG blocks have a state that bakes to the missing model: " + bad.stream().limit(30).toList());
		return blocks + " blocks, " + states + " states all resolve to a model other than the missing one";
	}

	private static void run(Probe.Ctx c, String command) {
		var server = c.server();
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "execute as @p at @s run " + command));
	}

	private static void shot(Probe.Ctx c, Path out, List<String> saved) {
		c.mc.gui.hud.getChat().clearMessages(false);
		Screenshot.takeScreenshot(c.mc.gameRenderer.mainRenderTarget(), image -> {
			try (image) {
				image.writeToFile(out);
				synchronized (saved) {
					saved.add(out.toString());
				}
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		});
	}

	/**
	 * Flies the player (spectator) to the surface of several located BWG biomes at noon and saves the framebuffer of each, then builds a row of
	 * BWG entities and signs on a platform in open sky and saves that. For a person to judge; the probe only checks that the files were written.
	 */
	@SuppressWarnings("unchecked")
	private static String screenshots(Probe.Ctx c, int tick) throws IOException {
		Minecraft mc = c.mc;
		Path dir = FabricLoader.getInstance().getGameDir().resolve("mms-uproot").resolve("screenshots");
		List<String> saved = (List<String>) c.state.computeIfAbsent("saved", k -> new ArrayList<String>());
		int perScene = 170;
		if (tick == 1) {
			Files.createDirectories(dir);
			c.state.put("camera", mc.options.getCameraType());
			c.state.put("home", mc.player.position().x + " " + mc.player.position().y + " " + mc.player.position().z);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			// command feedback would cover the pictures
			run(c, "gamerule send_command_feedback false");
			run(c, "gamemode spectator @p");
			run(c, "time set noon");
			run(c, "weather clear");
			run(c, "gamerule advance_time false");
			List<String> scenes = new ArrayList<>();
			for (String w : WANTED) if (BwgProbes.located().containsKey(NS + ":" + w)) scenes.add(w);
			for (String id : BwgProbes.located().keySet()) {
				if (scenes.size() >= 6) break;
				String path = id.substring(NS.length() + 1);
				if (!scenes.contains(path)) scenes.add(path);
			}
			c.state.put("scenes", scenes.subList(0, Math.min(6, scenes.size())));
			return null;
		}
		List<String> scenes = (List<String>) c.state.get("scenes");
		int scene = (tick - 2) / perScene;
		int step = (tick - 2) % perScene;
		if (scene < scenes.size()) {
			String biome = scenes.get(scene);
			if (step == 0) {
				BlockPos at = BwgProbes.located().get(NS + ":" + biome);
				BlockPos top = c.onServer(() -> {
					var level = c.server().overworld();
					level.getChunk(at.getX() >> 4, at.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);
					return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, at);
				});
				run(c, "tp @s " + top.getX() + " " + (top.getY() + 6) + " " + top.getZ() + " " + (scene * 60) + " 8");
			}
			if (step == perScene - 10) shot(c, dir.resolve("bwg-" + biome + ".png"), saved);
			return null;
		}
		int base = 2 + scenes.size() * perScene;
		int step2 = tick - base;
		if (step2 == 0) {
			run(c, "tp @s ~ 250 ~");
			run(c, "fill ~-12 ~-1 ~-4 ~12 ~-1 ~16 minecraft:grass_block");
			run(c, "fill ~-12 ~ ~-4 ~12 ~8 ~16 minecraft:air");
			run(c, "tp @s ~ ~2 ~ 0 12");
		}
		if (step2 == 20) {
			int x = -8;
			for (String entity : List.of("pumpkin_warden", "oddion", "man_o_war", "wreath", "aspen_boat")) {
				run(c, "summon " + NS + ":" + entity + " ~" + x + " ~-2 ~7 {NoAI:1b}");
				x += 4;
			}
			x = -10;
			for (String wood : List.of("aspen", "baobab", "cypress", "ebony", "jacaranda", "palm")) {
				run(c, "setblock ~" + x + " ~-2 ~3 " + NS + ":" + wood + "_sign[rotation=8]");
				run(c, "setblock ~" + (x + 1) + " ~-2 ~3 " + NS + ":" + wood + "_planks");
				run(c, "setblock ~" + (x + 1) + " ~-2 ~2 " + NS + ":" + wood + "_log");
				run(c, "setblock ~" + (x + 2) + " ~-2 ~3 " + NS + ":" + wood + "_leaves");
				x += 4;
			}
		}
		if (step2 == 110) shot(c, dir.resolve("bwg-entities-and-wood.png"), saved);
		if (step2 < 140) return null;
		mc.options.setCameraType((CameraType) c.state.get("camera"));
		run(c, "gamerule send_command_feedback true");
		run(c, "kill @e[type=!minecraft:player,distance=..40]");
		run(c, "gamemode creative @p");
		run(c, "gamerule advance_time true");
		run(c, "tp @p " + c.state.get("home"));
		if (saved.size() < scenes.size() + 1) throw new Probe.Fail("only " + saved.size() + " of " + (scenes.size() + 1) + " screenshots were written: " + saved);
		return "saved for a person to look at (not judged here): " + saved;
	}
}
