package info.mudbourn.mmsuproot.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import info.mudbourn.mmsuproot.boot.Report;
import info.mudbourn.mmsuproot.util.Json;
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * The converted data of one mod pack: some files served under other paths, some rewritten, a few
 * made up. Built on the first request for server data, once per pack, from the pack's own listing.
 */
final class ConvertedView {
	/** Returned by {@link #intercept} for every call the view leaves to the real pack. */
	static final Object PASS = new Object();

	private static final String OLD_FEATURES = "worldgen/configured_feature/";
	private static final String NEW_FEATURES = "worldgen/feature/";
	/** Folders whose files may hold block states, the old offset placement or spawn groups. */
	private static final List<String> GENERIC = List.of("worldgen/placed_feature/", "worldgen/processor_list/", "worldgen/structure/", "worldgen/biome/");

	private final ModPackResources pack;
	private final Report.Entry report;
	private volatile Built built;

	ConvertedView(ModPackResources pack, Report.Entry report) {
		this.pack = pack;
		this.report = report;
	}

	/** Files by "namespace:path". {@code served} replaces or adds, {@code hidden} is not served, {@code added} did not exist before. */
	private record Built(Map<String, byte[]> served, Set<String> hidden, Set<String> added, Set<String> namespaces) { }

	/** Handles the three calls that see data; everything else is {@link #PASS}. */
	Object intercept(Method method, Object[] args) throws IOException {
		switch (method.getName()) {
		case "getResource" -> {
			if (args.length != 2 || args[0] != PackType.SERVER_DATA) return PASS;
			Identifier id = (Identifier) args[1];
			String key = id.getNamespace() + ":" + id.getPath();
			Built b = built();
			byte[] bytes = b.served.get(key);
			if (bytes != null) return (IoSupplier<InputStream>) () -> new ByteArrayInputStream(bytes);
			return b.hidden.contains(key) ? null : PASS;
		}
		case "listResources" -> {
			if (args.length != 4 || args[0] != PackType.SERVER_DATA) return PASS;
			list((String) args[1], (String) args[2], (PackResources.ResourceOutput) args[3]);
			return null;
		}
		case "getNamespaces" -> {
			if (args.length != 1 || args[0] != PackType.SERVER_DATA) return PASS;
			Set<String> out = new HashSet<>(pack.getNamespaces(PackType.SERVER_DATA));
			out.addAll(built().namespaces);
			return out;
		}
		default -> {
			return PASS;
		}
		}
	}

	/** Whether a criterion waits for a recipe, named in a namespace of this pack, that the pack does not have. */
	private static boolean craftsMissingRecipe(Map<String, Object> advancement, Set<String> recipes, Set<String> namespaces) {
		if (!(advancement.get("criteria") instanceof Map<?, ?> criteria)) return false;
		for (Object c : criteria.values()) {
			if (!(c instanceof Map<?, ?> crit) || !"minecraft:recipe_crafted".equals(crit.get("trigger")) || !(crit.get("conditions") instanceof Map<?, ?> cond)) continue;
			Object named = cond.containsKey("recipe_id") ? cond.get("recipe_id") : cond.get("recipes");
			for (Object r : named instanceof List<?> l ? l : List.of(String.valueOf(named))) {
				Identifier id = Identifier.tryParse(String.valueOf(r));
				if (id != null && namespaces.contains(id.getNamespace()) && !recipes.contains(id.toString())) return true;
			}
		}
		return false;
	}

	private void list(String namespace, String path, PackResources.ResourceOutput out) {
		Built b = built();
		pack.listResources(PackType.SERVER_DATA, namespace, path, (id, supplier) -> {
			String key = namespace + ":" + id.getPath();
			if (b.hidden.contains(key)) return;
			byte[] bytes = b.served.get(key);
			out.accept(id, bytes != null ? () -> new ByteArrayInputStream(bytes) : supplier);
		});
		String prefix = namespace + ":" + path + "/";
		for (String key : b.added) {
			if (!key.startsWith(prefix)) continue;
			byte[] bytes = b.served.get(key);
			out.accept(Identifier.fromNamespaceAndPath(namespace, key.substring(namespace.length() + 1)), () -> new ByteArrayInputStream(bytes));
		}
	}

	private Built built() {
		Built b = built;
		if (b != null) return b;
		synchronized (this) {
			if (built == null) built = build();
			return built;
		}
	}

	private Built build() {
		Map<String, byte[]> served = new LinkedHashMap<>();
		Set<String> hidden = new HashSet<>();
		Set<String> added = new HashSet<>();
		Set<String> namespaces = new HashSet<>();
		FeatureJson.Stats stats = what -> report.dataConverted.computeIfAbsent(what, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();

		List<Object[]> files = new ArrayList<>();
		for (String ns : pack.getNamespaces(PackType.SERVER_DATA)) {
			pack.listResources(PackType.SERVER_DATA, ns, "worldgen", (id, supplier) -> files.add(new Object[] {ns, id.getPath(), supplier}));
		}

		Set<String> ownRecipes = new HashSet<>();
		for (String ns : pack.getNamespaces(PackType.SERVER_DATA)) {
			pack.listResources(PackType.SERVER_DATA, ns, "recipe", (id, supplier) -> {
				String path = id.getPath();
				if (path.endsWith(".json")) ownRecipes.add(ns + ":" + path.substring("recipe/".length(), path.length() - ".json".length()));
			});
		}
		Set<String> ownNamespaces = new HashSet<>(pack.getNamespaces(PackType.SERVER_DATA));

		Map<String, String> parents = new HashMap<>();
		Set<String> hiddenRecipes = new HashSet<>();
		List<String> timelines = new ArrayList<>();
		for (String ns : pack.getNamespaces(PackType.SERVER_DATA)) {
			for (String folder : List.of("recipe", "timeline", "advancement", "loot_table", "enchantment")) {
				pack.listResources(PackType.SERVER_DATA, ns, folder, (id, supplier) -> {
					if (!id.getPath().endsWith(".json")) return;
					String key = ns + ":" + id.getPath();
					try (InputStream in = supplier.get()) {
						Map<String, Object> root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
						if (folder.equals("recipe")) {
							// a recipe of a type whose serializer a mod could not register would fail the whole recipe registry
							if (root.get("type") instanceof String type && Identifier.tryParse(type) instanceof Identifier serializer
									&& !BuiltInRegistries.RECIPE_SERIALIZER.containsKey(serializer)) {
								hidden.add(key);
								hiddenRecipes.add(ns + ":" + id.getPath().substring("recipe/".length(), id.getPath().length() - ".json".length()));
								stats.count("recipes of unregistered types left out");
							}
							return;
						}
						if (folder.equals("advancement") && craftsMissingRecipe(root, ownRecipes, ownNamespaces)) {
							// 1.21.11 never checked the recipe a criterion names; 26.3 needs it to exist, and this one is not in the pack
							hidden.add(key);
							stats.count("advancements of recipes the pack lacks left out");
							return;
						}
						if (folder.equals("advancement") && root.get("rewards") instanceof Map<?, ?> rewards && rewards.get("recipes") instanceof List<?> unlocked
								&& unlocked.stream().anyMatch(r -> hiddenRecipes.contains(String.valueOf(r)))) {
							// the advancement only unlocks recipes that were left out
							hidden.add(key);
							return;
						}
						Object converted = switch (folder) {
							case "timeline" -> FeatureJson.timeline(root, stats);
							case "advancement" -> FeatureJson.advancement(root, stats);
							case "enchantment" -> FeatureJson.enchantment(root, stats);
							default -> FeatureJson.lootTable(root, stats);
						};
						if (converted == null) {
							hidden.add(key);
							return;
						}
						if (folder.equals("advancement") && root.get("parent") instanceof String parent) {
							parents.put(key, parent.contains(":") ? parent.replaceFirst(":", ":advancement/") + ".json" : "minecraft:advancement/" + parent + ".json");
						}
						if (folder.equals("timeline")) timelines.add(ns + ":" + id.getPath().substring("timeline/".length(), id.getPath().length() - ".json".length()));
						served.put(key, Json.write(converted).getBytes(StandardCharsets.UTF_8));
						report.dataConvertedFiles.incrementAndGet();
					} catch (IOException | RuntimeException e) {
						fail(key, e);
					}
				});
			}
		}
		hideOrphans(parents, hidden, served);
		tagTimelines(timelines, served, added, namespaces, stats);

		// A mod's own data registry reads from a folder named after the registry's namespace (data/<ns>/<registry namespace>/<registry path>).
		// What it holds may embed vanilla values in their 1.21.11 shapes (block states, state providers), which the generic pass reads by shape.
		Set<String> namespaceList = pack.getNamespaces(PackType.SERVER_DATA);
		for (String ns : namespaceList) {
			for (String registryNamespace : namespaceList) {
				if (registryNamespace.equals("minecraft")) continue;
				pack.listResources(PackType.SERVER_DATA, ns, registryNamespace, (id, supplier) -> {
					if (!id.getPath().endsWith(".json")) return;
					String key = ns + ":" + id.getPath();
					try (InputStream in = supplier.get()) {
						Map<String, Object> root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
						Object converted = FeatureJson.generic(root, stats);
						if (converted.equals(root)) return;
						served.put(key, Json.write(converted).getBytes(StandardCharsets.UTF_8));
						report.dataConvertedFiles.incrementAndGet();
					} catch (IOException | RuntimeException e) {
						fail(key, e);
					}
				});
			}
		}

		for (String ns : pack.getNamespaces(PackType.SERVER_DATA)) {
			pack.listResources(PackType.SERVER_DATA, ns, "structure", (id, supplier) -> {
				if (!id.getPath().endsWith(".nbt")) return;
				String key = ns + ":" + id.getPath();
				try (InputStream in = supplier.get()) {
					byte[] fixed = StructureNbt.fix(in.readAllBytes(), stats);
					if (fixed == null) return;
					served.put(key, fixed);
					report.dataConvertedFiles.incrementAndGet();
				} catch (IOException | RuntimeException e) {
					fail(key, e);
				}
			});
		}

		boolean referencesPatches = false;
		for (Object[] f : files) {
			String ns = (String) f[0];
			String path = (String) f[1];
			if (!path.endsWith(".json")) continue;
			boolean feature = path.startsWith(OLD_FEATURES);
			if (!feature && GENERIC.stream().noneMatch(path::startsWith)) continue;

			String text;
			try (InputStream in = ((IoSupplier<InputStream>) f[2]).get()) {
				text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				fail(ns + ":" + path, e);
				continue;
			}
			String outPath = feature ? NEW_FEATURES + path.substring(OLD_FEATURES.length()) : path;
			String key = ns + ":" + path;
			try {
				Map<String, Object> root = Json.parseObject(text);
				Object converted;
				if (feature) converted = FeatureJson.configured(root, stats);
				else if (path.startsWith("worldgen/placed_feature/")) converted = FeatureJson.placed(root, stats);
				else converted = FeatureJson.generic(root, stats);
				String json = Json.write(converted);
				if (json.contains("\"minecraft:patch_")) referencesPatches = true;
				served.put(ns + ":" + outPath, json.getBytes(StandardCharsets.UTF_8));
				report.dataConvertedFiles.incrementAndGet();
			} catch (RuntimeException e) {
				fail(key, e);
				if (feature) served.put(ns + ":" + outPath, text.getBytes(StandardCharsets.UTF_8));
			}
			if (feature) {
				hidden.add(key);
				added.add(ns + ":" + outPath);
			}
		}

		if (referencesPatches) {
			for (Map.Entry<String, Map<String, Object>> e : FeatureJson.removedPatches().entrySet()) {
				String key = "minecraft:" + NEW_FEATURES + e.getKey() + ".json";
				served.put(key, Json.write(e.getValue()).getBytes(StandardCharsets.UTF_8));
				added.add(key);
			}
			namespaces.add("minecraft");
			report.dataConverted.computeIfAbsent("rebuilt minecraft patch features", k -> new java.util.concurrent.atomic.AtomicInteger()).addAndGet(5);
		}
		return new Built(served, hidden, added, namespaces);
	}

	/**
	 * 26.3 runs only the timelines a dimension's tag lists. 1.21.11 mods listed theirs in code, so a timeline no tag of the pack
	 * names (as the clock of every converted one is the overworld's) joins {@code minecraft:timeline/in_overworld}.
	 */
	private void tagTimelines(List<String> timelines, Map<String, byte[]> served, Set<String> added, Set<String> namespaces, FeatureJson.Stats stats) {
		if (timelines.isEmpty()) return;
		StringBuilder tagged = new StringBuilder();
		for (String ns : pack.getNamespaces(PackType.SERVER_DATA)) {
			pack.listResources(PackType.SERVER_DATA, ns, "tags/timeline", (id, supplier) -> {
				try (InputStream in = supplier.get()) {
					tagged.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				} catch (IOException | RuntimeException e) {
					fail(ns + ":" + id.getPath(), e);
				}
			});
		}
		List<Object> values = new ArrayList<>();
		IoSupplier<InputStream> own = pack.getResource(PackType.SERVER_DATA, Identifier.parse("minecraft:tags/timeline/in_overworld.json"));
		if (own != null) {
			// the pack's own file of that name is replaced by this one, so its entries come along
			try (InputStream in = own.get()) {
				if (Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)).get("values") instanceof List<?> existing) values.addAll(existing);
			} catch (IOException | RuntimeException e) {
				fail("minecraft:tags/timeline/in_overworld.json", e);
			}
		}
		int own0 = values.size();
		for (String timeline : timelines) {
			if (tagged.indexOf("\"" + timeline + "\"") >= 0) continue;
			values.add(timeline);
			stats.count("timelines added to the overworld tag");
		}
		if (values.size() == own0) return;
		String key = "minecraft:tags/timeline/in_overworld.json";
		Map<String, Object> tag = new LinkedHashMap<>();
		tag.put("values", values);
		served.put(key, Json.write(tag).getBytes(StandardCharsets.UTF_8));
		added.add(key);
		namespaces.add("minecraft");
	}

	/** An advancement whose parent was left out has nothing to hang from; it goes too, down the chain. */
	private void hideOrphans(Map<String, String> parents, Set<String> hidden, Map<String, byte[]> served) {
		boolean again = true;
		while (again) {
			again = false;
			for (Map.Entry<String, String> e : parents.entrySet()) {
				if (hidden.contains(e.getValue()) && hidden.add(e.getKey())) {
					served.remove(e.getKey());
					again = true;
				}
			}
		}
	}

	private void fail(String file, Exception e) {
		report.dataFailures.add(file + ": " + e);
	}
}
