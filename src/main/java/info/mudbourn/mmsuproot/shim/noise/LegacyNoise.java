package info.mudbourn.mmsuproot.shim.noise;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * What 1.21.11's {@code NormalNoise} did for a mod that made one itself. In 26.3 a {@code NormalNoise} only
 * describes a noise and {@code create(RandomSource)} makes the {@link Noise} that is sampled, but the
 * old code holds one object in a local of type {@code NormalNoise} and samples it. So {@link #create} hands
 * back the description and remembers the noise it made for it, and {@link #getValue} samples that. Two
 * creations from equal parameters share one entry, the newest; mods seed theirs from the world seed, so
 * the two are the same noise.
 */
public final class LegacyNoise {
	private static final Map<NormalNoise, Noise> MADE = Collections.synchronizedMap(new WeakHashMap<>());

	private LegacyNoise() { }

	/** {@code NormalNoise.create(random, parameters)}. */
	public static NormalNoise create(RandomSource random, NoiseParameters parameters) {
		NormalNoise description = parameters.toNoise();
		MADE.put(description, description.create(random));
		return description;
	}

	/** {@code normalNoise.getValue(x, y, z)} on a noise made by {@link #create}. */
	public static double getValue(NormalNoise noise, double x, double y, double z) {
		Noise made = MADE.get(noise);
		if (made == null) throw new IllegalStateException("This noise was not made through NormalNoise.create");
		return made.get(x, y, z);
	}
}
