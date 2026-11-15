package info.mudbourn.mmsuproot.shim.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.21.11's block render dispatcher, which drew block models for entities and block entities. 26.3
 * has no such class. A renderer that stores it can be built; {@code renderSingleBlock} submits to the collector of the
 * picture-in-picture render in progress, and the other draw calls do not exist here.
 */
public final class BlockRenderDispatcher {
	private static final BlockRenderDispatcher INSTANCE = new BlockRenderDispatcher();

	private BlockRenderDispatcher() { }

	public static BlockRenderDispatcher of(EntityRendererProvider.Context context) {
		return INSTANCE;
	}

	/** {@code Minecraft.getBlockRenderer()}. */
	public static BlockRenderDispatcher of(Minecraft minecraft) {
		return INSTANCE;
	}

	/**
	 * {@code renderSingleBlock(state, pose, buffers, light, overlay)}: the block's baked model, drawn untinted. The buffer source is not
	 * used; the block goes to the collector of the picture-in-picture render in progress, and outside one this throws.
	 */
	public void renderSingleBlock(BlockState state, PoseStack pose, LegacyBufferSource buffers, int light, int overlay) {
		if (!(LegacyRender.collector instanceof OrderedSubmitNodeCollector collector)) {
			throw new IllegalStateException("A block is drawn only inside a picture-in-picture render in 26.3");
		}
		LegacyClient.submitBlock(collector, pose, state, light, overlay, 0);
	}
}
