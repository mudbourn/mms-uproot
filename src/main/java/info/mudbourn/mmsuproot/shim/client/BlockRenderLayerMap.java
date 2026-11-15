package info.mudbourn.mmsuproot.shim.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

/**
 * Fabric's old table from block to chunk layer. 26.3 bakes the layer into every quad of a model, from the transparency of the
 * sprite area the quad covers (solid, cutout when some pixel is fully transparent, translucent when some is partly so), and Fabric's
 * table is gone. A block a mod listed here has its baked model wrapped as the model loading API allows, after the bake, and the wrapper
 * hands out the same parts with every quad on at least the listed layer. A quad whose sprite already needs a later layer keeps it, so
 * a block listed as cutout never loses the soft pixels of its sprite. Fabric's own mesh path reads the layer from the same quads, so
 * terrain, moving blocks and the item renderer all see it.
 */
public final class BlockRenderLayerMap {
	private static final Map<Block, ChunkSectionLayer> LAYERS = new ConcurrentHashMap<>();

	static {
		ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, bake) -> {
			ChunkSectionLayer layer = LAYERS.get(bake.state().getBlock());
			return layer == null ? model : new Layered(model, layer);
		}));
	}

	private BlockRenderLayerMap() { }

	public static void putBlock(Block block, ChunkSectionLayer layer) {
		LAYERS.put(block, layer);
	}

	public static void putBlocks(ChunkSectionLayer layer, Block... blocks) {
		for (Block block : blocks) putBlock(block, layer);
	}

	/** A baked block model whose quads are on a chunk layer or a later one. */
	private static final class Layered implements BlockStateModel {
		private final BlockStateModel model;
		private final ChunkSectionLayer layer;
		private final Map<BlockStateModelPart, BlockStateModelPart> parts = new ConcurrentHashMap<>();

		Layered(BlockStateModel model, ChunkSectionLayer layer) {
			this.model = model;
			this.layer = layer;
		}

		@Override
		public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
			List<BlockStateModelPart> inner = new ArrayList<>();
			model.collectParts(random, inner);
			for (BlockStateModelPart part : inner) output.add(parts.computeIfAbsent(part, p -> new Part(p, layer)));
		}

		@Override
		public Material.Baked particleMaterial() {
			return model.particleMaterial();
		}

		@Override
		public int materialFlags() {
			return flags(model.materialFlags(), layer);
		}
	}

	/** One part of a model with its quads moved up to a chunk layer. */
	private static final class Part implements BlockStateModelPart {
		private final BlockStateModelPart part;
		private final ChunkSectionLayer layer;
		private final Map<Direction, List<BakedQuad>> quads = new ConcurrentHashMap<>();
		private volatile List<BakedQuad> unculled;

		Part(BlockStateModelPart part, ChunkSectionLayer layer) {
			this.part = part;
			this.layer = layer;
		}

		@Override
		public List<BakedQuad> getQuads(Direction side) {
			if (side == null) {
				List<BakedQuad> own = unculled;
				if (own == null) unculled = own = moved(part.getQuads(null));
				return own;
			}
			return quads.computeIfAbsent(side, d -> moved(part.getQuads(d)));
		}

		private List<BakedQuad> moved(List<BakedQuad> from) {
			List<BakedQuad> out = new ArrayList<>(from.size());
			for (BakedQuad q : from) {
				BakedQuad.MaterialInfo i = q.materialInfo();
				if (i.layer().ordinal() >= layer.ordinal()) {
					out.add(q);
					continue;
				}
				BakedQuad.MaterialInfo info = new BakedQuad.MaterialInfo(i.sprite(), layer, i.itemRenderType(), i.itemGlintRenderType(), i.itemGlintSpecialRenderType(),
					i.tintIndex(), i.shadeDirectionOverride(), i.lightEmission());
				out.add(new BakedQuad(q.position0(), q.position1(), q.position2(), q.position3(), q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(),
					q.direction(), info));
			}
			return List.copyOf(out);
		}

		@Override
		public boolean useAmbientOcclusion() {
			return part.useAmbientOcclusion();
		}

		@Override
		public Material.Baked particleMaterial() {
			return part.particleMaterial();
		}

		@Override
		public int materialFlags() {
			return flags(part.materialFlags(), layer);
		}
	}

	/** The flags of a model with the translucent one set when the layer is, as {@code BakedQuad.MaterialInfo.flags} does; a flag the quads already have stays. */
	private static int flags(int flags, ChunkSectionLayer layer) {
		return layer.translucent() ? flags | BakedQuad.FLAG_TRANSLUCENT : flags;
	}
}
