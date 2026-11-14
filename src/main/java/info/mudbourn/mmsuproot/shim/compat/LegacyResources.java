package info.mudbourn.mmsuproot.shim.compat;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** {@code ResourceManager}'s listing calls took a {@code Predicate} of the file name before 26.3 and take a {@code Selector} now. */
public final class LegacyResources {
	private LegacyResources() { }

	public static Map<Identifier, Resource> listResources(ResourceManager manager, String path, Predicate<Identifier> filter) {
		return manager.listResources(path, filter::test);
	}

	public static Map<Identifier, List<Resource>> listResourceStacks(ResourceManager manager, String path, Predicate<Identifier> filter) {
		return manager.listResourceStacks(path, filter::test);
	}
}
