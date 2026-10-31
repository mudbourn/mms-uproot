package info.mudbourn.mmsuproot.shim.entity;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.world.level.block.state.BlockState;

/** Render state fields that 26.3 changed the type of. Client only; nothing here loads on a server. */
public final class LegacyRenderStates {
	/** The block each enderman render state was last filled with, kept by mixin.EndermanCarriedMixin; the render thread only. */
	private static final Map<EndermanRenderState, BlockState> CARRIED = new WeakHashMap<>();

	private LegacyRenderStates() { }

	/** Called when an enderman fills its render state (mixin.EndermanCarriedMixin); {@code block} is null when it carries none. */
	public static void setCarriedBlock(EndermanRenderState state, BlockState block) {
		if (block == null) CARRIED.remove(state);
		else CARRIED.put(state, block);
	}

	/**
	 * {@code EndermanRenderState.carriedBlock}: the block the enderman carries, null when it carries none. 26.3 keeps what to
	 * draw of it, not the block, so the block is taken from the enderman as the renderer extracts the state.
	 */
	public static BlockState carriedBlock(EndermanRenderState state) {
		return CARRIED.get(state);
	}
}
