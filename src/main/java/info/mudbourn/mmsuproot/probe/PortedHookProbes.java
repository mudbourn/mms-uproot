package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import info.mudbourn.mmsuproot.shim.compat.OwoCopium;
import info.mudbourn.mmsuproot.shim.compat.OwoRemainders;
import info.mudbourn.mmsuproot.shim.noise.NoiseParameters;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * The ports that replaced a switched-off hook of a third-party mod: Peek's placeholders on Placeholder API 3.x, Biomes We've Gone's between-noise
 * state provider, owo-lib's recipe remainders and copium ore hook. What can only be judged by looking at the game (cube maps, leaves, stripping) is left to the person.
 */
final class PortedHookProbes {
	private PortedHookProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("ported.peek-placeholders", PortedHookProbes::peek),
			Probe.once("ported.bwg-between-noise-provider", PortedHookProbes::bwgProvider),
			Probe.once("ported.owo-recipe-remainders", PortedHookProbes::remainders),
			Probe.once("ported.owo-copium-flush", PortedHookProbes::copium),
			Probe.eyes("ported.terraform-leaf-layer", "look at a Terraform tree's leaves (Bountiful Fish's) and confirm they draw cutout, with no solid black or white where the texture is transparent"),
			Probe.eyes("ported.strip-keeps-properties", "strip a log of Biomes We've Gone or Bountiful Fish that lies on its side with an axe and confirm the stripped log keeps its direction"),
			Probe.eyes("ported.owo-cube-map-output", "open an owo screen with a cube map component that draws into a texture and confirm it appears in its place and the main view is not overpainted"),
			Probe.eyes("ported.prometheus-fire", "set an entity alight with a Prometheus fire type (soul or copper fire) and confirm its flames use that fire's sprites"),
			Probe.eyes("ported.owo-blur-surface", "open an owo screen whose panel uses the blur surface (26.2 build of owo only) and confirm the area behind the panel is blurred"));
	}

	/** Peek registers five placeholders through a stand-in for a handler class Placeholder API 3.x dropped. */
	private static String peek(Probe.Ctx c) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("peekmod") || !FabricLoader.getInstance().isModLoaded("placeholder-api")) throw new Probe.Skip("Peek or Placeholder API is not installed");
		Class<?> placeholders = Class.forName("eu.pb4.placeholders.api.Placeholders", true, PortedHookProbes.class.getClassLoader());
		Method get = placeholders.getMethod("getCommonPlaceholder", Identifier.class);
		for (String path : List.of("peek_count", "peeked_count", "total_duration", "is_peeking", "is_private")) {
			if (get.invoke(null, Identifier.fromNamespaceAndPath("peekmod", path)) == null) throw new Probe.Fail("peekmod:" + path + " is not a registered placeholder");
		}
		return "the five peekmod placeholders are registered in Placeholder API";
	}

	/** The provider builds, samples its noise in 2D and 3D, picks within or outside by its thresholds, and its codec round-trips. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String bwgProvider(Probe.Ctx c) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("biomeswevegone")) throw new Probe.Skip("Biomes We've Gone is not installed");
		ClassLoader loader = PortedHookProbes.class.getClassLoader();
		if (!BuiltInRegistries.BLOCK_STATE_PROVIDER_TYPE.containsKey(Identifier.fromNamespaceAndPath("biomeswevegone", "between_noise_threshold_provider"))) {
			throw new Probe.Fail("biomeswevegone:between_noise_threshold_provider is not a registered state provider type");
		}
		Class<?> type = Class.forName("net.potionstudios.biomeswevegone.world.level.levelgen.feature.stateproviders.BetweenNoiseThresholdProvider", true, loader);
		var ctor = type.getConstructor(long.class, NoiseParameters.class, float.class, List.class, BlockStateProvider.class, BlockStateProvider.class, boolean.class);
		BlockStateProvider within = BlockStateProvider.of(Blocks.STONE);
		BlockStateProvider outside = BlockStateProvider.of(Blocks.DIRT);
		RandomSource random = RandomSource.create(1L);
		BlockPos pos = new BlockPos(13, 70, -21);
		for (boolean threeD : new boolean[] {false, true}) {
			BlockStateProvider all = (BlockStateProvider) ctor.newInstance(42L, new NoiseParameters(-3, 1.0, 1.0), 0.25F, List.of(UniformFloat.of(-10F, 10F)), within, outside, threeD);
			BlockStateProvider none = (BlockStateProvider) ctor.newInstance(42L, new NoiseParameters(-3, 1.0, 1.0), 0.25F, List.of(), within, outside, threeD);
			if (all.getState(null, random, pos).getBlock() != Blocks.STONE) throw new Probe.Fail("use3D=" + threeD + ": a threshold that covers every value did not pick the within provider");
			if (none.getState(null, random, pos).getBlock() != Blocks.DIRT) throw new Probe.Fail("use3D=" + threeD + ": no threshold did not pick the outside provider");
		}
		BlockStateProvider provider = (BlockStateProvider) ctor.newInstance(7L, new NoiseParameters(-2, 1.0), 0.5F, List.of(UniformFloat.of(-10F, 10F)), within, outside, false);
		MapCodec<BlockStateProvider> codec = (MapCodec<BlockStateProvider>) type.getField("DIRECT_CODEC").get(null);
		JsonElement json = codec.codec().encodeStart(JsonOps.INSTANCE, provider).getOrThrow(IllegalStateException::new);
		BlockStateProvider back = codec.codec().parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new);
		if (back.getState(null, random, pos).getBlock() != Blocks.STONE) throw new Probe.Fail("the provider read back from " + json + " picked the wrong state");
		return "the provider samples 2D and 3D noise, picks by its thresholds and reads back what its codec wrote";
	}

	/** An {@code owo:remainders} entry of a recipe file reaches owo's storage, keyed by the recipe. */
	private static String remainders(Probe.Ctx c) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("owo")) throw new Probe.Skip("owo-lib is not installed");
		Identifier id = Identifier.fromNamespaceAndPath("mms_uproot", "probe_remainders");
		JsonObject recipe = new JsonObject();
		JsonObject remainders = new JsonObject();
		remainders.addProperty("minecraft:water_bucket", "minecraft:bucket");
		recipe.add("owo:remainders", remainders);
		OwoRemainders.read(ResourceKey.create(Registries.RECIPE, id), recipe);
		Class<?> storage = Class.forName("io.wispforest.owo.util.RecipeRemainderStorage", true, PortedHookProbes.class.getClassLoader());
		Method get = storage.getMethod("get", Identifier.class);
		Map<Item, ItemStack> stored = (Map<Item, ItemStack>) get.invoke(null, id);
		if (stored == null || !stored.containsKey(Items.WATER_BUCKET) || !stored.get(Items.WATER_BUCKET).is(Items.BUCKET)) throw new Probe.Fail("owo holds " + stored + " for the recipe");
		return "water_bucket leaves a bucket, as the recipe file said";
	}

	/** A block owo has on its copium list is written through the level when the ore feature is done. */
	private static String copium(Probe.Ctx c) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("owo")) throw new Probe.Skip("owo-lib is not installed");
		Class<?> owo = Class.forName("io.wispforest.owo.util.Maldenhagen", true, PortedHookProbes.class.getClassLoader());
		owo.getMethod("injectCopium", net.minecraft.world.level.block.Block.class).invoke(null, Blocks.GOLD_BLOCK);
		BlockPos pos = new BlockPos(0, 100, 0);
		BlockState state = Blocks.GOLD_BLOCK.defaultBlockState();
		return c.onServer(() -> {
			var level = c.server().overworld();
			OwoCopium.note(pos, state);
			OwoCopium.note(pos.above(), Blocks.STONE.defaultBlockState()); // not on the list: left alone
			OwoCopium.flush(level);
			BlockState written = level.getBlockState(pos);
			if (written.getBlock() != Blocks.GOLD_BLOCK) throw new Probe.Fail("the noted block was not written, the position holds " + written);
			if (level.getBlockState(pos.above()).getBlock() == Blocks.STONE) throw new Probe.Fail("a block that is not on the list was written");
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			return "a listed block is written through the level at the end of the feature, an unlisted one is not";
		});
	}
}
