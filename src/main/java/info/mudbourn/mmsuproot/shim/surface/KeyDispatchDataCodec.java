package info.mudbourn.mmsuproot.shim.surface;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * Stand-in for the 1.21.11 holder of a map codec that dispatches on a type key. 26.3 dropped it
 * and registers the {@link MapCodec} itself, so a mod's registration hands the registry the codec.
 */
public record KeyDispatchDataCodec<A>(MapCodec<A> codec) {
	public static <A> KeyDispatchDataCodec<A> of(MapCodec<A> codec) {
		return new KeyDispatchDataCodec<>(codec);
	}

	public static <A> KeyDispatchDataCodec<A> of(Codec<A> codec) {
		return new KeyDispatchDataCodec<>(codec.fieldOf("value"));
	}

	public Codec<A> dataCodec() {
		return codec.codec();
	}
}
