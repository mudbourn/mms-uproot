package info.mudbourn.mmsuproot.shim.tree;

/**
 * Stands in for the first version of Oh The Trees You'll Grow's tree settings, which a mod names
 * when it builds settings for datagen. The game reads that mod's JSON, so nothing here is called;
 * the classes only have to exist for the mod's classes to load. A call to a method that is not
 * here fails with {@code NoSuchMethodError}, which shows the settings were used after all.
 */
public final class TreeFromStructureNBTConfig {
	private TreeFromStructureNBTConfig() { }

	/** The builder of the old settings. */
	public static final class Builder { }
}
