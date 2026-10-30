package info.mudbourn.mmsuproot.shim.client;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;

/**
 * Fabric's old table from block to chunk layer. 26.3 picks the layer of every quad from the
 * transparency of its sprite, so a mod's registration has nothing left to say and is dropped.
 */
public final class BlockRenderLayerMap {
	private BlockRenderLayerMap() { }

	public static void putBlock(Block block, ChunkSectionLayer layer) {
	}
}
