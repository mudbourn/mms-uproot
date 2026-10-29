package info.mudbourn.mmsuproot.shim.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/** 1.21.11 wrapped a provider's codec in a type object; 26.3 registers the codec itself. */
public final class BlockStateProviderType<P extends BlockStateProvider> {
	private final MapCodec<P> codec;

	public BlockStateProviderType(MapCodec<P> codec) {
		this.codec = codec;
	}

	public MapCodec<P> codec() {
		return codec;
	}
}
