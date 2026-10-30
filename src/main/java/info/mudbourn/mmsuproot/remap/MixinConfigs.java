package info.mudbourn.mmsuproot.remap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import info.mudbourn.mmsuproot.util.Json;

/** Makes a target mod's mixin config forgiving, so one missing target disables one mixin and not the launch. */
public final class MixinConfigs {
	private MixinConfigs() { }

	/**
	 * Rewrites the config; returns null when it cannot be parsed. {@code refmap} receives the refmap name if any.
	 * {@code strict} keeps injectors required. A mixin class for which {@code dropped} (given its internal name) is true is left out of the lists.
	 */
	@SuppressWarnings("unchecked")
	public static String soften(String json, String[] refmap, Predicate<String> dropped, boolean strict) {
		try {
			Map<String, Object> config = Json.parseObject(json);
			config.put("required", Boolean.FALSE);
			Object inj = config.get("injectors");
			Map<String, Object> injectors = inj instanceof Map ? (Map<String, Object>) inj : new LinkedHashMap<>();
			// strict (dev, -Duproot.strictMixins=<mod id>): an injector that finds no target fails loudly instead of doing nothing
			injectors.put("defaultRequire", strict ? 1L : 0L);
			config.put("injectors", injectors);
			String pkg = String.valueOf(config.get("package")).replace('.', '/') + "/";
			for (String key : new String[] {"mixins", "client", "server"}) {
				if (!(config.get(key) instanceof List<?> list)) continue;
				List<Object> kept = new ArrayList<>();
				for (Object o : list) if (!dropped.test(pkg + String.valueOf(o).replace('.', '/'))) kept.add(o);
				config.put(key, kept);
			}
			if (config.get("refmap") instanceof String s) refmap[0] = s;
			return Json.write(config);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
