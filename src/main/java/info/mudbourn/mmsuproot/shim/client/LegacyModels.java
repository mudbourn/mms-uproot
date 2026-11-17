package info.mudbourn.mmsuproot.shim.client;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Model and tint calls whose answers 26.3 keeps in other places: a sprite is the one of a material, a tint is asked of the block's tint sources. */
public final class LegacyModels {
	private LegacyModels() { }

	/** {@code BakedQuad.tintIndex()}: the quad's material info holds it now. */
	public static int tintIndex(BakedQuad quad) {
		return quad.materialInfo().tintIndex();
	}

	/** {@code BlockStateModel.particleIcon()}: the sprite of the particle material. */
	public static TextureAtlasSprite particleIcon(BlockStateModel model) {
		return model.particleMaterial().sprite();
	}

	/** {@code ItemStackRenderState.pickParticleIcon(random)}: the sprite of the particle material it picks. */
	public static TextureAtlasSprite pickParticleIcon(ItemStackRenderState state, RandomSource random) {
		return state.pickParticleMaterial(random).sprite();
	}

	/**
	 * {@code BlockColors.getColor(state, level, pos, tintIndex)}: the colour of that tint layer of the block at the position, or -1 (no tint) when the state
	 * has no tint source for the index, as 1.21.11 answered for a block with no colour provider.
	 */
	public static int blockColor(BlockColors colors, BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
		BlockTintSource source = colors.getTintSource(state, tintIndex);
		return source == null ? -1 : source.colorInWorld(state, level, pos);
	}
}
