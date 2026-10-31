package info.mudbourn.mmsuproot.shim.noise;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * What 1.21.11's {@code NormalNoise} did for a mod that made one itself. In 26.3 a {@code NormalNoise} only
 * describes a noise and {@code create(RandomSource)} makes the {@link Noise} that is sampled, but the
 * old code holds one object in a local of type {@code NormalNoise} and samples it. So {@link #create} hands
 * back the description and remembers the noise it made for it, and {@link #getValue} samples that. The
 * description is remembered by identity, not by its parameters, so two worlds that make a noise from equal
 * parameters but different seeds each sample their own.
 */
public final class LegacyNoise {
	private static final Map<Ref, Noise> MADE = new ConcurrentHashMap<>();
	private static final ReferenceQueue<NormalNoise> DEAD = new ReferenceQueue<>();

	/** A description held weakly and compared by identity ({@code NormalNoise.equals} compares parameters). */
	private static final class Ref extends WeakReference<NormalNoise> {
		private final int hash;

		Ref(NormalNoise description, ReferenceQueue<NormalNoise> queue) {
			super(description, queue);
			this.hash = System.identityHashCode(description);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			return o instanceof Ref other && get() != null && get() == other.get();
		}
	}

	private LegacyNoise() { }

	/** {@code NormalNoise.create(random, parameters)}. */
	public static NormalNoise create(RandomSource random, NoiseParameters parameters) {
		NormalNoise description = parameters.toNoise();
		return share(description, description.create(random));
	}

	/**
	 * Makes {@code made} the noise that {@code description} samples as. For a stand-in that holds a noise of the game's own
	 * under a description of its own; the description must be an object made for this one noise (not a shared constant),
	 * because only that object samples it.
	 */
	public static NormalNoise share(NormalNoise description, Noise made) {
		for (Object dead; (dead = DEAD.poll()) != null; ) MADE.remove(dead);
		MADE.put(new Ref(description, DEAD), made);
		return description;
	}

	/** {@code normalNoise.getValue(x, y, z)} on a noise made by {@link #create}. */
	public static double getValue(NormalNoise noise, double x, double y, double z) {
		Noise made = MADE.get(new Ref(noise, null));
		if (made == null) throw new IllegalStateException("This noise was not made through NormalNoise.create");
		return made.get(x, y, z);
	}
}
