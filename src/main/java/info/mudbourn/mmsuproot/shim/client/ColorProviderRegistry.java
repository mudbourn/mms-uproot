package info.mudbourn.mmsuproot.shim.client;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric's old colour registry. A block colour becomes one tint source per tint index, which is how
 * 26.3 lists the tints of a block; the in-world colour is the old function's answer for that index.
 */
public interface ColorProviderRegistry<B, C> {
	/** Tint indices a model of a mod's block may use. */
	int TINT_INDICES = 4;

	ColorProviderRegistry<Block, BlockColor> BLOCK = new ColorProviderRegistry<>() { };

	/** Erased to (Object, Object[]) like Fabric's generic method, which is the shape the mod calls. */
	@SuppressWarnings("unchecked")
	default void register(C colorFunction, B... blocks) {
		BlockColor color = (BlockColor) colorFunction;
		Block[] list = new Block[blocks.length];
		for (int i = 0; i < blocks.length; i++) list[i] = (Block) blocks[i];
		List<BlockTintSource> sources = new ArrayList<>();
		for (int i = 0; i < TINT_INDICES; i++) sources.add(new Source(color, i));
		BlockColorRegistry.register(sources, list);
	}

	record Source(BlockColor color, int index) implements BlockTintSource {
		@Override
		public int color(BlockState state) {
			return -1;
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			return color.getColor(state, level, pos, index);
		}
	}
}
