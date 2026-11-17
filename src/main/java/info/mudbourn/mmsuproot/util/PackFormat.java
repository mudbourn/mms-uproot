package info.mudbourn.mmsuproot.util;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** What a {@code pack.mcmeta} says about the data format its content was written for; shared by the mod boot and the world data pack shim. */
public final class PackFormat {
	/** The data pack format 26.3 reads (version.json, data_major); a pack that stops below it was made for an older game. */
	public static final int DATA_FORMAT = 121;

	private PackFormat() { }

	/**
	 * The lowest of min_format, supported_formats (its lower end) and pack_format, empty when the file has no pack object or none of the three.
	 * The upper end is ignored on purpose; authors widen max_format (to 999, say) so a pack loads on new versions, which says nothing about
	 * the shape of the files in it.
	 */
	public static OptionalInt lowestFormat(Map<String, Object> mcmeta) {
		if (!(mcmeta.get("pack") instanceof Map<?, ?> pack)) return OptionalInt.empty();
		int lowest = Integer.MAX_VALUE;
		for (String key : List.of("min_format", "supported_formats", "pack_format")) {
			Object value = pack.get(key);
			if (value instanceof Map<?, ?> range) value = range.get("min_inclusive");
			if (value instanceof List<?> version && !version.isEmpty()) value = version.get(0);
			if (value instanceof Number n) lowest = Math.min(lowest, n.intValue());
		}
		return lowest == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(lowest);
	}
}
