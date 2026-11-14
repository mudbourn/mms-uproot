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
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
			Probe.steps("bwg.place-structures", 1200, BwgProbes::structures),
			Probe.once("bwg.loot-tables-roll", BwgProbes::loot),
			Probe.steps("bwg.entities-spawn-and-tick", 600, BwgProbes::entities),
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
		if (from >= ids.size()) {
			if (!thrown.isEmpty()) throw new Probe.Fail(thrown.size() + " features threw: " + thrown.stream().limit(5).toList());
			int placed = ids.size() - notPlaced.size();
			if (placed < ids.size() * 0.6) throw new Probe.Fail("only " + placed + " of " + ids.size() + " features placed; not placed: " + notPlaced.stream().limit(40).toList());
			return placed + " of " + ids.size() + " configured features placed on real terrain, none threw; did not place (need other soil or surroundings): " + notPlaced;
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
				Identifier id = Identifier.parse(ids.get(i));
				Feature feature = registry.get(ResourceKey.create(Registries.FEATURE, id)).orElseThrow().value();
				try {
					boolean ok = feature.place(level, level.getChunkSource().getGenerator(), level.getRandom(), at);
					if (!ok) notPlaced.add(id.getPath() + "<" + feature.getClass().getSimpleName() + ">");
				} catch (Throwable t) {
					thrown.add(id.getPath() + ": " + t);
				}
			}
			return null;
		});
		return null;
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

	/** Spawns every BWG entity type on a platform, ticks them for 3 seconds and checks that they are alive and ticking. */
	private static String entities(Probe.Ctx c, int tick) {
		List<EntityType<?>> types = BuiltInRegistries.ENTITY_TYPE.stream().filter(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace().equals(NS)).toList();
		if (types.isEmpty()) throw new Probe.Fail("no BWG entity type is registered");
		if (tick == 1) {
			BlockPos center = platformCentre(c);
			c.onServer(() -> {
				ServerLevel level = c.server().overworld();
				platform(c, level, center, Blocks.GRASS_BLOCK);
				int x = -8;
				List<Integer> ids = new ArrayList<>();
				List<String> names = new ArrayList<>();
				for (EntityType<?> type : types) {
					Entity e = type.create(level, EntitySpawnReason.COMMAND);
					if (e == null) throw new Probe.Fail(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " could not be created");
					e.setPos(center.getX() + x, center.getY() + 1, center.getZ());
					level.addFreshEntity(e);
					ids.add(e.getId());
					names.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
					x += 4;
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
		// give the server 40 ticks of the slowest entity before judging (it may run behind the client)
		if (tick < 500 && c.onServer(() -> ids.stream().anyMatch(id -> {
			Entity e = c.server().overworld().getEntity(id);
			return e != null && e.tickCount < 45;
		}))) return null;
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			List<String> bad = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (int n = 0; n < ids.size(); n++) {
				Entity e = level.getEntity(ids.get(n));
				String name = spawned.get(n);
				if (e == null || !e.isAlive() || e.tickCount < 40) bad.add(name + " (alive=" + (e != null && e.isAlive()) + ", tickCount=" + (e == null ? -1 : e.tickCount) + ")");
				else names.add(name + " tick " + e.tickCount);
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
