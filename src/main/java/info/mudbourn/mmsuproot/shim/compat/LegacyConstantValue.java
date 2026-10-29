package info.mudbourn.mmsuproot.shim.compat;

/** 1.21.11's {@code ConstantValue}: a roll count that is a fixed number. */
public record LegacyConstantValue(float value) implements LegacyNumberProvider {
	public static LegacyConstantValue exactly(float value) {
		return new LegacyConstantValue(value);
	}
}
