package info.mudbourn.mmsuproot.shim.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric's old table from block to chunk layer. 26.3 bakes the layer into every quad of a model, from the transparency of the
 * sprite area the quad covers (solid, cutout when some pixel is fully transparent, translucent when some is partly so), and Fabric's
 * table is gone. A block a mod listed here has its baked model wrapped as the model loading API allows, after the bake, and the wrapper
 * emits the same quads with every one on at least the listed layer. A quad whose sprite already needs a later layer keeps it, so
 * a block listed as cutout never loses the soft pixels of its sprite. Fabric's mesh path reads the layer from the emitted quads, so
 * terrain, moving blocks and the item renderer all see it.
 */
public final class BlockRenderLayerMap {
	private static final Map<Block, ChunkSectionLayer> LAYERS = new ConcurrentHashMap<>();
	/** Rules of Uproot's own for blocks no mod lists one by one (a state test and the layer the matching states are on at least). */
	private static final List<Map.Entry<Predicate<BlockState>, ChunkSectionLayer>> RULES = new CopyOnWriteArrayList<>();

	static {
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, bake) -> {
			BlockState state = bake.state();
			ChunkSectionLayer layer = LAYERS.get(state.getBlock());
			if (layer == null) {
				for (Map.Entry<Predicate<BlockState>, ChunkSectionLayer> rule : RULES) {
					if (rule.getKey().test(state)) {
						layer = rule.getValue();
						break;
					}
				}
			}
			return layer == null ? model : new Layered(model, layer);
		}));
	}

	private BlockRenderLayerMap() { }

	public static void putBlock(Block block, ChunkSectionLayer layer) {
		LAYERS.put(block, layer);
	}

	/** Every state a test accepts has its model raised to at least {@code layer}, as {@link #putBlock} does for a whole block. */
	public static void putStates(Predicate<BlockState> test, ChunkSectionLayer layer) {
		RULES.add(Map.entry(test, layer));
	}

	public static void putBlocks(ChunkSectionLayer layer, Block... blocks) {
		for (Block block : blocks) putBlock(block, layer);
	}

	/**
	 * A baked block model whose quads are on a chunk layer or a later one. Every renderer path reads the model through Fabric's
	 * {@code emitQuads}, so the raise is a quad transform pushed around the wrapped model's own emit: a model that emits its own
	 * geometry, or varies it with the world, keeps it, and its geometry key and particle material stay its own.
	 */
	private static final class Layered implements BlockStateModel {
		private final BlockStateModel model;
		private final ChunkSectionLayer layer;
		private final QuadTransform raise;

		Layered(BlockStateModel model, ChunkSectionLayer layer) {
			this.model = model;
			this.layer = layer;
			this.raise = quad -> {
				if (quad.chunkLayer().ordinal() < layer.ordinal()) quad.chunkLayer(layer);
				return true;
			};
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
			model.collectParts(random, output);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
			emitter.pushTransform(raise);
			try {
				model.emitQuads(emitter, view, pos, state, random, cullTest);
			} finally {
				emitter.popTransform();
			}
		}

		@Override
		public Object createGeometryKey(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random) {
			return model.createGeometryKey(view, pos, state, random);
		}

		@Override
		public Material.Baked particleMaterial() {
			return model.particleMaterial();
		}

		@Override
		public Material.Baked particleMaterial(BlockAndTintGetter view, BlockPos pos, BlockState state) {
			return model.particleMaterial(view, pos, state);
		}

		@Override
		public int materialFlags() {
			return flags(model.materialFlags(), layer);
		}

		@Override
		public int materialFlags(BlockAndTintGetter view, BlockPos pos, BlockState state, RandomSource random) {
			return flags(model.materialFlags(view, pos, state, random), layer);
		}
	}

	/** The flags of a model with the translucent one set when the layer is, as {@code BakedQuad.MaterialInfo.flags} does; a flag the quads already have stays. */
	private static int flags(int flags, ChunkSectionLayer layer) {
		return layer.translucent() ? flags | BakedQuad.FLAG_TRANSLUCENT : flags;
	}
}
