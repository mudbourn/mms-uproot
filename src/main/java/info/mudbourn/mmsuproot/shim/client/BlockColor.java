package info.mudbourn.mmsuproot.shim.client;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** 1.21.11's block colour function: a tint for one tint index of a block state at a position. */
@FunctionalInterface
public interface BlockColor {
	int getColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex);
}
