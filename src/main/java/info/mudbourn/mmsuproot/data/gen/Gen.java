package info.mudbourn.mmsuproot.data.gen;

import java.util.LinkedHashMap;
import java.util.Map;

import info.mudbourn.mmsuproot.util.Json;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * Shared bits of the generated data: the namespace it lives in, JSON object helpers, and the
 * collector that every recorder writes its files into. Files are keyed by their path in the pack,
 * such as {@code data/mms_uproot/villager_trade/x.json}.
 */
final class Gen {
	static final String NS = "mms_uproot";

	private Gen() { }

	/** Every recorder adds its files here, when the pack is opened and never earlier. */
	static Map<String, byte[]> build() {
		GenSetup.fireLegacyEvents();
		Map<String, String> files = new LinkedHashMap<>();
		Trades.files(files);
		Brewing.files(files);
		Fuel.files(files);
		Compost.files(files);
		Map<String, byte[]> bytes = new LinkedHashMap<>();
		files.forEach((path, json) -> bytes.put(path, json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		return bytes;
	}

	static String itemId(ItemLike item) {
		return BuiltInRegistries.ITEM.getKey(item.asItem()).toString();
	}

	/** A pack path for a registry entry: {@code data/<ns>/<dir>/<path>.json}. */
	static String path(Identifier id, String dir) {
		return "data/" + id.getNamespace() + "/" + dir + "/" + id.getPath() + ".json";
	}

	/** Alphanumeric key for ids of other namespaces folded into a file name. */
	static String flat(Identifier id) {
		String s = id.getNamespace().equals("minecraft") ? id.getPath() : id.getNamespace() + "_" + id.getPath();
		return s.replace('/', '_');
	}

	static Map<String, Object> obj(Object... kv) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
		return map;
	}

	/** A float without the float-to-double noise, so 0.05f reads 0.05. */
	static double clean(float f) {
		return Double.parseDouble(Float.toString(f));
	}

	static String json(Object value) {
		return Json.write(value);
	}
}
