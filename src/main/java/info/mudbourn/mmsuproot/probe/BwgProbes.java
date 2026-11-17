package info.mudbourn.mmsuproot.probe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Pair;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * Oh The Biomes We've Gone (with CorgiLib and TerraBlender): that every data file the mod ships is a loaded registry entry, that its
 * biomes generate in a fresh world, and that its features, structures, loot, blocks and entities work. Each probe is deterministic.
 */
final class BwgProbes {
	private static final String NS = "biomeswevegone";

	private BwgProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("bwg.registries-hold-every-data-file", BwgProbes::dataFiles),
			Probe.once("bwg.structure-templates-load", BwgProbes::templates),
			Probe.once("bwg.log-has-no-bwg-errors", BwgProbes::log),
			Probe.steps("bwg.locate-biomes", 1200, BwgProbes::locate),
			Probe.steps("bwg.chunks-generate-in-bwg-biomes", 1200, BwgProbes::generate),
			Probe.steps("bwg.place-every-feature", 3000, BwgProbes::features),
			Probe.once("bwg.plants-survive-on-every-legacy-dirt", BwgProbes::soils),
			Probe.steps("bwg.place-structures", 1200, BwgProbes::structures),
			Probe.once("bwg.loot-tables-roll", BwgProbes::loot),
			Probe.steps("bwg.entities-spawn-and-tick", 1200, BwgProbes::entities),
			Probe.once("bwg.blocks-and-items-register", BwgProbes::blocks));
	}

	private static Path root() {
		return FabricLoader.getInstance().getModContainer(NS).orElseThrow(() -> new Probe.Fail("biomeswevegone is not loaded")).getRootPaths().get(0);
	}

	/** Ids ("biomeswevegone:a/b") of the json (or other) files below one folder of the mod's data. */
	private static Set<String> files(String folder, String extension) {
		Path dir = root().resolve("data/" + NS + "/" + folder);
		if (!Files.isDirectory(dir)) return Set.of();
		try (Stream<Path> walk = Files.walk(dir)) {
			Set<String> out = new TreeSet<>();
			walk.filter(Files::isRegularFile).map(p -> dir.relativize(p).toString().replace('\\', '/')).filter(n -> n.endsWith(extension))
				.forEach(n -> out.add(NS + ":" + n.substring(0, n.length() - extension.length())));
			return out;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static <T> Set<String> loaded(HolderLookup.RegistryLookup<T> registry) {
		Set<String> out = new TreeSet<>();
		registry.listElementIds().forEach(key -> {
			if (key.identifier().getNamespace().equals(NS)) out.add(key.identifier().toString());
		});
		return out;
	}

	/** Every worldgen, loot, recipe and advancement file in the mod's data must be an entry in the game's registries after loading. */
	private static String dataFiles(Probe.Ctx c) {
		return c.onServer(() -> {
			var access = c.server().registryAccess();
			var lookup = c.server().reloadableRegistries().lookup();
			Map<String, Set<String>> loadedBy = new TreeMap<>();
			loadedBy.put("worldgen/biome", loaded(access.lookupOrThrow(Registries.BIOME)));
			loadedBy.put("worldgen/configured_feature", loaded(access.lookupOrThrow(Registries.FEATURE)));
			loadedBy.put("worldgen/placed_feature", loaded(access.lookupOrThrow(Registries.PLACED_FEATURE)));
			loadedBy.put("worldgen/structure", loaded(access.lookupOrThrow(Registries.STRUCTURE)));
			loadedBy.put("worldgen/structure_set", loaded(access.lookupOrThrow(Registries.STRUCTURE_SET)));
			loadedBy.put("worldgen/template_pool", loaded(access.lookupOrThrow(Registries.TEMPLATE_POOL)));
			loadedBy.put("worldgen/processor_list", loaded(access.lookupOrThrow(Registries.PROCESSOR_LIST)));
			loadedBy.put("damage_type", loaded(access.lookupOrThrow(Registries.DAMAGE_TYPE)));
			loadedBy.put("jukebox_song", loaded(access.lookupOrThrow(Registries.JUKEBOX_SONG)));
			loadedBy.put("timeline", loaded(access.lookupOrThrow(Registries.TIMELINE)));
			loadedBy.put("loot_table", loaded(lookup.lookupOrThrow(Registries.LOOT_TABLE)));
			Set<String> recipes = new TreeSet<>();
			c.server().getRecipeManager().getRecipes().forEach(r -> {
				if (r.id().identifier().getNamespace().equals(NS)) recipes.add(r.id().identifier().toString());
			});
			loadedBy.put("recipe", recipes);
			Set<String> advancements = new TreeSet<>();
			c.server().getAdvancements().getAllAdvancements().forEach(a -> {
				if (a.id().getNamespace().equals(NS)) advancements.add(a.id().toString());
			});
			loadedBy.put("advancement", advancements);
			StringBuilder missing = new StringBuilder();
			StringBuilder counts = new StringBuilder();
			int total = 0;
			for (var e : loadedBy.entrySet()) {
				Set<String> shipped = files(e.getKey(), ".json");
				Set<String> absent = new TreeSet<>(shipped);
				absent.removeAll(e.getValue());
				total += shipped.size();
				counts.append(e.getKey()).append(' ').append(shipped.size() - absent.size()).append('/').append(shipped.size()).append("; ");
				if (!absent.isEmpty()) missing.append(e.getKey()).append(" missing ").append(absent.size()).append(' ').append(absent.stream().limit(6).toList()).append(" | ");
			}
			if (missing.length() > 0) throw new Probe.Fail(missing + "(" + counts + ")");
			return total + " data files, all loaded: " + counts;
		});
	}

	/** Every structure template (nbt) the mod ships can be read by the template manager. */
	private static String templates(Probe.Ctx c) {
		Set<String> shipped = files("structure", ".nbt");
		return c.onServer(() -> {
			List<String> bad = new ArrayList<>();
			List<String> corrupt = new ArrayList<>();
			for (String id : shipped) {
				var t = c.server().getStructureTemplateManager().get(Identifier.parse(id));
				if (t.isEmpty() || t.get().getSize().getX() <= 0) {
					if (corrupt(id)) corrupt.add(id);
					else bad.add(id);
				}
			}
			if (!bad.isEmpty()) throw new Probe.Fail(bad.size() + " of " + shipped.size() + " templates did not load, first: " + bad.stream().limit(6).toList());
			return (shipped.size() - corrupt.size()) + " of " + shipped.size() + " structure templates load; broken in the mod's own jar (truncated gzip, no data file uses them): " + corrupt;
		});
	}

	/** Whether a template file in the mod's jar is itself unreadable (a truncated gzip stream), whatever the game does with it. */
	private static boolean corrupt(String id) {
		Path file = root().resolve("data/" + NS + "/structure/" + id.substring(NS.length() + 1) + ".nbt");
		try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(file))) {
			in.readAllBytes();
			return false;
		} catch (IOException e) {
			return true;
		}
	}

	/** Data load problems show up as log lines; none of them may name BWG, CorgiLib or TerraBlender, or be a parse failure. */
	private static String log(Probe.Ctx c) throws IOException {
		Path file = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
		List<String> bad = new ArrayList<>();
		String[] words = {"biomeswevegone", "corgilib", "terrablender", "Parsing error", "Couldn't parse", "Failed to load", "Failed to parse", "Missing "};
		for (String line : Files.readAllLines(file)) {
			if (!line.contains("/WARN]") && !line.contains("/ERROR]")) continue;
			if (line.contains("Dead hook:") || line.contains("Can't keep up")) continue;
			for (String w : words) {
				if (line.contains(w)) {
					bad.add(line);
					break;
				}
			}
		}
		if (!bad.isEmpty()) throw new Probe.Fail(bad.size() + " warn/error lines: " + bad.stream().limit(5).map(l -> l.length() > 240 ? l.substring(0, 240) : l).toList());
		return "latest.log has no warn or error line about BWG, CorgiLib, TerraBlender or a failed data parse";
	}

	private static List<Holder.Reference<Biome>> bwgBiomes(Probe.Ctx c) {
		return c.server().registryAccess().lookupOrThrow(Registries.BIOME).listElements().filter(h -> h.key().identifier().getNamespace().equals(NS)).toList();
	}

	/** Where bwg.locate-biomes found each biome; the probes after it generate around these. */
	private static final Map<String, BlockPos> LOCATED = new TreeMap<>();

	static Map<String, BlockPos> located() {
		return LOCATED;
	}

	/** The overworld locate-biome search finds BWG biomes (one biome per tick, so the server is not held for long). */
	private static String locate(Probe.Ctx c, int tick) {
		List<Holder.Reference<Biome>> biomes = bwgBiomes(c);
		if (biomes.isEmpty()) throw new Probe.Fail("no biomeswevegone biome is registered");
		int i = tick - 1;
		Map<String, BlockPos> found = LOCATED;
		if (i < biomes.size()) {
			Holder.Reference<Biome> biome = biomes.get(i);
			c.onServer(() -> {
				ServerLevel level = c.server().overworld();
				Pair<BlockPos, Holder<Biome>> hit = level.findClosestBiome3d(h -> h.is(biome.key()), BlockPos.ZERO, 3000, 32, 64);
				if (hit != null) found.put(biome.key().identifier().toString(), hit.getFirst());
				return null;
			});
			return null;
		}
		if (found.size() < 8) throw new Probe.Fail("only " + found.size() + " of " + biomes.size() + " BWG biomes were found within 3000 blocks: " + found.keySet());
		Set<String> missing = new TreeSet<>();
		biomes.forEach(b -> {
			if (!found.containsKey(b.key().identifier().toString())) missing.add(b.key().identifier().getPath());
		});
		return found.size() + " of " + biomes.size() + " BWG biomes located within 3000 blocks of the origin; not found: " + missing;
	}

	/** Generates full chunks around a located spot of several BWG biomes and counts the BWG blocks that came out (surface and trees). */
	private static String generate(Probe.Ctx c, int tick) {
		Map<String, BlockPos> found = LOCATED;
		if (found.isEmpty()) throw new Probe.Fail("the locate probe found nothing to generate around");
		List<String> ids = new ArrayList<>(found.keySet());
		@SuppressWarnings("unchecked")
		Map<String, Integer> counts = (Map<String, Integer>) c.state.computeIfAbsent("counts", k -> new TreeMap<String, Integer>());
		int i = tick - 1;
		if (i < Math.min(ids.size(), 12)) {
			String id = ids.get(i);
			BlockPos at = found.get(id);
			int n = c.onServer(() -> {
				ServerLevel level = c.server().overworld();
				int bwg = 0;
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						ChunkAccess chunk = level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz, ChunkStatus.FULL, true);
						BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
						for (int x = 0; x < 16; x++) {
							for (int z = 0; z < 16; z++) {
								int top = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
								for (int y = top; y > top - 40 && y > level.getMinY(); y--) {
									p.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
									if (BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(p).getBlock()).getNamespace().equals(NS)) bwg++;
								}
							}
						}
					}
				}
				return bwg;
			});
			counts.put(id, n);
			return null;
		}
		long withBwg = counts.values().stream().filter(n -> n > 0).count();
		if (counts.isEmpty() || withBwg < counts.size() * 0.75) throw new Probe.Fail("BWG blocks per biome (5x5 chunks, top 40 blocks): " + counts);
		return counts.size() + " biomes generated 5x5 chunks each without error; BWG block counts " + counts;
	}

	private static final int PLATFORM_Y = 250;

	/** The platform's centre, above the player; read before a server task starts, since a task cannot wait on another one. */
	private static BlockPos platformCentre(Probe.Ctx c) {
		return c.onServer(() -> c.server().getPlayerList().getPlayers().get(0).blockPosition().atY(PLATFORM_Y));
	}

	private static void platform(Probe.Ctx c, ServerLevel level, BlockPos center, Block top) {
		for (int dx = -12; dx <= 12; dx++) {
			for (int dz = -12; dz <= 12; dz++) {
				level.setBlock(center.offset(dx, 0, dz), top.defaultBlockState(), 2 | 16);
				for (int dy = 1; dy <= 36; dy++) level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}
	}

	/**
	 * Empties the top blocks of the loaded columns around a point. The structures are placed at the platform's height and blend into
	 * the terrain around it by that column's height; a tree or peak that reaches the last block makes a lake write one above the
	 * world (BWG's LargeLakePiece.blendTerrain does, on 1.21.11 as well), which the test world's seed has near the platform.
	 */
	private static void clearBuildLimit(ServerLevel level, BlockPos center, int radius) {
		java.util.Set<net.minecraft.world.level.chunk.LevelChunk> chunks = new java.util.LinkedHashSet<>();
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
				if (!level.hasChunk(x >> 4, z >> 4)) continue;
				chunks.add(level.getChunk(x >> 4, z >> 4));
				for (int y = level.getMaxY() - 8; y <= level.getMaxY(); y++) {
					BlockPos at = new BlockPos(x, y, z);
					if (!level.getBlockState(at).isAir()) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | 16);
				}
			}
		}
		// the world-generation surface heightmap is only read from the blocks once, and setBlock does not keep it
		for (var chunk : chunks) net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG));
	}

	/**
	 * Places every configured feature of the mod on the real terrain, each in its own cell of a grid around the player (32 blocks apart,
	 * the chunks around it generated first), at the surface. An exception is a failure; "did not place" is counted, since a feature that
	 * wants other soil (sand, water) does not place on whatever the cell holds.
	 */
	private static String features(Probe.Ctx c, int tick) {
		List<String> ids = new ArrayList<>(files("worldgen/configured_feature", ".json"));
		int per = 12;
		int from = (tick - 1) * per;
		@SuppressWarnings("unchecked")
		List<String> notPlaced = (List<String>) c.state.computeIfAbsent("notPlaced", k -> new ArrayList<String>());
		@SuppressWarnings("unchecked")
		List<String> thrown = (List<String>) c.state.computeIfAbsent("thrown", k -> new ArrayList<String>());
		@SuppressWarnings("unchecked")
		Map<String, String> samples = (Map<String, String>) c.state.computeIfAbsent("samples", k -> new TreeMap<String, String>());
		if (from >= ids.size()) {
			if (!thrown.isEmpty()) throw new Probe.Fail(thrown.size() + " features threw: " + thrown.stream().limit(5).toList());
			int placed = ids.size() - notPlaced.size();
			if (placed < ids.size() * 0.6) throw new Probe.Fail("only " + placed + " of " + ids.size() + " features placed; not placed: " + notPlaced.stream().limit(40).toList() + "; one sample per feature type: " + samples);
			return placed + " of " + ids.size() + " configured features placed on real terrain, none threw; did not place (need sand, water or other surroundings): " + notPlaced;
		}
		BlockPos origin = platformCentre(c);
		c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			var registry = c.server().registryAccess().lookupOrThrow(Registries.FEATURE);
			for (int i = from; i < Math.min(ids.size(), from + per); i++) {
				int cx = (origin.getX() >> 4) + 2 * (i % 23) - 22;
				int cz = (origin.getZ() >> 4) + 2 * (i / 23) + 4;
				for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk(cx + dx, cz + dz, ChunkStatus.FULL, true);
				BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, new BlockPos(cx * 16 + 8, 0, cz * 16 + 8));
				groundCell(level, at);
				Identifier id = Identifier.parse(ids.get(i));
				Feature feature = registry.get(ResourceKey.create(Registries.FEATURE, id)).orElseThrow().value();
				try {
					boolean ok = feature.place(level, level.getChunkSource().getGenerator(), level.getRandom(), at);
					if (!ok) {
						notPlaced.add(id.getPath() + "<" + feature.getClass().getSimpleName() + ">");
						samples.putIfAbsent(feature.getClass().getSimpleName(), id.getPath() + " at " + at + " on " + level.getBlockState(at.below()) + " = " + feature);
					}
				} catch (Throwable t) {
					thrown.add(id.getPath() + "<" + feature.getClass().getSimpleName() + ">: " + t);
				}
			}
			return null;
		});
		return null;
	}

	/** The members of 1.21.11's #dirt tag (read from its data/minecraft/tags/block/dirt.json). */
	private static final List<Block> LEGACY_DIRT = List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.MYCELIUM,
			Blocks.ROOTED_DIRT, Blocks.MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS);

	/**
	 * Soil predicates, checked directly (the feature probe grounds every cell on grass, which hides a broken one): every BWG vegetation
	 * block that survives on plain dirt must survive on each of the other members of 1.21.11's #dirt as well. A plant that needs other
	 * soil (sand, water, a light level) fails on plain dirt too and is not judged.
	 */
	private static String soils(Probe.Ctx c) {
		BlockPos centre = platformCentre(c);
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			BlockPos pos = centre.above();
			level.getChunk(pos);
			int dirtPlants = 0, other = 0;
			List<String> failures = new ArrayList<>();
			for (Block block : BuiltInRegistries.BLOCK) {
				Identifier id = BuiltInRegistries.BLOCK.getKey(block);
				if (!id.getNamespace().equals(NS) || !(block instanceof VegetationBlock)) continue;
				BlockState plant = block.defaultBlockState();
				if (!survives(level, pos, plant, Blocks.DIRT)) {
					other++;
					continue;
				}
				dirtPlants++;
				List<String> refused = new ArrayList<>();
				for (Block soil : LEGACY_DIRT) {
					if (!survives(level, pos, plant, soil)) refused.add(BuiltInRegistries.BLOCK.getKey(soil).getPath());
				}
				if (!refused.isEmpty()) failures.add(id.getPath() + " refuses " + refused);
			}
			level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 2 | 16);
			if (dirtPlants < 10) throw new Probe.Fail("only " + dirtPlants + " BWG plants survive on dirt; the check is not reaching the plants");
			if (!failures.isEmpty()) throw new Probe.Fail(failures.size() + " of " + dirtPlants + " BWG plants that survive on dirt refuse another 1.21.11 dirt: " + failures.stream().limit(10).toList());
			return dirtPlants + " BWG plants survive on all " + LEGACY_DIRT.size() + " blocks of 1.21.11's #dirt (" + other + " other vegetation blocks need other soil or light)";
		});
	}

	private static boolean survives(ServerLevel level, BlockPos pos, BlockState plant, Block soil) {
		level.setBlock(pos.below(), soil.defaultBlockState(), 2 | 16);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
		level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
		return plant.canSurvive(level, pos);
	}

	/** A patch of grass over dirt with open air above it, so that plants and trees have the soil and room they need wherever the cell lies. */
	private static void groundCell(ServerLevel level, BlockPos at) {
		for (int dx = -10; dx <= 10; dx++) {
			for (int dz = -10; dz <= 10; dz++) {
				level.setBlock(at.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2 | 16);
				level.setBlock(at.offset(dx, -2, dz), Blocks.DIRT.defaultBlockState(), 2 | 16);
				for (int dy = 0; dy <= 40; dy++) level.setBlock(at.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}
	}

	private static String command(Probe.Ctx c, String command) {
		Vec3 pos = Vec3.atBottomCenterOf(platformCentre(c));
		return c.onServer(() -> {
			List<String> messages = new ArrayList<>();
			CommandSource source = new CommandSource() {
				@Override
				public void sendSystemMessage(Component message) {
					messages.add(message.getString());
				}

				@Override
				public boolean acceptsSuccess() {
					return true;
				}

				@Override
				public boolean acceptsFailure() {
					return true;
				}

				@Override
				public boolean shouldInformAdmins() {
					return false;
				}
			};
			ServerLevel level = c.server().overworld();
			CommandSourceStack stack = new CommandSourceStack(source, pos, Vec2.ZERO, level, PermissionSet.ALL_PERMISSIONS, Component.literal("probe"), c.server());
			try {
				int result = c.server().getCommands().getDispatcher().execute(command, stack);
				return result + " " + String.join(" / ", messages);
			} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
				return "FAILED " + e.getMessage();
			}
		});
	}

	/** /place structure for the mod's structures, on a platform in open sky (some need terrain; failures are counted, exceptions fail). */
	private static String structures(Probe.Ctx c, int tick) {
		List<String> ids = new ArrayList<>(files("worldgen/structure", ".json"));
		if (ids.isEmpty()) throw new Probe.Fail("no structure files");
		@SuppressWarnings("unchecked")
		List<String> ok = (List<String>) c.state.computeIfAbsent("ok", k -> new ArrayList<String>());
		@SuppressWarnings("unchecked")
		List<String> bad = (List<String>) c.state.computeIfAbsent("bad", k -> new ArrayList<String>());
		int i = tick - 1;
		if (i >= ids.size()) {
			if (ok.size() < ids.size() / 2) throw new Probe.Fail("only " + ok.size() + " of " + ids.size() + " structures placed; failed: " + bad);
			return ok.size() + " of " + ids.size() + " structures placed with /place structure; did not place: " + bad;
		}
		BlockPos center = platformCentre(c);
		c.onServer(() -> {
			platform(c, c.server().overworld(), center, Blocks.GRASS_BLOCK);
			// a structure needs its chunks loaded (the command refuses otherwise), and a big lake reaches well past 100 blocks: every loaded one.
			// Cleared before each placement, over the chunks loaded now: an earlier structure's trees and peaks, and chunks that loaded since, reach the limit again
			clearBuildLimit(c.server().overworld(), center, 208);
			return null;
		});
		String out = command(c, "place structure " + ids.get(i) + " ~ ~1 ~");
		if (out.startsWith("FAILED")) bad.add(ids.get(i).substring(NS.length() + 1) + " (" + out + ")");
		else ok.add(ids.get(i));
		return null;
	}

	/** Rolls every loot table of the mod with a context built from its parameter set where one can be made. */
	private static String loot(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			var lookup = c.server().reloadableRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE);
			int rolled = 0, withItems = 0, skipped = 0;
			List<String> thrown = new ArrayList<>();
			for (Holder.Reference<LootTable> ref : lookup.listElements().filter(h -> h.key().identifier().getNamespace().equals(NS)).toList()) {
				String path = ref.key().identifier().getPath();
				LootParams.Builder builder = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO);
				if (path.startsWith("blocks/")) {
					Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath(NS, path.substring(7))).orElse(null);
					if (block == null) {
						skipped++;
						continue;
					}
					BlockState state = block.defaultBlockState();
					builder.withParameter(LootContextParams.BLOCK_STATE, state).withParameter(LootContextParams.TOOL, new ItemStack(Items.DIAMOND_PICKAXE));
				} else if (path.startsWith("entities/")) {
					skipped++;
					continue;
				}
				try {
					var params = builder.create(ref.value().getParamSet());
					List<ItemStack> stacks = ref.value().getRandomItems(params, 1234L);
					rolled++;
					if (!stacks.isEmpty()) withItems++;
				} catch (IllegalArgumentException t) {
					// a table that needs an entity or other context this probe does not build
					if (t.getMessage() != null && t.getMessage().startsWith("Missing required parameters")) skipped++;
					else thrown.add(path + ": " + t);
				} catch (Throwable t) {
					thrown.add(path + ": " + t);
				}
			}
			if (!thrown.isEmpty()) throw new Probe.Fail(thrown.size() + " tables threw when rolled: " + thrown.stream().limit(5).toList());
			if (rolled == 0) throw new Probe.Fail("no loot table could be rolled");
			return rolled + " loot tables rolled without error (" + withItems + " gave items), " + skipped + " need an entity context and were not rolled";
		});
	}

	/**
	 * Spawns every BWG entity type on a platform, ticks them for 3 seconds and checks that they are alive and ticking. The player stands on the platform
	 * meanwhile (and goes back after): the platform is at y 250, and a mob that removes itself when far away (the man o' war does, always) is discarded
	 * on its first tick when the nearest player is more than 128 blocks off, which a player on the surface of a new world is.
	 */
	private static String entities(Probe.Ctx c, int tick) {
		List<EntityType<?>> types = BuiltInRegistries.ENTITY_TYPE.stream().filter(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace().equals(NS)).toList();
		if (types.isEmpty()) throw new Probe.Fail("no BWG entity type is registered");
		if (tick == 1) {
			BlockPos center = platformCentre(c);
			c.onServer(() -> {
				ServerLevel level = c.server().overworld();
				platform(c, level, center, Blocks.GRASS_BLOCK);
				ServerPlayer player = c.server().getPlayerList().getPlayers().get(0);
				c.state.put("entityPlayerBack", player.position());
				player.teleportTo(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
				int n = 0;
				List<Integer> ids = new ArrayList<>();
				List<String> names = new ArrayList<>();
				for (EntityType<?> type : types) {
					Entity e = type.create(level, EntitySpawnReason.COMMAND);
					if (e == null) throw new Probe.Fail(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " could not be created");
					// a grid on the platform (9 to a row), so every one is near the player and ticks
					e.setPos(center.getX() - 12 + 3 * (n % 9), center.getY() + 1, center.getZ() - 12 + 4 * (n / 9));
					level.addFreshEntity(e);
					ids.add(e.getId());
					names.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
					n++;
				}
				c.state.put("entityIds", ids);
				c.state.put("entityNames", names);
				return null;
			});
			return null;
		}
		@SuppressWarnings("unchecked")
		List<Integer> ids = (List<Integer>) c.state.get("entityIds");
		@SuppressWarnings("unchecked")
		List<String> spawned = (List<String>) c.state.get("entityNames");
		// an entity counts once it has been seen alive after 40 ticks: a wreath hangs on nothing here and takes itself away at its next survival
		// check (100 ticks), which a lagging server may reach before this probe looks again; give the slowest entity until tick 1000 to get there
		@SuppressWarnings("unchecked")
		java.util.Set<Integer> seen = (java.util.Set<Integer>) c.state.computeIfAbsent("seen", k -> new java.util.HashSet<Integer>());
		boolean done = c.onServer(() -> {
			for (int id : ids) {
				Entity e = c.server().overworld().getEntity(id);
				if (e != null && e.isAlive() && e.tickCount >= 40) seen.add(id);
			}
			return seen.size() == ids.size();
		});
		if (!done && tick < 1000) return null;
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			List<String> bad = new ArrayList<>();
			List<String> names = new ArrayList<>();
			net.minecraft.world.phys.Vec3 back = (net.minecraft.world.phys.Vec3) c.state.get("entityPlayerBack");
			if (back != null) c.server().getPlayerList().getPlayers().get(0).teleportTo(back.x, back.y, back.z);
			for (int n = 0; n < ids.size(); n++) {
				Entity e = level.getEntity(ids.get(n));
				String name = spawned.get(n);
				if (!seen.contains(ids.get(n))) bad.add(name + " (alive=" + (e != null && e.isAlive()) + ", tickCount=" + (e == null ? -1 : e.tickCount) + ")");
				else names.add(name);
				if (e != null) e.discard();
			}
			if (!bad.isEmpty()) throw new Probe.Fail("not alive and ticking: " + bad);
			return names.size() + " BWG entities spawned and ticked: " + names;
		});
	}

	/** Every BWG block has a registry entry with a default state, and the block items exist for the blocks that should have one. */
	private static String blocks(Probe.Ctx c) {
		int blocks = 0, withItem = 0;
		List<String> noItem = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			if (!id.getNamespace().equals(NS)) continue;
			blocks++;
			if (BuiltInRegistries.ITEM.containsKey(id)) withItem++;
			else noItem.add(id.getPath());
		}
		if (blocks < 500) throw new Probe.Fail("only " + blocks + " BWG blocks registered");
		List<String> badEntities = new ArrayList<>();
		int withEntity = 0;
		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			if (!id.getNamespace().equals(NS) || !(block instanceof net.minecraft.world.level.block.EntityBlock owner)) continue;
			withEntity++;
			var entity = owner.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
			if (entity == null || !entity.getType().isValid(block.defaultBlockState())) badEntities.add(id.getPath());
		}
		if (!badEntities.isEmpty()) throw new Probe.Fail(badEntities.size() + " BWG blocks make no block entity that accepts them: " + badEntities.stream().limit(10).toList());
		long items = BuiltInRegistries.ITEM.keySet().stream().filter(i -> i.getNamespace().equals(NS)).count();
		return blocks + " BWG blocks, " + withEntity + " of them with a block entity that accepts them (signs, hanging signs and the like), (" + withItem + " with a same-named item; without: " + noItem + "), " + items + " BWG items";
	}
}
