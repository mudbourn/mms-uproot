package info.mudbourn.mmsuproot.probe;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.mojang.serialization.Dynamic;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;

/**
 * Cardinal Components API (1.21.11 build). It is reached by reflection: nothing of the mod is in this repo.
 */
final class CcaProbes {
	private static final String UNKNOWN = "uproot_probe:unknown";
	private static final String PROVIDER = "org.ladysnake.cca.api.v3.component.ComponentProvider";

	private CcaProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("cca.providers-on-game-classes", CcaProbes::providers),
			Probe.once("cca.level-data-save-and-reload", CcaProbes::levelRoundTrip));
	}

	private static Object container(Object holder) throws Exception {
		Class<?> provider = Class.forName(PROVIDER, true, CcaProbes.class.getClassLoader());
		if (!provider.isInstance(holder)) throw new Probe.Fail(holder.getClass().getName() + " is not a ComponentProvider");
		Object container = provider.getMethod("getComponentContainer").invoke(holder);
		if (container == null) throw new Probe.Fail(holder.getClass().getSimpleName() + " has no component container");
		return container;
	}

	/** The mixins of the entity, world, chunk, block, level and scoreboard modules each made a game class a provider with a container. */
	private static String providers(Probe.Ctx c) {
		if (!FabricLoader.getInstance().isModLoaded("cardinal-components-base")) throw new Probe.Skip("Cardinal Components is not installed");
		return c.onServer(() -> {
			MinecraftServer server = c.server();
			StringBuilder out = new StringBuilder();
			var player = server.getPlayerList().getPlayers().get(0);
			container(player);
			out.append("entity ");
			container(player.level());
			out.append("world ");
			LevelChunk chunk = player.level().getChunkAt(player.blockPosition());
			container(chunk);
			out.append("chunk ");
			BlockEntity chest = Blocks.CHEST.defaultBlockState().hasBlockEntity() ? ((net.minecraft.world.level.block.EntityBlock) Blocks.CHEST).newBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()) : null;
			if (chest == null) throw new Probe.Fail("no chest block entity to look at");
			container(chest);
			out.append("block ");
			container(server.getWorldData());
			out.append("level ");
			container(server.getScoreboard());
			out.append("scoreboard");
			return "components containers present on: " + out;
		});
	}

	/**
	 * The save and load paths of the level data carry CCA's injections: a world copy is written by the real save path and read back by the
	 * real load path, with a component entry nobody registered added to its level.dat; CCA's read wrap runs when it reports that entry as
	 * unknown, and the level data it returns has a container. No installed mod registers a level component, so no component data is
	 * written or compared: this shows the paths run, not that data survives them.
	 */
	private static String levelRoundTrip(Probe.Ctx c) {
		if (!FabricLoader.getInstance().isModLoaded("cardinal-components-level")) throw new Probe.Skip("Cardinal Components (level) is not installed");
		return c.onServer(() -> {
			MinecraftServer server = c.server();
			var access = (PrimaryLevelData) server.getWorldData();
			container(access);
			CompoundTag saved = access.createTag(UUID.randomUUID());
			if (saved.isEmpty()) throw new Probe.Fail("the save path produced an empty tag");

			Path source = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
			Path saves = null;
			try {
				saves = Files.createTempDirectory("uproot-cca");
				copyTree(source, saves.resolve("copy"));
				LevelStorageSource storage = LevelStorageSource.createDefault(saves);
				try (LevelStorageSource.LevelStorageAccess levelAccess = storage.createAccess("copy")) {
					levelAccess.saveDataTag(access);
					CompoundTag root = (CompoundTag) levelAccess.getUnfixedDataTag(false).convert(NbtOps.INSTANCE).getValue();
					CompoundTag unknown = new CompoundTag();
					unknown.put(UNKNOWN, new CompoundTag());
					root.put("cardinal_components", unknown);
					Dynamic<?> dynamic = new Dynamic<>(NbtOps.INSTANCE, root);
					List<String> warnings = new java.util.concurrent.CopyOnWriteArrayList<>();
					org.apache.logging.log4j.core.LoggerContext logs = (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
					org.apache.logging.log4j.core.appender.AbstractAppender listener = new org.apache.logging.log4j.core.appender.AbstractAppender("uproot-cca-probe", null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
						@Override
						public void append(org.apache.logging.log4j.core.LogEvent event) {
							if (event.getMessage().getFormattedMessage().contains(UNKNOWN)) warnings.add(event.getMessage().getFormattedMessage());
						}
					};
					listener.start();
					logs.getConfiguration().getRootLogger().addAppender(listener, null, null);
					logs.updateLoggers();
					var stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
					LevelDataAndDimensions loaded = LevelStorageSource.getLevelDataAndDimensions(levelAccess, dynamic, access.getDataConfiguration(), stems, server.registryAccess());
					PrimaryLevelData reloaded = (PrimaryLevelData) loaded.worldDataAndGenSettings().data();
					logs.getConfiguration().getRootLogger().removeAppender("uproot-cca-probe");
					logs.updateLoggers();
					if (warnings.isEmpty()) throw new Probe.Fail("CCA's read wrap did not run: no warning about the unknown component " + UNKNOWN);
					container(reloaded);
					if (reloaded.createTag(UUID.randomUUID()).isEmpty()) throw new Probe.Fail("the reloaded level data saves an empty tag");
				}
			} catch (IOException e) {
				throw new Probe.Fail("could not write or read the world copy: " + e);
			} finally {
				if (saves != null) deleteTree(saves);
			}
			return "level.dat of a world copy was written by saveDataTag and read back by getLevelDataAndDimensions into level data with a component container, and CCA's read wrap reported the unknown entry (no level component exists in the installed mods, so no component data was compared)";
		});
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> files = Files.walk(from)) {
			for (Path p : (Iterable<Path>) files::iterator) {
				Path target = to.resolve(from.relativize(p).toString());
				if (Files.isDirectory(p)) Files.createDirectories(target);
				else if (!p.getFileName().toString().equals("session.lock")) Files.copy(p, target);
			}
		}
	}

	private static void deleteTree(Path root) {
		try (Stream<Path> files = Files.walk(root)) {
			files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		} catch (IOException ignored) {
			// scratch data in the temp folder
		}
	}
}
