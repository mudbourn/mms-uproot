package info.mudbourn.mmsuproot.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import info.mudbourn.mmsuproot.util.Json;

/**
 * The content side of {@link IntersectTool}: what a jar's {@code assets/} and {@code data/} (and its built-in resource and data
 * packs under {@code resourcepacks/}, {@code datapacks/}) declare.
 *
 * <p>Node keys: {@code id:PATH} (a block, item, entity, enchantment, effect or biome, namespace stripped), {@code name:TEXT} (its
 * English display name, normalised), {@code tag:REGISTRY/NS:PATH}, {@code override:FULL/PATH} (a file in a namespace the mod does not
 * own), {@code recipe:TYPE|SIGNATURE}, {@code modifier:biome:TARGET} and {@code modifier:loot:TABLE}.
 */
final class IntersectContent {
	private IntersectContent() { }

	/** What one jar declares, for the coverage check and the update diff. Ids are {@code ns:path}. */
	static final class ModContent {
		final Set<String> items = new TreeSet<>();
		final Set<String> blocks = new TreeSet<>();
		final Set<String> entities = new TreeSet<>();
		final Set<String> enchantments = new TreeSet<>();
		/** Item tags this jar defines, {@code ns:path} to the entries (tags as {@code #ns:path}). */
		final Map<String, Set<String>> itemTags = new HashMap<>();
		/** Items that a weapon_attributes file names. */
		final Set<String> weaponAttributes = new TreeSet<>();
		/** Every {@code assets/} and {@code data/} path of the jar, kept for the vanilla jar only. */
		final Set<String> paths = new HashSet<>();
	}

	private static final Pattern LANG = Pattern.compile("^(item|block|entity|enchantment|effect|biome)\\.([^.]+)\\.([^.]+)$");
	private static final String[] PACK_DIRS = {"resourcepacks", "datapacks", "resourcepack", "datapack"};

	/** Walks the jar root and the packs inside it. */
	static ModContent scan(String mod, Path root, boolean legacy, boolean keepPaths, Touches out, List<String> notes) {
		ModContent c = new ModContent();
		scanRoot(mod, root, "", legacy, keepPaths, c, out, notes);
		for (String dir : PACK_DIRS) {
			Path packs = root.resolve(dir);
			if (!Files.isDirectory(packs)) continue;
			try (Stream<Path> list = Files.list(packs)) {
				for (Path pack : list.filter(Files::isDirectory).toList()) {
					String name = pack.getFileName().toString().replaceAll("/$", "");
					scanRoot(mod, pack, "pack:" + dir + "/" + name, legacy, false, c, out, notes);
				}
			} catch (IOException e) {
				notes.add(mod + ": " + dir + " could not be listed: " + e);
			}
		}
		if (!mod.equals("minecraft")) {
			// a lang or model file that mentions a vanilla id (a lang override) does not make it the mod's own content
			for (Set<String> set : List.of(c.items, c.blocks, c.entities, c.enchantments)) set.removeIf(id -> id.startsWith("minecraft:"));
		}
		return c;
	}

	private static void scanRoot(String mod, Path root, String pack, boolean legacy, boolean keepPaths, ModContent c, Touches out, List<String> notes) {
		for (String top : new String[] {"assets", "data"}) {
			Path base = root.resolve(top);
			if (!Files.isDirectory(base)) continue;
			List<Path> files;
			try (Stream<Path> walk = Files.walk(base)) {
				files = walk.filter(Files::isRegularFile).toList();
			} catch (IOException | RuntimeException e) {
				notes.add(mod + ": " + top + " could not be walked: " + e);
				continue;
			}
			for (Path f : files) {
				String rel = root.relativize(f).toString().replace('\\', '/');
				try {
					file(mod, pack, rel, f, legacy, keepPaths, c, out);
				} catch (IOException | RuntimeException e) {
					notes.add(mod + ": " + rel + " could not be read: " + e.getMessage());
				}
			}
		}
	}

	private static boolean own(String ns, String mod) {
		return ns.equals(mod) || ns.equals(mod.replace('-', '_')) || ns.equals(mod.replace('_', '-'));
	}

	private static void file(String mod, String pack, String rel, Path f, boolean legacy, boolean keepPaths, ModContent c, Touches out) throws IOException {
		String[] parts = rel.split("/", 3);
		if (parts.length < 3) return;
		String top = parts[0];
		String ns = parts[1];
		String sub = parts[2];
		String name = sub.substring(sub.lastIndexOf('/') + 1);
		if (name.startsWith(".") || name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".license")) return;
		if (keepPaths) c.paths.add(rel);
		String where = pack.isEmpty() ? "" : " " + pack;
		boolean json = name.endsWith(".json");
		String stem = json ? sub.substring(0, sub.length() - 5) : sub;
		if (!own(ns, mod) && !sub.startsWith("tags/")) {
			out.addOnce("override:" + rel, new Touch(mod, "override", IntersectCode.cap("ships " + rel + where), 3, legacy, false));
		}
		if (!json) return;
		if (top.equals("assets")) {
			if (sub.startsWith("blockstates/")) {
				String path = stem.substring("blockstates/".length());
				c.blocks.add(ns + ":" + path);
				id(out, mod, "blockstate" + where, path, legacy);
			} else if (sub.startsWith("items/")) {
				String path = stem.substring("items/".length());
				c.items.add(ns + ":" + path);
				id(out, mod, "item model" + where, path, legacy);
			} else if (sub.equals("lang/en_us.json")) {
				lang(mod, f, legacy, c, out);
			}
			return;
		}
		// data
		if (sub.startsWith("loot_table/blocks/") || sub.startsWith("loot_tables/blocks/")) {
			id(out, mod, "block loot table" + where, stem.substring(stem.indexOf("blocks/") + 7), legacy);
		} else if (sub.startsWith("loot_table/entities/") || sub.startsWith("loot_tables/entities/")) {
			c.entities.add(ns + ":" + stem.substring(stem.indexOf("entities/") + 9));
		} else if (sub.startsWith("enchantment/")) {
			String path = stem.substring("enchantment/".length());
			c.enchantments.add(ns + ":" + path);
			id(out, mod, "enchantment" + where, path, legacy);
		} else if (sub.startsWith("weapon_attributes/")) {
			c.weaponAttributes.add(ns + ":" + stem.substring("weapon_attributes/".length()));
		} else if (sub.startsWith("tags/")) {
			tag(mod, pack, ns, stem.substring(5), f, legacy, c, out);
		} else if (sub.startsWith("recipe/") || sub.startsWith("recipes/")) {
			recipe(mod, pack, f, legacy, out);
		} else if (sub.startsWith("forge/biome_modifier/") || sub.startsWith("neoforge/biome_modifier/")) {
			biomeModifier(mod, f, legacy, out);
		} else if (sub.startsWith("loot_modifiers/") && !name.equals("global_loot_modifiers.json")) {
			lootModifier(mod, f, legacy, out);
		}
	}

	private static void id(Touches out, String mod, String kind, String path, boolean legacy) {
		out.addOnce("id:" + path, new Touch(mod, kind, "", 1, legacy, false));
	}

	private static String text(Path f) throws IOException {
		return Files.readString(f, StandardCharsets.UTF_8);
	}

	private static void lang(String mod, Path f, boolean legacy, ModContent c, Touches out) throws IOException {
		Map<String, Object> map = Json.parseObject(text(f));
		for (Map.Entry<String, Object> e : map.entrySet()) {
			Matcher m = LANG.matcher(e.getKey());
			if (!m.matches() || !(e.getValue() instanceof String value)) continue;
			String kind = m.group(1);
			String path = m.group(3);
			String id = m.group(2) + ":" + path;
			switch (kind) {
			case "item" -> c.items.add(id);
			case "block" -> c.blocks.add(id);
			case "entity" -> c.entities.add(id);
			case "enchantment" -> c.enchantments.add(id);
			default -> { }
			}
			id(out, mod, "lang " + kind, path, legacy);
			String norm = normalize(value);
			if (norm.length() >= 3 && value.indexOf('%') < 0) out.addOnce("name:" + norm, new Touch(mod, "lang " + kind, IntersectCode.cap(e.getKey() + " = " + value), 1.5, legacy, false));
		}
	}

	/** Lower case, colour codes and punctuation removed, white space collapsed. */
	static String normalize(String s) {
		return s.replaceAll("§.", "").toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", "").replaceAll("\\s+", " ").strip();
	}

	private static final Pattern REPLACE = Pattern.compile("\"replace\"\\s*:\\s*true");

	private static void tag(String mod, String pack, String ns, String rest, Path f, boolean legacy, ModContent c, Touches out) throws IOException {
		String[] seg = rest.split("/");
		int regLen = seg[0].equals("worldgen") ? 2 : 1;
		if (seg.length <= regLen) return;
		String registry = String.join("/", Arrays.copyOfRange(seg, 0, regLen));
		String path = String.join("/", Arrays.copyOfRange(seg, regLen, seg.length));
		String body = text(f);
		boolean replace = REPLACE.matcher(body).find();
		out.addOnce("tag:" + registry + "/" + ns + ":" + path, new Touch(mod, "tag" + (pack.isEmpty() ? "" : " " + pack), replace ? "replace=true" : "", replace ? 4 : 1, legacy, false));
		if (registry.equals("item") || registry.equals("items")) {
			Object values = Json.parseObject(body).get("values");
			Set<String> set = c.itemTags.computeIfAbsent(ns + ":" + path, k -> new TreeSet<>());
			if (values instanceof List<?> l) {
				for (Object o : l) {
					if (o instanceof String s) set.add(s);
					else if (o instanceof Map<?, ?> m && m.get("id") instanceof String s) set.add(s);
				}
			}
		}
	}

	// ---- recipes

	private static String ingredient(Object o) {
		if (o instanceof String s) return s;
		if (o instanceof List<?> l) {
			List<String> parts = new ArrayList<>();
			for (Object e : l) parts.add(ingredient(e));
			parts.sort(null);
			return "[" + String.join("|", parts) + "]";
		}
		if (o instanceof Map<?, ?> m) {
			if (m.get("item") instanceof String s) return s;
			if (m.get("id") instanceof String s) return s;
			if (m.get("tag") instanceof String s) return "#" + s;
		}
		return "?";
	}

	private static String result(Object o) {
		if (o instanceof String s) return s;
		if (o instanceof Map<?, ?> m) {
			String id = m.get("id") instanceof String s ? s : m.get("item") instanceof String s2 ? s2 : "?";
			Object count = m.get("count");
			return count instanceof Number n && n.intValue() != 1 ? id + " x" + n.intValue() : id;
		}
		return "?";
	}

	private static void recipe(String mod, String pack, Path f, boolean legacy, Touches out) throws IOException {
		Map<String, Object> r = Json.parseObject(text(f));
		if (!(r.get("type") instanceof String type0)) return;
		String type = type0.startsWith("minecraft:") ? type0.substring(10) : type0;
		String sig;
		switch (type) {
		case "crafting_shaped" -> {
			if (!(r.get("pattern") instanceof List<?> rows) || !(r.get("key") instanceof Map<?, ?> key)) return;
			List<String> out2 = new ArrayList<>();
			for (Object row : rows) {
				StringBuilder sb = new StringBuilder();
				for (char ch : String.valueOf(row).toCharArray()) {
					if (sb.length() > 0) sb.append(',');
					sb.append(ch == ' ' ? "_" : ingredient(key.get(String.valueOf(ch))));
				}
				out2.add(sb.toString());
			}
			sig = String.join("/", out2);
		}
		case "crafting_shapeless" -> {
			if (!(r.get("ingredients") instanceof List<?> l)) return;
			List<String> parts = new ArrayList<>();
			for (Object o : l) parts.add(ingredient(o));
			parts.sort(null);
			sig = String.join(",", parts);
		}
		case "smelting", "blasting", "smoking", "campfire_cooking", "stonecutting" -> {
			if (r.get("ingredient") == null) return;
			sig = ingredient(r.get("ingredient"));
		}
		case "smithing_transform", "smithing_trim" -> sig = ingredient(r.get("template")) + "," + ingredient(r.get("base")) + "," + ingredient(r.get("addition"));
		default -> {
			return;
		}
		}
		out.addOnce("recipe:" + type + "|" + sig, new Touch(mod, "recipe" + (pack.isEmpty() ? "" : " " + pack), "-> " + result(r.get("result")), 2, legacy, false));
	}

	// ---- modifiers

	private static void biomeModifier(String mod, Path f, boolean legacy, Touches out) throws IOException {
		Map<String, Object> r = Json.parseObject(text(f));
		Object biomes = r.get("biomes");
		String type = String.valueOf(r.getOrDefault("type", "?"));
		for (Object o : biomes instanceof List<?> l ? l : biomes == null ? List.of() : List.of(biomes)) {
			out.addOnce("modifier:biome:" + o, new Touch(mod, "biome modifier", IntersectCode.cap(type), 2, legacy, false));
		}
	}

	private static void lootModifier(String mod, Path f, boolean legacy, Touches out) throws IOException {
		Object root = Json.parse(text(f));
		Set<String> tables = new TreeSet<>();
		findTables(root, tables);
		String type = root instanceof Map<?, ?> m ? String.valueOf(m.get("type")) : "?";
		for (String t : tables) out.addOnce("modifier:loot:" + t, new Touch(mod, "loot modifier", IntersectCode.cap(type), 2, legacy, false));
	}

	private static void findTables(Object o, Set<String> into) {
		if (o instanceof Map<?, ?> m) {
			for (Map.Entry<?, ?> e : m.entrySet()) {
				if (("loot_table".equals(e.getKey()) || "loot_table_id".equals(e.getKey()) || "table".equals(e.getKey())) && e.getValue() instanceof String s) into.add(s);
				else findTables(e.getValue(), into);
			}
		} else if (o instanceof List<?> l) {
			for (Object e : l) findTables(e, into);
		}
	}
}
