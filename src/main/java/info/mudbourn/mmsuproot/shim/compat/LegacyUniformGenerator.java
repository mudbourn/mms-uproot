package info.mudbourn.mmsuproot.shim.compat;

/** 1.21.11's {@code UniformGenerator}: a roll count drawn evenly between two numbers. */
public record LegacyUniformGenerator(float min, float max) implements LegacyNumberProvider {
	public static LegacyUniformGenerator between(float min, float max) {
		return new LegacyUniformGenerator(min, max);
	}

	@Override
	public float value() {
		return (min + max) / 2;
	}
}
