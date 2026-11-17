package info.mudbourn.mmsuproot.data;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which data elements that failed to load may be left out of the game: only those read from a pack Uproot converted (a target mod, a world
 * data pack for an older format). Elements of vanilla and native 26.x packs, and errors of a registry as a whole, stay fatal as the game has them.
 */
public final class LenientData {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");
	/** The registries 26.3 reads through RegistryDataLoader that 1.21.11 read leniently (ReloadableServerRegistries). */
	private static final Set<String> REGISTRIES = Set.of("loot_table", "advancement", "predicate", "item_modifier");
	/** Failed element to the pack it was read from, for the packs that are converted. */
	private static final Map<ResourceKey<?>, String> FAILED = new ConcurrentHashMap<>();

	private LenientData() { }

	/** Called for every element that failed to parse, with the id of the pack its file came from. */
	public static void failed(ResourceKey<?> key, String packId) {
		if (REGISTRIES.contains(key.registry().getPath()) && key.registry().getNamespace().equals("minecraft") && DataShim.converted(packId)) FAILED.put(key, packId);
	}

	/** Whether the error under this key is of an element of a converted pack and may be dropped; logs it with the pack. */
	public static boolean skip(ResourceKey<?> key, Throwable error) {
		if (key.registry().equals(Identifier.withDefaultNamespace("root"))) return false; // the registry as a whole: strict
		String pack = FAILED.get(key);
		if (pack == null) return false;
		Throwable cause = error;
		while (cause.getCause() != null) cause = cause.getCause();
		LOGGER.error("Left out {} from pack {}: {}", key, pack, cause.getMessage());
		return true;
	}
}
