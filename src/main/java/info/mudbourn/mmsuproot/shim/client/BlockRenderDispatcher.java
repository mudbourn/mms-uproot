package info.mudbourn.mmsuproot.shim.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * 1.21.11's block render dispatcher, which drew block models for entities and block entities. 26.3
 * has no such class; this empty one lets a renderer that stores it be built. It draws nothing.
 */
public final class BlockRenderDispatcher {
	private static final BlockRenderDispatcher INSTANCE = new BlockRenderDispatcher();

	private BlockRenderDispatcher() { }

	public static BlockRenderDispatcher of(EntityRendererProvider.Context context) {
		return INSTANCE;
	}
}
