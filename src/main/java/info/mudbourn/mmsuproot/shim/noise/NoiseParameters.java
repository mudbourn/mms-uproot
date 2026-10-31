package info.mudbourn.mmsuproot.shim.noise;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * What 1.21.11 called {@code NormalNoise.NoiseParameters}: the first octave and one amplitude per
 * octave. 26.3 made the record package-private and describes noise by a base amplitude, a base octave
 * and amplitude modifiers, so mods that build the old record are pointed here, and the constructors
 * of the providers that took it get the {@link NormalNoise} this makes.
 */
public final class NoiseParameters {
	private final int firstOctave;
	private final double[] amplitudes;
	private final NormalNoise made;

	public NoiseParameters(int firstOctave, double amplitude, double... others) {
		this.firstOctave = firstOctave;
		this.made = null;
		this.amplitudes = new double[others.length + 1];
		amplitudes[0] = amplitude;
		System.arraycopy(others, 0, amplitudes, 1, others.length);
	}

	private NoiseParameters(NormalNoise made) {
		this.firstOctave = 0;
		this.amplitudes = new double[0];
		this.made = made;
	}

	/**
	 * A cast to the old record, which 26.3 registries no longer hold: they hold the noise itself. Anything
	 * else is handed back as it is (and fails the cast as it always did).
	 */
	public static NoiseParameters cast(Object o) {
		if (o instanceof NormalNoise noise) return new NoiseParameters(noise);
		return (NoiseParameters) o;
	}

	/**
	 * The 26.3 noise that samples as 1.21.11's {@code NormalNoise.create(random, firstOctave, amplitudes)} did:
	 * {@code createParity} keeps the old normalisation, which differs from what the builder does by default.
	 */
	public NormalNoise toNoise() {
		if (made != null) return made;
		return NormalNoise.createParity(firstOctave, new DoubleArrayList(amplitudes));
	}
}
