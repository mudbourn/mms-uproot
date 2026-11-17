package info.mudbourn.mmsuproot.shim.compat;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.resources.ResourceKey;

/**
 * Stand-in for 26.2's {@code RegistryFixedCodec} and {@code RegistryFileCodec}. Their {@code create} methods returned the codec class itself,
 * so a mod calls {@code fieldOf} and the other codec methods on that class (invokevirtual). 26.3's {@code create} returns a plain
 * {@link Codec} and builds it through {@link RegistryCodecs#holder}; this class wraps that codec and answers every call with it.
 */
public final class LegacyRegistryCodec<E> implements Codec<Holder<E>> {
	private final Codec<Holder<E>> delegate;

	private LegacyRegistryCodec(Codec<Holder<E>> delegate) {
		this.delegate = delegate;
	}

	/** {@code RegistryFixedCodec.create(key)}: holders of the registry, by id only. */
	public static <E> LegacyRegistryCodec<E> create(ResourceKey<? extends Registry<E>> key) {
		return new LegacyRegistryCodec<>(RegistryCodecs.holder(key));
	}

	/** {@code RegistryFileCodec.create(key, elementCodec)}: by id, or inline with the element codec. */
	public static <E> LegacyRegistryCodec<E> create(ResourceKey<? extends Registry<E>> key, Codec<E> elementCodec) {
		return new LegacyRegistryCodec<>(RegistryCodecs.holder(key, elementCodec));
	}

	/** {@code RegistryFileCodec.create(key, elementCodec, allowInline)}. */
	public static <E> LegacyRegistryCodec<E> create(ResourceKey<? extends Registry<E>> key, Codec<E> elementCodec, boolean allowInline) {
		return new LegacyRegistryCodec<>(RegistryCodecs.holder(key, elementCodec, allowInline));
	}

	@Override
	public <T> DataResult<T> encode(Holder<E> input, DynamicOps<T> ops, T prefix) {
		return delegate.encode(input, ops, prefix);
	}

	@Override
	public <T> DataResult<Pair<Holder<E>, T>> decode(DynamicOps<T> ops, T input) {
		return delegate.decode(ops, input);
	}

	@Override
	public String toString() {
		return delegate.toString();
	}
}
