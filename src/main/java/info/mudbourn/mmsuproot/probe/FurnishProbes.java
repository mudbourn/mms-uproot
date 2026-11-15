package info.mudbourn.mmsuproot.probe;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Furnish (build 31 for 1.21.11): the furniture workbench recipes, sitting on a chair, a storage furniture's contents, and the block entity renderers. */
final class FurnishProbes {
	private static final String NAMESPACE = "furnish";

	private FurnishProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("furnish.workbench-recipes-craft", FurnishProbes::recipes),
			Probe.once("furnish.chair-seats-the-player", FurnishProbes::chair),
			Probe.once("furnish.cabinet-keeps-its-items", FurnishProbes::cabinet),
			Probe.once("furnish.client.block-entity-renderers", FurnishProbes::renderers));
	}

	private static void requireMod() {
		if (!FabricLoader.getInstance().isModLoaded(NAMESPACE)) throw new Probe.Skip("Furnish is not installed");
	}

	private static Block block(String path) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(NAMESPACE, path));
		if (block == net.minecraft.world.level.block.Blocks.AIR) throw new Probe.Fail("furnish:" + path + " is not registered");
		return block;
	}

	/**
	 * Every furniture-making recipe the world loaded (their own recipe class and serializer, over 26.3's single item recipe) matches the
	 * plank or block it is made from and yields the furniture it names.
	 */
	private static String recipes(Probe.Ctx c) {
		requireMod();
		return c.onServer(() -> {
			var level = c.server().overworld();
			int count = 0;
			List<String> bad = new ArrayList<>();
			for (var holder : c.server().getRecipeManager().getRecipes()) {
				if (!holder.id().identifier().getNamespace().equals(NAMESPACE)) continue;
				if (!(holder.value() instanceof SingleItemRecipe recipe)) continue;
				count++;
				ItemStack input = new ItemStack(recipe.input().items().findFirst().orElseThrow());
				var in = new SingleRecipeInput(input);
				ItemStack made = recipe.assemble(in);
				Identifier made_ = BuiltInRegistries.ITEM.getKey(made.getItem());
				if (!recipe.matches(in, level) || made.isEmpty() || !made_.getNamespace().equals(NAMESPACE)) bad.add(holder.id().identifier().getPath());
			}
			if (count == 0) throw new Probe.Fail("the world holds no furnish recipe");
			if (!bad.isEmpty()) throw new Probe.Fail(bad.size() + " of " + count + " furnish recipes do not match their input or yield no furniture: " + bad.subList(0, Math.min(5, bad.size())));
			return count + " furniture workbench recipes each match their ingredient and assemble a furnish item";
		});
	}

	private static BlockHitResult hit(BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
	}

	/** Four blocks over the player, kept inside the world: the test world's spawn can stand at the build limit. */
	private static BlockPos above(ServerPlayer player) {
		BlockPos at = player.blockPosition();
		return at.atY(Math.min(at.getY() + 4, player.level().getMaxY() - 2));
	}

	/** Using a chair puts the player on a seat entity, which is the mod's own entity type. */
	private static String chair(Probe.Ctx c) {
		requireMod();
		return c.onServer(() -> {
			ServerPlayer player = c.serverPlayer();
			var level = player.level();
			BlockPos pos = above(player);
			BlockState before = level.getBlockState(pos);
			try {
				level.setBlockAndUpdate(pos, block("acacia_chair").defaultBlockState());
				var result = level.getBlockState(pos).useWithoutItem(level, player, hit(pos));
				var vehicle = player.getVehicle();
				if (vehicle == null) throw new Probe.Fail("using the chair did not seat the player (result " + result + ")");
				Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType());
				if (!type.getNamespace().equals(NAMESPACE)) throw new Probe.Fail("the player's vehicle is " + type + ", not a furnish seat");
				vehicle.discard();
				player.stopRiding();
				return "the player sat on " + type + " (result " + result + ")";
			} finally {
				level.setBlockAndUpdate(pos, before);
			}
		});
	}

	/** A cabinet's block entity holds what is put in it and gives it back after a save and a load. */
	private static String cabinet(Probe.Ctx c) {
		requireMod();
		return c.onServer(() -> {
			ServerPlayer player = c.serverPlayer();
			var level = player.level();
			BlockPos pos = above(player);
			BlockState before = level.getBlockState(pos);
			try {
				BlockState state = block("acacia_cabinet").defaultBlockState();
				level.setBlockAndUpdate(pos, state);
				BlockEntity entity = level.getBlockEntity(pos);
				if (!(entity instanceof Container container)) throw new Probe.Fail("the cabinet has no container block entity (" + entity + ")");
				container.setItem(0, new ItemStack(Items.DIAMOND, 7));
				var tag = entity.saveWithFullMetadata(level.registryAccess());
				BlockEntity loaded = BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
				if (!(loaded instanceof Container again) || !again.getItem(0).is(Items.DIAMOND) || again.getItem(0).getCount() != 7) {
					throw new Probe.Fail("the cabinet did not keep 7 diamonds through a save and a load: " + (loaded instanceof Container a ? a.getItem(0) : loaded));
				}
				return "a cabinet keeps 7 diamonds through save and load (" + container.getContainerSize() + " slots)";
			} finally {
				level.setBlockAndUpdate(pos, before);
			}
		});
	}

	/** Every block entity type of the mod that draws something has a renderer, and each of them extracts a state from a fresh block entity. */
	private static String renderers(Probe.Ctx c) {
		requireMod();
		var dispatcher = c.mc.getBlockEntityRenderDispatcher();
		List<String> drawn = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()) {
			if (!id.getNamespace().equals(NAMESPACE)) continue;
			var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(id);
			Block block = BuiltInRegistries.BLOCK.stream().filter(b -> type.isValid(b.defaultBlockState())).findFirst().orElseThrow(() -> new Probe.Fail("no block for " + id));
			BlockEntity entity = type.create(BlockPos.ZERO, block.defaultBlockState());
			entity.setLevel(c.mc.level);
			if (dispatcher.getRenderer(entity) != null) drawn.add(id.getPath());
		}
		for (String needed : List.of("shelf", "showcase", "mailbox", "disk_rack", "plate", "recycle_bin")) {
			if (!drawn.contains(needed)) throw new Probe.Fail("no block entity renderer for furnish:" + needed + " (have " + drawn + ")");
		}
		return "block entity renderers: " + drawn;
	}
}
