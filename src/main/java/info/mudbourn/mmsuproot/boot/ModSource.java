package info.mudbourn.mmsuproot.boot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import info.mudbourn.mmsuproot.remap.AccessWideners;
import info.mudbourn.mmsuproot.disable.Disabler;
import info.mudbourn.mmsuproot.remap.ClassRemap;
import info.mudbourn.mmsuproot.remap.Namer;
import info.mudbourn.mmsuproot.tool.Ranges;
import info.mudbourn.mmsuproot.util.Json;
import info.mudbourn.mmsuproot.util.PackFormat;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A mod Uproot rewrites: where its classes live and how to rename them. */
public final class ModSource {
	private static final byte[] MARKER = "net/minecraft/class_".getBytes(StandardCharsets.ISO_8859_1);
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");
	private static final int[] MINECRAFT = {26, 3, 0};
	/**
	 * Names that exist only before 26.3 (absent from its jar), the left-hand sides of renames-26x.txt that a class file can show
	 * on its own: the GPU buffer slice and render pipeline moved into renderpearl, the locator bar renderer was renamed, and
	 * ResourceManager.listResources took a Predicate, Fabric API's brewing event was renamed and AxeItem was removed. Every string of an entry must be present in the same class file.
	 */
	private static final byte[][][] OLDER_26 = {
		{ascii("com/mojang/blaze3d/buffers/GpuBufferSlice")},
		{ascii("com/mojang/blaze3d/pipeline/RenderPipeline")},
		{ascii("net/minecraft/client/gui/contextualbar/LocatorBarRenderer")},
		{ascii("listResources"), ascii("(Ljava/lang/String;Ljava/util/function/Predicate;)Ljava/util/Map;")},
		{ascii("net/fabricmc/fabric/api/registry/FabricPotionBrewingBuilder")},
		{ascii("net/minecraft/world/item/AxeItem")},
	};
	private static volatile Set<String> fabricApiIds;

	final ModContainerImpl mod;
	final String id;
	final Namer namer;
	final Report.Entry report;
	final Map<String, Path> classes = new HashMap<>();

	ModSource(ModContainerImpl mod, Namer namer, Report.Entry report) {
		this.mod = mod;
		this.id = mod.getMetadata().getId();
		this.namer = namer;
		this.report = report;
	}

	/** Index of every class file in the mod. */
	void index() throws IOException {
		for (Path root : mod.getRootPaths()) {
			try (Stream<Path> walk = Files.walk(root)) {
				for (Path p : (Iterable<Path>) walk::iterator) {
					String rel = root.relativize(p).toString().replace('\\', '/');
					if (!rel.endsWith(".class")) continue;
					String internal = rel.substring(0, rel.length() - 6);
					classes.put(internal, p);
					namer.mods().put(internal, p);
				}
			}
		}
	}

	/** The class rewritten to 26.3 names, or null when this mod does not hold it. */
	byte[] remapped(String internalName, boolean count) throws IOException {
		Path p = classes.get(internalName);
		if (p == null) return null;
		byte[] out;
		try {
			out = Disabler.transform(internalName, ClassRemap.remap(Files.readAllBytes(p), namer), report.legacy);
		} catch (RuntimeException e) {
			report.classesFailed.incrementAndGet();
			throw e;
		}
		if (count) report.classesRemapped.incrementAndGet();
		return out;
	}

	/**
	 * Remaps every class once so the report lists all references to Minecraft members that 26.3 lacks,
	 * not only those of classes the game happened to load. Only for {@code -Duproot.auditAll=true}; with
	 * {@code -Duproot.dumpRemapped=<dir>} as well, each remapped class is also written under {@code <dir>/<mod id>/}.
	 */
	void auditAll() {
		String dump = System.getProperty("uproot.dumpRemapped");
		for (String name : new ArrayList<>(classes.keySet())) {
			try {
				byte[] bytes = remapped(name, false);
				if (dump != null && bytes != null) {
					Path out = Path.of(dump, id, name + ".class");
					Files.createDirectories(out.getParent());
					Files.write(out, bytes);
				}
			} catch (IOException | RuntimeException | LinkageError e) {
				report.notes.add("audit could not read " + name + ": " + e);
			}
		}
	}

	/** Names of the mod's mixin configs for the current side. */
	List<String> mixinConfigs(net.fabricmc.api.EnvType side) {
		return new ArrayList<>(mod.getMetadata().getMixinConfigs(side));
	}

	/**
	 * True when the mod refers to intermediary names: an intermediary access widener or any class that
	 * mentions them. Every class file is scanned, stopping at the first hit, so a mod with few such
	 * classes is not missed. A mod that cannot be read is logged and reported, then treated as not a target.
	 */
	static boolean refersToIntermediary(ModContainerImpl mod, Report report) {
		LoaderModMetadata meta = mod.getInfo();
		try {
			String aw = meta.getClassTweaker();
			if (aw != null) {
				Path p = mod.findPath(aw).orElse(null);
				if (p != null) {
					String ns = AccessWideners.namespace(Files.readString(p));
					if ("intermediary".equals(ns)) return true;
				}
			}

			for (Path root : mod.getRootPaths()) {
				try (Stream<Path> walk = Files.walk(root)) {
					for (Path p : (Iterable<Path>) walk::iterator) {
						if (p.getFileName() == null || !p.getFileName().toString().endsWith(".class")) continue;
						if (mentionsIntermediary(Files.readAllBytes(p))) return true;
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not tell whether {} refers to intermediary names; not remapping it", meta.getId(), e);
			report.note(meta.getId() + ": could not be scanned for intermediary names, so it was not remapped: " + e);
		}
		return false;
	}

	/** Whether a class file names an intermediary class. */
	public static boolean mentionsIntermediary(byte[] classFile) {
		return indexOf(classFile, MARKER) >= 0;
	}

	/**
	 * True when the mod was built for a 26.x older than 26.3, by what its own {@code fabric.mod.json} and class files say.
	 * Loader's dependency overrides rewrite the metadata in memory, so the file in the jar is read instead. A mod that names
	 * no Minecraft version is left alone. A range that excludes 26.3 counts when it admits 26.1 or 26.2. An open range such
	 * as {@code >=26.2} admits 26.3 and says nothing about the build, so such a mod counts only when a class file names
	 * something 26.3 no longer has ({@link #OLDER_26}); a 26.3 build never does.
	 */
	static boolean builtForOlder26(ModContainerImpl mod, Report report) {
		try {
			for (Path root : mod.getRootPaths()) {
				Path file = root.resolve("fabric.mod.json");
				if (!Files.isRegularFile(file)) continue;
				Boolean byRange = olderByRange(Json.parseObject(Files.readString(file)));
				return byRange != null ? byRange : namesPre263(mod);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not tell whether {} was built for an older 26.x; not remapping it", mod.getMetadata().getId(), e);
			report.note(mod.getMetadata().getId() + ": could not be scanned for an older 26.x build, so it was not remapped: " + e);
		}
		return false;
	}

	/**
	 * True when the mod's own {@code fabric.mod.json} admits 26.3 and nothing older (no 1.21.x, 26.1 or 26.2), such as "26.3" or
	 * "~26.3". Such a mod is a native 26.3 build and is never remapped, even if a class names intermediary classes: Streams Reflowing,
	 * for one, carries optional compat mixins that target other mods' 1.21.11 names.
	 */
	static boolean declares263Only(ModContainerImpl mod) {
		try {
			for (Path root : mod.getRootPaths()) {
				Path file = root.resolve("fabric.mod.json");
				if (!Files.isRegularFile(file)) continue;
				Map<String, Object> fmj = Json.parseObject(Files.readString(file));
				if (!(fmj.get("depends") instanceof Map<?, ?> deps) || !deps.containsKey("minecraft")) return false;
				Object range = deps.get("minecraft");
				if (!Ranges.admits(range, MINECRAFT)) return false;
				for (int[] v : new int[][] {{1, 21, 0}, {1, 21, 1}, {1, 21, 4}, {1, 21, 8}, {1, 21, 10}, {1, 21, 11}}) {
					if (Ranges.admits(range, v)) return false;
				}
				for (int minor = 1; minor <= 2; minor++) {
					for (int patch = 0; patch <= 9; patch++) {
						if (Ranges.admits(range, new int[] {26, minor, patch})) return false;
					}
				}
				return true;
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read the Minecraft range of {}", mod.getMetadata().getId(), e);
		}
		return false;
	}

	/**
	 * True when the mod's own {@code fabric.mod.json} admits 1.21.1: a build for the 1.21.1 era, whose intermediary names include some that 1.21.11 dropped, so
	 * its names are also read from the 1.21.1 table. Loader's overrides rewrite the metadata in memory, so the file in the jar is read.
	 */
	static boolean builtFor1211(ModContainerImpl mod) {
		try {
			for (Path root : mod.getRootPaths()) {
				Path file = root.resolve("fabric.mod.json");
				if (!Files.isRegularFile(file)) continue;
				Map<String, Object> fmj = Json.parseObject(Files.readString(file));
				if (fmj.get("depends") instanceof Map<?, ?> deps && deps.containsKey("minecraft")) return Ranges.admits(deps.get("minecraft"), new int[] {1, 21, 1});
				return false;
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read the Minecraft range of {}", mod.getMetadata().getId(), e);
		}
		return false;
	}

	/** What a {@code fabric.mod.json} says about an older 26.x build: true or false, or null when only the class files can tell (an open range). */
	public static Boolean olderByRange(Map<String, Object> fmj) {
		if (!(fmj.get("depends") instanceof Map<?, ?> deps) || !deps.containsKey("minecraft")) return false;
		Object range = deps.get("minecraft");
		// every patch release of 26.1 and 26.2 counts: a range such as ~26.1.1 admits 26.1.1 but not 26.1.0
		boolean older = false;
		for (int minor = 1; minor <= 2 && !older; minor++) {
			for (int patch = 0; patch <= 9; patch++) {
				if (Ranges.admits(range, new int[] {26, minor, patch})) { older = true; break; }
			}
		}
		if (!Ranges.admits(range, MINECRAFT)) return older;
		// A range that admits 26.3 but no 26.1 or 26.2 (such as "26.3" or "~26.3") is a 26.3 build: never remap it,
		// even if a class file happens to hold one of the OLDER_26 names.
		return older ? null : false;
	}

	/**
	 * True for a mod that is only a data pack (no class file, a {@code data} folder) whose {@code pack.mcmeta} stops below the data format
	 * of 26.3. Its data is in the shapes of an older game, so it gets the data shim; it has no code for Uproot to rewrite.
	 */
	static boolean legacyDataPack(ModContainerImpl mod, Report report) {
		try {
			boolean hasData = false;
			int lowest = 0;
			for (Path root : mod.getRootPaths()) {
				try (Stream<Path> walk = Files.walk(root)) {
					for (Path p : (Iterable<Path>) walk::iterator) {
						if (p.getFileName() != null && p.getFileName().toString().endsWith(".class")) return false;
					}
				}
				if (!Files.isDirectory(root.resolve("data"))) continue;
				hasData = true;
				Path meta = root.resolve("pack.mcmeta");
				if (Files.isRegularFile(meta)) lowest = Math.max(lowest, PackFormat.lowestFormat(Json.parseObject(Files.readString(meta))).orElse(0));
			}
			// the newest of the roots decides (a pack.mcmeta that declares nothing counts as 0); every root is read before the answer, since a mod's roots are not in any order and a later one may hold the classes or the newer pack.mcmeta
			return hasData && lowest < PackFormat.DATA_FORMAT;
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not tell whether {} is a data pack for an older game; not converting it", mod.getMetadata().getId(), e);
			report.note(mod.getMetadata().getId() + ": could not be scanned as a data pack, so its data was not converted: " + e);
		}
		return false;
	}

	/**
	 * True for a mod built for an older 26.x that still ships {@code worldgen/configured_feature}, a folder 26.3 renamed to {@code worldgen/feature}.
	 * Its {@code pack.mcmeta} cannot say this: 26.x mods often carry a stale format, so the folder is the mark.
	 */
	static boolean shipsOldWorldgen(ModContainerImpl mod, Report report) {
		try {
			for (Path root : mod.getRootPaths()) {
				// the jar's own data, and built-in packs a mod registers from a folder of the jar (resourcepacks/<name>/data, datapack/<name>/data)
				try (Stream<Path> walk = Files.walk(root, 3)) {
					for (Path data : (Iterable<Path>) walk::iterator) {
						if (data.getFileName() == null || !data.getFileName().toString().equals("data") || !Files.isDirectory(data)) continue;
						try (Stream<Path> namespaces = Files.list(data)) {
							// configured_feature is the folder 26.3 renamed; enchantment, feature and advancement files of a 26.1 or 26.2 build can keep
							// shapes 26.3 dropped (a loot condition keyed "condition", a feature's "config" level, a recipe_unlocked "recipe"), which the
							// converters only touch when present
							if (namespaces.anyMatch(ns -> Files.isDirectory(ns.resolve("worldgen/configured_feature")) || Files.isDirectory(ns.resolve("enchantment"))
									|| Files.isDirectory(ns.resolve("worldgen/feature")) || Files.isDirectory(ns.resolve("advancement")))) return true;
						}
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not tell whether {} ships old worldgen; not converting its data", mod.getMetadata().getId(), e);
			report.note(mod.getMetadata().getId() + ": could not be scanned for old worldgen, so its data was not converted: " + e);
		}
		return false;
	}

	/** Whether any class file of the mod contains every byte string of one entry of {@link #OLDER_26}. Each file is read once. */
	private static boolean namesPre263(ModContainerImpl mod) throws IOException {
		for (Path root : mod.getRootPaths()) {
			try (Stream<Path> walk = Files.walk(root)) {
				for (Path p : (Iterable<Path>) walk::iterator) {
					if (p.getFileName() == null || !p.getFileName().toString().endsWith(".class")) continue;
					if (namesPre263(Files.readAllBytes(p))) return true;
				}
			}
		}
		return false;
	}

	/** Whether a class file contains every byte string of one entry of {@link #OLDER_26}. */
	public static boolean namesPre263(byte[] data) {
		for (byte[][] names : OLDER_26) {
			boolean all = true;
			for (byte[] name : names) {
				if (indexOf(data, name) < 0) { all = false; break; }
			}
			if (all) return true;
		}
		return false;
	}

	private static byte[] ascii(String s) {
		return s.getBytes(StandardCharsets.ISO_8859_1);
	}

	private static int indexOf(byte[] data, byte[] pat) {
		outer:
		for (int i = 0; i <= data.length - pat.length; i++) {
			for (int j = 0; j < pat.length; j++) if (data[i + j] != pat[j]) continue outer;
			return i;
		}
		return -1;
	}

	/** Ids that never get remapped: the platform, Uproot itself and Fabric API in all its module names. */
	static boolean skip(ModContainerImpl mod) {
		ModMetadata meta = mod.getMetadata();
		if (skipId(meta.getId()) || "builtin".equals(meta.getType())) return true;
		String id = meta.getId();
		for (Optional<ModContainer> c = mod.getContainingMod(); c.isPresent(); c = c.get().getContainingMod()) {
			if (c.get().getMetadata().getId().equals("fabric-api")) return true;
		}
		return FabricLoader.getInstance().getModContainer("fabric-api")
				.map(api -> api.getMetadata().getProvides().stream().anyMatch(prov -> prov.equals(id))).orElse(false);
	}

	/** Ids that never get remapped, by id alone. */
	public static boolean skipId(String id) {
		return id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals("mms_uproot")
				|| id.startsWith("fabric-") || fabricApiIds().contains(id);
	}

	/** Both columns of the alias list: the old and the new Fabric API module ids. */
	private static Set<String> fabricApiIds() {
		Set<String> ids = fabricApiIds;
		if (ids != null) return ids;
		ids = new HashSet<>();
		ids.add("fabric-api");
		try (InputStream in = ModSource.class.getResourceAsStream("/uproot/fabric-api-aliases.txt")) {
			if (in != null) {
				for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
					line = line.strip();
					if (line.isEmpty() || line.charAt(0) == '#') continue;
					ids.addAll(List.of(line.split("\\s+")));
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Could not read the Fabric API alias list", e);
		}
		fabricApiIds = ids;
		return ids;
	}
}
