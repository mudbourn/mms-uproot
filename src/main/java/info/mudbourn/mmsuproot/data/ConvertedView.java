package info.mudbourn.mmsuproot.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import info.mudbourn.mmsuproot.boot.Report;
import info.mudbourn.mmsuproot.util.Json;
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources;
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

		for (String ns : pack.getNamespaces(PackType.SERVER_DATA)) {
			for (String folder : List.of("timeline", "advancement")) {
				pack.listResources(PackType.SERVER_DATA, ns, folder, (id, supplier) -> {
					if (!id.getPath().endsWith(".json")) return;
					String key = ns + ":" + id.getPath();
					try (InputStream in = supplier.get()) {
						Map<String, Object> root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
						Object converted = folder.equals("timeline") ? FeatureJson.timeline(root, stats) : FeatureJson.advancement(root, stats);
						served.put(key, Json.write(converted).getBytes(StandardCharsets.UTF_8));
						report.dataConvertedFiles.incrementAndGet();
					} catch (IOException | RuntimeException e) {
						fail(key, e);
					}
				});
			}
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

	private void fail(String file, Exception e) {
		report.dataFailures.add(file + ": " + e);
	}
}
