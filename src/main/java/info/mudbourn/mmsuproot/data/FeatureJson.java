package info.mudbourn.mmsuproot.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites worldgen JSON from the 1.21.11 shapes to the 26.3 shapes. The rules come from the two
 * vanilla formats, never from a mod's files: each is keyed on a vanilla type id and applies to any
 * file that uses it. Every method works on trees made by {@link info.mudbourn.mmsuproot.util.Json}.
 */
public final class FeatureJson {
	/** Old state provider type ids and what 26.3 calls them. */
	private static final Map<String, String> PROVIDERS = Map.of(
			"minecraft:simple_state_provider", "minecraft:simple",
			"minecraft:weighted_state_provider", "minecraft:weighted",
			"minecraft:randomized_int_state_provider", "minecraft:randomized_int",
			"minecraft:noise_threshold_provider", "minecraft:noise_threshold",
			"minecraft:noise_provider", "minecraft:noise",
			"minecraft:dual_noise_provider", "minecraft:dual_noise",
			"minecraft:rotated_block_provider", "minecraft:rotated");

	/** Feature types 26.3 still has under the same id with the same fields, only without the {@code config} level. */
	private static final Set<String> FLAT = Set.of("minecraft:random_selector", "minecraft:simple_random_selector", "minecraft:simple_block",
			"minecraft:delta_feature", "minecraft:disk", "minecraft:fallen_tree");

	/** The parts of an entity predicate that 26.3 keys by component id. */
	private static final Map<String, String> ENTITY_PARTS = Map.of("type", "minecraft:entity_type", "location", "minecraft:location",
			"distance", "minecraft:distance", "flags", "minecraft:flags", "effects", "minecraft:effects");

	private FeatureJson() { }

	/** Counts what was converted and what failed, for the report. */
	public interface Stats {
		void count(String what);
	}

	/** A spawn group's size: a plain number when the bounds agree, else a uniform int provider. */
	private static Object count(Number min, Number max) {
		if (min.longValue() == max.longValue()) return min;
		Map<String, Object> uniform = new LinkedHashMap<>();
		uniform.put("type", "minecraft:uniform");
		uniform.put("min_inclusive", min);
		uniform.put("max_inclusive", max);
		return uniform;
	}

	/** Timelines name the world clock they run on now; the old ones all ran on the overworld's day. */
	public static Object timeline(Map<String, Object> root, Stats stats) {
		if (root.containsKey("clock")) return root;
		stats.count("timeline clocks");
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("clock", "minecraft:overworld");
		out.putAll(root);
		return out;
	}

	/**
	 * Advancement criteria in 26.3's shape: the recipe trigger names a list of recipes, and a field that held
	 * a list of loot conditions holds one predicate (several become an {@code all_of}) whose conditions are
	 * keyed {@code type}. Entity predicates key their parts by component id.
	 */
	@SuppressWarnings("unchecked")
	public static Object advancement(Map<String, Object> root, Stats stats) {
		if (!(root.get("criteria") instanceof Map<?, ?> criteria)) return root;
		for (Object c : criteria.values()) {
			if (!(c instanceof Map<?, ?> crit) || !(crit.get("conditions") instanceof Map<?, ?> cond)) continue;
			Map<String, Object> conditions = (Map<String, Object>) cond;
			if ("minecraft:recipe_unlocked".equals(crit.get("trigger")) && conditions.containsKey("recipe")) {
				stats.count("recipe triggers");
				Map<String, Object> renamed = new LinkedHashMap<>();
				conditions.forEach((k, v) -> renamed.put(k.equals("recipe") ? "recipes" : k, v));
				conditions.clear();
				conditions.putAll(renamed);
			}
			for (Map.Entry<String, Object> e : conditions.entrySet()) {
				if (e.getValue() instanceof List<?> list && !list.isEmpty() && list.stream().allMatch(o -> o instanceof Map<?, ?> m && m.containsKey("condition"))) {
					stats.count("advancement conditions");
					List<Object> terms = new ArrayList<>();
					for (Object o : list) terms.add(lootCondition((Map<String, Object>) o));
					if (terms.size() == 1) e.setValue(terms.get(0));
					else {
						Map<String, Object> all = new LinkedHashMap<>();
						all.put("type", "minecraft:all_of");
						all.put("terms", terms);
						e.setValue(all);
					}
				}
			}
		}
		return root;
	}

	/** A loot condition with the discriminator renamed from {@code condition} to {@code type}, at every depth. */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> lootCondition(Map<String, Object> in) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : in.entrySet()) {
			String key = e.getKey().equals("condition") ? "type" : e.getKey();
			Object v = e.getValue();
			if (key.equals("predicate") && "minecraft:entity_properties".equals(in.get("condition")) && v instanceof Map<?, ?> p) {
				Map<String, Object> pred = new LinkedHashMap<>();
				((Map<String, Object>) p).forEach((k, pv) -> pred.put(ENTITY_PARTS.getOrDefault(k, k), pv));
				v = pred;
			} else if (v instanceof List<?> list && key.equals("terms")) {
				List<Object> terms = new ArrayList<>();
				for (Object o : list) terms.add(o instanceof Map<?, ?> m ? lootCondition((Map<String, Object>) m) : o);
				v = terms;
			}
			out.put(key, v);
		}
		return out;
	}

	// ---- generic pass: block states, provider ids, offset placement, untyped rule providers ----

	/** Applies the shape changes that do not depend on which feature a value sits in. */
	@SuppressWarnings("unchecked")
	public static Object generic(Object value, Stats stats) {
		if (value instanceof List<?> list) {
			List<Object> out = new ArrayList<>(list.size());
			for (Object o : list) out.add(generic(o, stats));
			return out;
		}
		if (!(value instanceof Map<?, ?>)) return value;
		Map<String, Object> in = (Map<String, Object>) value;
		Map<String, Object> out = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : in.entrySet()) out.put(e.getKey(), generic(e.getValue(), stats));

		if (out.get("firstOctave") instanceof Number octave && out.get("amplitudes") instanceof List<?> amplitudes) {
			stats.count("noise parameters");
			return noise(octave, amplitudes);
		}
		if (out.get("type") instanceof String && out.get("minCount") instanceof Number min && out.get("maxCount") instanceof Number max
				&& Set.of("type", "weight", "minCount", "maxCount").containsAll(out.keySet())) {
			stats.count("spawner counts");
			Map<String, Object> spawner = new LinkedHashMap<>();
			spawner.put("type", out.get("type"));
			if (out.containsKey("weight")) spawner.put("weight", out.get("weight"));
			spawner.put("count", count(min, max));
			return spawner;
		}
		if (out.get("Name") instanceof String name && Set.of("Name", "Properties").containsAll(out.keySet())) {
			stats.count("block states");
			Map<String, Object> state = new LinkedHashMap<>();
			state.put("id", name);
			if (out.get("Properties") instanceof Map<?, ?> props && !props.isEmpty()) state.put("properties", props);
			return state;
		}
		if (out.get("type") instanceof String type) {
			String renamed = PROVIDERS.get(type);
			if (renamed != null) {
				out.put("type", renamed);
				stats.count("state provider ids");
			} else if (type.equals("minecraft:random_offset")) {
				stats.count("offset placements");
				Object xz = out.getOrDefault("xz_spread", 0L);
				Object y = out.getOrDefault("y_spread", 0L);
				Map<String, Object> offset = new LinkedHashMap<>();
				offset.put("type", "minecraft:offset");
				offset.put("x", xz);
				offset.put("y", y);
				offset.put("z", xz);
				return offset;
			}
		} else if (out.containsKey("fallback") && out.containsKey("rules")) {
			stats.count("rule based providers");
			Map<String, Object> typed = new LinkedHashMap<>();
			typed.put("type", "minecraft:rule_based");
			typed.putAll(out);
			return typed;
		}
		return out;
	}

	/** Noise parameters changed from octave amplitudes to a base amplitude and octave; the factor is the one vanilla's own data got for one octave. */
	private static final double ONE_OCTAVE = 0.955388882960065;

	private static Map<String, Object> noise(Number firstOctave, List<?> amplitudes) {
		if (amplitudes.size() != 1 || !(amplitudes.get(0) instanceof Number amplitude)) {
			throw new IllegalArgumentException("noise parameters with " + amplitudes.size() + " octaves cannot be converted");
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("base_amplitude", amplitude.doubleValue() * ONE_OCTAVE);
		out.put("base_octave", firstOctave.longValue());
		return out;
	}

	// ---- configured feature (old) to feature (new) ----

	/** Converts a whole configured feature file. Throws {@link IllegalArgumentException} for a shape it cannot read. */
	public static Map<String, Object> configured(Map<String, Object> root, Stats stats) {
		return feature(generic(root, stats), stats);
	}

	/** Converts a placed feature file. Its inline features, if any, are converted too. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> placed(Map<String, Object> root, Stats stats) {
		Map<String, Object> g = (Map<String, Object>) generic(root, stats);
		g.put("feature", featureRef(g.get("feature"), stats));
		return g;
	}

	/** A feature given by key (kept) or inline (converted). */
	private static Object featureRef(Object ref, Stats stats) {
		return ref instanceof Map<?, ?> m ? feature(cast(m), stats) : ref;
	}

	/** A placed feature given by key (kept) or inline with its own feature. */
	private static Object placedRef(Object ref, Stats stats) {
		if (!(ref instanceof Map<?, ?> m) || !m.containsKey("feature")) return ref;
		Map<String, Object> out = new LinkedHashMap<>(cast(m));
		out.put("feature", featureRef(out.get("feature"), stats));
		return out;
	}

	private static Object placedRefs(Object refs, Stats stats) {
		if (!(refs instanceof List<?> list)) return refs;
		List<Object> out = new ArrayList<>();
		for (Object o : list) out.add(placedRef(o, stats));
		return out;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> cast(Object o) {
		return (Map<String, Object>) o;
	}

	/** One feature object, {@code {"type", "config"}} in the old shape. Mod types keep that shape. */
	private static Map<String, Object> feature(Object value, Stats stats) {
		if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("feature is not an object");
		Map<String, Object> f = cast(value);
		if (!(f.get("type") instanceof String type)) throw new IllegalArgumentException("feature has no type");
		Map<String, Object> cfg = f.get("config") instanceof Map<?, ?> c ? cast(c) : new LinkedHashMap<>();

		switch (type) {
		case "minecraft:random_patch", "minecraft:flower" -> {
			return patch(cfg, stats);
		}
		case "minecraft:forest_rock" -> {
			stats.count(type);
			Map<String, Object> out = typed("minecraft:block_blob");
			out.put("state", cfg.get("state"));
			Map<String, Object> pred = new LinkedHashMap<>();
			pred.put("type", "minecraft:matching_block_tag");
			pred.put("tag", "minecraft:forest_rock_can_place_on");
			out.put("can_place_on", pred);
			return out;
		}
		case "minecraft:basalt_columns" -> {
			stats.count(type);
			return basalt(cfg);
		}
		case "ohthetreesyoullgrow:tree_from_nbt_v1" -> {
			stats.count(type);
			return tree(cfg);
		}
		default -> {
			if (!FLAT.contains(type)) {
				if (!type.startsWith("minecraft:")) return mod(f, stats); // a mod's own type keeps the nested config
				stats.count("flattened " + type);
			} else {
				stats.count(type);
			}
			Map<String, Object> out = typed(type);
			for (Map.Entry<String, Object> e : cfg.entrySet()) out.put(e.getKey(), e.getValue());
			if (type.equals("minecraft:random_selector")) {
				out.put("features", selectorEntries(out.get("features"), stats));
				out.put("default", placedRef(out.get("default"), stats));
			} else if (type.equals("minecraft:simple_random_selector")) {
				out.put("features", placedRefs(out.get("features"), stats));
			}
			return out;
		}
		}
	}

	private static Map<String, Object> mod(Map<String, Object> f, Stats stats) {
		stats.count("mod types kept");
		return f;
	}

	private static Map<String, Object> typed(String type) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("type", type);
		return m;
	}

	private static Object selectorEntries(Object entries, Stats stats) {
		if (!(entries instanceof List<?> list)) return entries;
		List<Object> out = new ArrayList<>();
		for (Object o : list) {
			Map<String, Object> e = new LinkedHashMap<>(cast(o));
			e.put("feature", placedRef(e.get("feature"), stats));
			out.add(e);
		}
		return out;
	}

	private static Map<String, Object> trapezoid(Object spread) {
		long s = spread instanceof Number n ? n.longValue() : 0;
		if (s == 0) {
			Map<String, Object> zero = typed("minecraft:constant");
			zero.put("value", 0L);
			return zero;
		}
		Map<String, Object> t = typed("minecraft:trapezoid");
		t.put("min", -s);
		t.put("max", s);
		t.put("plateau", 0L);
		return t;
	}

	/**
	 * Vanilla moved a patch's tries and spread into the placement of the feature that placed it. The
	 * key stays a feature here, so the patch becomes an {@code overlay} of one inline placed feature
	 * that repeats the inner one {@code tries} times around the origin.
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> patch(Map<String, Object> cfg, Stats stats) {
		if (!(cfg.get("feature") instanceof Map<?, ?> inner)) throw new IllegalArgumentException("patch feature is not inline");
		stats.count("patches");
		Object tries = cfg.getOrDefault("tries", 128L);
		List<Object> placement = new ArrayList<>();
		Map<String, Object> count = typed("minecraft:count");
		count.put("count", tries);
		placement.add(count);
		Map<String, Object> offset = typed("minecraft:offset");
		offset.put("x", trapezoid(cfg.getOrDefault("xz_spread", 7L)));
		offset.put("y", trapezoid(cfg.getOrDefault("y_spread", 3L)));
		offset.put("z", trapezoid(cfg.getOrDefault("xz_spread", 7L)));
		placement.add(offset);
		if (inner.get("placement") instanceof List<?> tail) placement.addAll(tail);

		Map<String, Object> placed = new LinkedHashMap<>();
		placed.put("feature", featureRef(inner.get("feature"), stats));
		placed.put("placement", placement);
		Map<String, Object> out = typed("minecraft:overlay");
		out.put("features", List.of(placed));
		return out;
	}

	/** Vanilla's replacement for the columns feature: its old 90 percent branch and the other 10 percent. */
	private static Map<String, Object> basalt(Map<String, Object> cfg) {
		List<Object> entries = new ArrayList<>();
		entries.add(basaltEntry(cfg, 5, 50, 9));
		entries.add(basaltEntry(cfg, 8, 15, 1));
		Map<String, Object> out = typed("minecraft:weighted_random_selector");
		out.put("features", entries);
		return out;
	}

	private static Map<String, Object> basaltEntry(Map<String, Object> cfg, long clusterReach, long columnCount, long weight) {
		Map<String, Object> f = typed("minecraft:stepped_column_cluster");
		Map<String, Object> block = new LinkedHashMap<>();
		block.put("id", "minecraft:basalt");
		block.put("properties", Map.of("axis", "y"));
		f.put("block", block);
		Map<String, Object> air = typed("minecraft:matching_block_tag");
		air.put("tag", "minecraft:air");
		Map<String, Object> lava = typed("minecraft:matching_blocks");
		lava.put("blocks", "minecraft:lava");
		Map<String, Object> range = typed("minecraft:height_range");
		range.put("max_inclusive", Map.of("relative_to_sea_level", 0L));
		range.put("min_inclusive", Map.of("above_bottom", 0L));
		Map<String, Object> lavaBelow = typed("minecraft:all_of");
		lavaBelow.put("predicates", List.of(lava, range));
		Map<String, Object> canReplace = typed("minecraft:any_of");
		canReplace.put("predicates", List.of(air, lavaBelow));
		f.put("can_replace", canReplace);
		f.put("cannot_place_on", "#minecraft:cannot_place_basalt_pillar_on");
		f.put("cluster_reach", clusterReach);
		f.put("column_count", columnCount);
		f.put("column_reach", cfg.get("reach"));
		Map<String, Object> through = typed("minecraft:matching_blocks");
		through.put("blocks", "minecraft:basalt");
		f.put("continue_through", through);
		f.put("height", cfg.get("height"));

		Map<String, Object> placed = new LinkedHashMap<>();
		placed.put("feature", f);
		placed.put("placement", List.of());
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("data", placed);
		entry.put("weight", weight);
		return entry;
	}

	/** The tree-from-structure feature moved from a nested config to a flat second version. */
	private static Map<String, Object> tree(Map<String, Object> cfg) {
		Map<String, Object> out = typed("ohthetreesyoullgrow:tree_from_nbt_v2");
		for (Map.Entry<String, Object> e : cfg.entrySet()) {
			switch (e.getKey()) {
			case "place_from_nbt" -> { }
			case "leaves_provider" -> out.put("leaves_provider", e.getValue() instanceof List<?> ? e.getValue() : List.of(e.getValue()));
			default -> out.put(e.getKey(), e.getValue());
			}
		}
		out.put("replace_from_nbt", new LinkedHashMap<>());
		return out;
	}

	// ---- keys 26.3 removed, rebuilt from their old shapes ----

	/**
	 * Features of the removed vanilla patch family, as they were defined before the rework: one block
	 * placed {@code tries} times around the origin. The inner features are the ones that survived.
	 * Returns the file name (under {@code worldgen/feature/}) to the converted tree.
	 */
	public static Map<String, Map<String, Object>> removedPatches() {
		Map<String, Map<String, Object>> out = new LinkedHashMap<>();
		out.put("patch_cactus", legacyPatch("minecraft:cactus", 10, allOf(airTag(), wouldSurvive("minecraft:cactus"))));
		out.put("patch_grass", legacyPatch("minecraft:grass", 32, airTag()));
		out.put("patch_grass_jungle", legacyPatch("minecraft:grass_jungle", 32,
				allOf(airTag(), not(blockBelow("minecraft:podzol")))));
		out.put("patch_pumpkin", legacyPatch("minecraft:pumpkin", 96, allOf(airTag(), blockBelow("minecraft:grass_block"))));
		out.put("patch_waterlily", legacyPatch("minecraft:waterlily", 10, airTag()));
		return out;
	}

	private static Map<String, Object> legacyPatch(String feature, long tries, Map<String, Object> filter) {
		Map<String, Object> pred = typed("minecraft:block_predicate_filter");
		pred.put("predicate", filter);
		Map<String, Object> cfg = new LinkedHashMap<>();
		Map<String, Object> inner = new LinkedHashMap<>();
		inner.put("feature", feature);
		inner.put("placement", List.of(pred));
		cfg.put("feature", inner);
		cfg.put("tries", tries);
		cfg.put("xz_spread", 7L);
		cfg.put("y_spread", 3L);
		return patch(cfg, what -> { });
	}

	private static Map<String, Object> airTag() {
		Map<String, Object> m = typed("minecraft:matching_block_tag");
		m.put("tag", "minecraft:air");
		return m;
	}

	@SafeVarargs
	private static Map<String, Object> allOf(Map<String, Object>... predicates) {
		Map<String, Object> m = typed("minecraft:all_of");
		m.put("predicates", List.of(predicates));
		return m;
	}

	private static Map<String, Object> not(Map<String, Object> predicate) {
		Map<String, Object> m = typed("minecraft:not");
		m.put("predicate", predicate);
		return m;
	}

	private static Map<String, Object> wouldSurvive(String block) {
		Map<String, Object> m = typed("minecraft:would_survive");
		m.put("state", block);
		return m;
	}

	private static Map<String, Object> blockBelow(String block) {
		Map<String, Object> m = typed("minecraft:matching_blocks");
		m.put("blocks", block);
		m.put("offset", List.of(0L, -1L, 0L));
		return m;
	}
}
