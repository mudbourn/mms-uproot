package info.mudbourn.mmsuproot.shim.surface;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.UniformFloat;

/**
 * Stripes of noise values: a stripe {@code size} wide every {@code 2 * size}, from {@code from} up to
 * {@code to}. A target mod's own helper for this sits in a class that is switched off, because its
 * base class was rebuilt; the rule table sends the call here.
 */
public final class Thresholds {
	private Thresholds() { }

	public static List<FloatProvider> createThresholds(float size, float from, float to) {
		List<FloatProvider> out = new ArrayList<>();
		for (float start = from; start <= to; start += size * 2) out.add(UniformFloat.of(start, start + size));
		return out;
	}

	/** {@code FloatProvider.getMinValue()}, now {@code min()}. */
	public static float getMinValue(FloatProvider provider) {
		return provider.min();
	}

	/** {@code FloatProvider.getMaxValue()}, now {@code max()}. */
	public static float getMaxValue(FloatProvider provider) {
		return provider.max();
	}
}
