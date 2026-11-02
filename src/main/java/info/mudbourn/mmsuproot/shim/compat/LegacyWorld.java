package info.mudbourn.mmsuproot.shim.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import info.mudbourn.mmsuproot.shim.feature.ConfiguredFeature;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** World calls that moved or changed argument shape. */
public final class LegacyWorld {
	private static volatile MinecraftServer server;

	private LegacyWorld() { }

	/** {@code BlockPos.getBottomCenter()}: the middle of the block's floor. */
	public static Vec3 getBottomCenter(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
	}

	/** The running server's registries, for data.gen to serialize item components faithfully; null before a server is up. */
	public static RegistryAccess registries() {
		MinecraftServer s = server;
		return s == null ? null : s.registryAccess();
	}

	/** Remembers the running server, for calls that used to find it through an object that no longer leads there. */
	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
	}

	/**
	 * {@code WorldData.worldGenOptions()}: the world options now sit in the world generation settings of the server,
	 * which the caller reached the world data through.
	 */
	public static WorldOptions worldGenOptions(WorldData data) {
		MinecraftServer s = server;
		if (s == null) throw new IllegalStateException("no server is running");
		return s.getWorldGenSettings().options();
	}

	/**
	 * {@code Level.getDayTime()}. 26.3 has several world clocks, but 1.21.11 kept one day time in the level data and
	 * every dimension read it, so this is the overworld's clock whichever level asks, not the default clock of the level.
	 */
	public static long getDayTime(Level level) {
		return level.getOverworldClockTime();
	}

	/** {@code StructureManager.startsForStructure(SectionPos, Structure)}, now taking the chunk coordinates. */
	public static List<StructureStart> startsForStructure(StructureManager manager, SectionPos section, Structure structure) {
		return manager.startsForStructure(section.x(), section.z(), structure);
	}

	/** {@code RandomState.sampler()}, now made on demand; an uncached context is enough for a point lookup. */
	public static Climate.Sampler sampler(RandomState state) {
		return state.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
	}

	/** {@code ServerLevel.structureManager()} gave the template manager; that one is {@code getStructureTemplateManager()} now. */
	public static StructureTemplateManager templateManager(ServerLevel level) {
		return level.getStructureTemplateManager();
	}

	/** {@code Structure.generate(...)} without the climate sampler, which 26.3 takes after the biome source. */
	public static StructureStart generate(Structure structure, Holder<Structure> holder, ResourceKey<Level> dimension, RegistryAccess registries,
			ChunkGenerator generator, BiomeSource biomes, RandomState randomState, StructureTemplateManager templates, long seed, ChunkPos chunk,
			int references, LevelHeightAccessor height, Predicate<Holder<Biome>> validBiome) {
		return structure.generate(holder, dimension, registries, generator, biomes, sampler(randomState), randomState, templates, seed, chunk,
				references, height, validBiome);
	}

	/** {@code BiomeSource.getNoiseBiome(x, y, z, sampler)}, now answered by a resolver made from the sampler. */
	public static Holder<Biome> getNoiseBiome(BiomeSource source, int x, int y, int z, Climate.Sampler sampler) {
		return source.createResolver(sampler).getNoiseBiome(x, y, z);
	}

	/**
	 * {@code BlockStateProvider.getState(random, pos)}, now also taking the level. The providers that
	 * Minecraft ships and the mods use ignore it for a plain pick, so none is passed.
	 */
	public static BlockState getState(BlockStateProvider provider, RandomSource random, BlockPos pos) {
		return provider.getState(null, random, pos);
	}

	/** {@code StructureManager.hasAnyStructureAt(pos)}: whether any structure has a piece's chunk reference at the position. */
	public static boolean hasAnyStructureAt(StructureManager manager, BlockPos pos) {
		return !manager.getAllStructuresAt(pos).isEmpty();
	}

	/** {@code BlockPos.getCenter()}, now a static on {@link Vec3}. */
	public static Vec3 getCenter(BlockPos pos) {
		return Vec3.atCenterOf(pos);
	}

	/** {@code SimplexNoise.getValue(x, y)}, now {@code get} and a float. */
	public static double getValue(SimplexNoise noise, double x, double y) {
		return noise.get(x, y);
	}

	/** {@code SimplexNoise.getValue(x, y, z)}, now {@code get} and a float. */
	public static double getValue(SimplexNoise noise, double x, double y, double z) {
		return noise.get(x, y, z);
	}

	/** {@code PlacementModifier.getPositions(context, random, pos)}, now a callback; the positions are collected in order. */
	public static Stream<BlockPos> getPositions(PlacementModifier modifier, PlacementContext context, RandomSource random, BlockPos pos) {
		List<BlockPos> out = new ArrayList<>();
		modifier.modify(context, random, pos, out::add);
		return out.stream();
	}

	/**
	 * {@code BiomeGenerationSettings.getFlowerFeatures()}: the flowers a bone meal can grow, as the old configured
	 * features that wrap a patch. 26.3 keeps the features themselves, which place on their own.
	 */
	public static List<?> getFlowerFeatures(BiomeGenerationSettings settings) {
		List<ConfiguredFeature<?, ?>> out = new ArrayList<>();
		for (Feature feature : settings.getBoneMealFeatures()) out.add(ConfiguredFeature.patchOf(feature));
		return out;
	}

	/** {@code ChatFormatting.getColor()}: the colour is a text colour now. Null for the formats that have none. */
	public static Integer getColor(ChatFormatting format) {
		TextColor color = TextColor.fromLegacyFormat(format);
		return color == null ? null : color.getValue();
	}
}
