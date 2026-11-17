package info.mudbourn.mmsuproot.shim.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The leaf layer of the Terraform Wood API. Its 1.21.11 hook answered {@code ItemBlockRenderTypes.getChunkRenderType} with the cutout layer for
 * its leaves and for a small log that carries leaves; 26.3 has no such table, so the same states are raised to the cutout layer through
 * {@link BlockRenderLayerMap}, which wraps their baked models. Blocks are told apart by the names of their classes, found anywhere above the
 * block's own class, so Uproot needs no build dependency on Terraform and nothing happens when it is not there.
 */
public final class LegacyLeafLayers implements ClientModInitializer {
	private static final String LEAVES = "com.terraformersmc.terraform.leaves.api.block.ExtendedLeavesBlock";
	private static final String SMALL_LOG = "com.terraformersmc.terraform.wood.api.block.SmallLogBlock";
	private static final String HAS_LEAVES = "has_leaves";

	/** What a block class is to Terraform: 1 an extended leaves block, 2 a small log, 0 neither. */
	private static final ClassValue<Integer> KIND = new ClassValue<>() {
		@Override
		protected Integer computeValue(Class<?> type) {
			for (Class<?> c = type; c != null; c = c.getSuperclass()) {
				if (c.getName().equals(LEAVES)) return 1;
				if (c.getName().equals(SMALL_LOG)) return 2;
			}
			return 0;
		}
	};

	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.putStates(LegacyLeafLayers::isLeafLayer, ChunkSectionLayer.CUTOUT);
	}

	private static boolean isLeafLayer(BlockState state) {
		int kind = KIND.get(state.getBlock().getClass());
		if (kind == 1) return true;
		if (kind != 2) return false;
		for (Property<?> property : state.getProperties()) {
			if (property.getName().equals(HAS_LEAVES)) return Boolean.TRUE.equals(state.getValue(property));
		}
		return false;
	}
}
