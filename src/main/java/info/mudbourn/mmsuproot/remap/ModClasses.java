package info.mudbourn.mmsuproot.remap;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every class of every target mod, by internal name. Uproot never renames these, and it reads their
 * headers to follow a mod class up to the Minecraft class it extends.
 */
public final class ModClasses {
	private final Map<String, Path> classes = new ConcurrentHashMap<>();

	public void put(String internalName, Path path) {
		classes.put(internalName, path);
	}

	public Path path(String internalName) {
		return classes.get(internalName);
	}

	public boolean has(String internalName) {
		return classes.containsKey(internalName);
	}
}
