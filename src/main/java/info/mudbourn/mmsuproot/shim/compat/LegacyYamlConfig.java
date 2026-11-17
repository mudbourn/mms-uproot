package info.mudbourn.mmsuproot.shim.compat;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

/**
 * YAML Config 22.x dropped its static platform class, which older builds of the mods that use it
 * still call for their config directory. On Fabric that call answered Fabric's config directory.
 */
public final class LegacyYamlConfig {
	private LegacyYamlConfig() {
	}

	public static Path getConfigDirectory() {
		return FabricLoader.getInstance().getConfigDir();
	}
}
