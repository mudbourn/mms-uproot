package info.mudbourn.mmsuproot.shim.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** Helpers for mixin handlers rewritten by a {@code handler} rule (see remap.MixinParams). */
public final class LegacyOperations {
	private LegacyOperations() { }

	/**
	 * A wrapped call whose target changed parameters: the handler still passes the arguments it knew, and {@code spec} says what the real
	 * call gets: entry {@code n} of 0 or more is the handler's argument {@code n}, and a negative {@code -k-1} is entry {@code k} of
	 * {@code captured}, values the rewritten handler took from the enclosing method.
	 */
	public static <R> Operation<R> remapping(Operation<R> original, int[] spec, Object[] captured) {
		return args -> {
			Object[] mapped = new Object[spec.length];
			for (int i = 0; i < spec.length; i++) mapped[i] = spec[i] >= 0 ? args[spec[i]] : captured[-spec[i] - 1];
			return original.call(mapped);
		};
	}
}
