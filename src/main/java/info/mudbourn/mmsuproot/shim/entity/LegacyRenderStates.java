package info.mudbourn.mmsuproot.shim.entity;

import java.lang.reflect.Field;
import java.util.Collection;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Render state fields that 26.3 changed the type of. Client only; nothing here loads on a server. */
public final class LegacyRenderStates {
	private static Field modelParts;
	private static Field specialRenderer;

	private LegacyRenderStates() { }

	/**
	 * {@code EndermanRenderState.carriedBlock}: the block the enderman carries. 26.3 keeps what to draw of it, not the block,
	 * so a mod that only asks whether there is one gets air for none and some block for one.
	 */
	public static BlockState carriedBlock(EndermanRenderState state) {
		return hasModel(state.carriedBlock) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
	}

	private static boolean hasModel(BlockModelRenderState model) {
		try {
			if (modelParts == null) {
				modelParts = BlockModelRenderState.class.getDeclaredField("modelParts");
				modelParts.setAccessible(true);
				specialRenderer = BlockModelRenderState.class.getDeclaredField("specialRenderer");
				specialRenderer.setAccessible(true);
			}
			return !((Collection<?>) modelParts.get(model)).isEmpty() || specialRenderer.get(model) != null;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Uproot could not read the carried block of an enderman", e);
		}
	}
}
