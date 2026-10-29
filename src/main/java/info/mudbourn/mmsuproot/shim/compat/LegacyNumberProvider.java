package info.mudbourn.mmsuproot.shim.compat;

/** 1.21.11's {@code NumberProvider}, gone in 26.3. Loot code that only builds rolls needs the type to exist. */
public interface LegacyNumberProvider {
	/** The value, for providers that are a plain number. */
	float value();
}
