package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.world.level.block.state.BlockState;

/** 1.21.11's settings holding one block state; the field is public as it was. */
public class BlockStateConfiguration implements FeatureConfiguration {
	public final BlockState state;

	public BlockStateConfiguration(BlockState state) {
		this.state = state;
	}

	public BlockState state() {
		return state;
	}
}
