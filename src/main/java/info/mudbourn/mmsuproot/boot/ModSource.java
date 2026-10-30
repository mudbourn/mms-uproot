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
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A mod Uproot rewrites: where its classes live and how to rename them. */
final class ModSource {
	private static final byte[] MARKER = "net/minecraft/class_".getBytes(StandardCharsets.ISO_8859_1);
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");
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
			out = Disabler.transform(internalName, ClassRemap.remap(Files.readAllBytes(p), namer));
		} catch (RuntimeException e) {
			report.classesFailed.incrementAndGet();
			throw e;
		}
		if (count) report.classesRemapped.incrementAndGet();
		return out;
	}

	/**
	 * Remaps every class once so the report lists all references to Minecraft members that 26.3 lacks,
	 * not only those of classes the game happened to load. Only for {@code -Duproot.auditAll=true}.
	 */
	void auditAll() {
		for (String name : new ArrayList<>(classes.keySet())) {
			try {
				remapped(name, false);
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
						if (indexOf(Files.readAllBytes(p), MARKER) >= 0) return true;
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not tell whether {} refers to intermediary names; not remapping it", meta.getId(), e);
			report.note(meta.getId() + ": could not be scanned for intermediary names, so it was not remapped: " + e);
		}
		return false;
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
		String id = meta.getId();
		if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals("mms_uproot")
				|| id.startsWith("fabric-") || fabricApiIds().contains(id) || "builtin".equals(meta.getType())) {
			return true;
		}
		for (Optional<ModContainer> c = mod.getContainingMod(); c.isPresent(); c = c.get().getContainingMod()) {
			if (c.get().getMetadata().getId().equals("fabric-api")) return true;
		}
		return FabricLoader.getInstance().getModContainer("fabric-api")
				.map(api -> api.getMetadata().getProvides().stream().anyMatch(prov -> prov.equals(id))).orElse(false);
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
