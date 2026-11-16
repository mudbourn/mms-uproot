package info.mudbourn.mmsuproot.tool;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import info.mudbourn.mmsuproot.boot.ModSource;
import info.mudbourn.mmsuproot.boot.Report;
import info.mudbourn.mmsuproot.disable.Disabler;
import info.mudbourn.mmsuproot.map.GameClasses;
import info.mudbourn.mmsuproot.map.MappingFetcher;
import info.mudbourn.mmsuproot.map.NameTable;
import info.mudbourn.mmsuproot.map.Renames;
import info.mudbourn.mmsuproot.remap.AccessWideners;
import info.mudbourn.mmsuproot.remap.ClassRemap;
import info.mudbourn.mmsuproot.remap.ModClasses;
import info.mudbourn.mmsuproot.remap.Namer;
import info.mudbourn.mmsuproot.util.Json;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * Offline gap scanner: lists every reference in the target mods that 26.3 still lacks after Uproot's own remap, without
 * launching the game.
 *
 * <pre>java -cp &lt;mms-uproot and the game classes&gt; info.mudbourn.mmsuproot.tool.ScanTool [--out dir] [--cache dir] &lt;mods dir or jar&gt;...</pre>
 *
 * <p>Every jar (and the jars nested in it) is sorted by the same tests the boot hook uses: a mod that names intermediary classes
 * is remapped with {@code renames.txt}, one built for 26.1 or 26.2 with {@code renames-26x.txt}. Each class goes through
 * {@link ClassRemap} and {@link Disabler}, then its instructions, descriptors, signatures, supertypes and annotations are checked
 * against the game classes on the class path (the 26.3 jar and Fabric API). Writes {@code gaps.json} and {@code gaps.md}.
 */
public final class ScanTool {
	/** Owners the game and Fabric API provide; anything else (other mods, libraries, the JDK) is not judged. */
	private static final String[] GAME = {"net/minecraft/", "com/mojang/", "net/fabricmc/fabric/", "net/fabricmc/loader/", "net/fabricmc/api/"};

	private enum Kind { LEGACY, MODERN, NONE }

	/** What a missing reference means for the game, strongest first: only {@code REAL} is a gap to fix. */
	private enum Cat {
		REAL("real"), MIXIN_ADDED("mixin-added"), DATAGEN("datagen");

		final String label;

		Cat(String label) {
			this.label = label;
		}
	}

	/** One mod jar, possibly nested in another. */
	private record Jar(String label, String id, Kind kind, Path root) { }

	/** The header of a remapped mod class, for following a call up to the Minecraft class that declares it. */
	private record Header(String superName, List<String> interfaces, Set<String> fields, Set<String> methods, boolean isInterface) { }

	/** Outcome of resolving a member: found (or not judged), or missing from {@code owner}, which is itself absent when {@code classGone}. */
	private record Res(String owner, boolean classGone) { }

	private static final Res FOUND = new Res(null, false);
	private static final Res NONE = new Res(null, false); // a mod class chain that never reached the game: not a gap

	/** Uses of one missing member, or of a missing class itself. */
	private static final class Use {
		final Map<String, Integer> byMod = new TreeMap<>();
		final Map<String, Set<String>> examples = new TreeMap<>();
		final Map<String, Integer> bySite = new TreeMap<>(); // "mod, tab, user class" to its call sites
		final Map<Cat, Integer> cats = new EnumMap<>(Cat.class);
		final Map<String, Map<Cat, Integer>> catsByMod = new TreeMap<>();
		int total;

		void add(String mod, String user) {
			total++;
			bySite.merge(mod + '\t' + user, 1, Integer::sum);
			byMod.merge(mod, 1, Integer::sum);
			Set<String> ex = examples.computeIfAbsent(mod, k -> new LinkedHashSet<>());
			if (ex.size() < 3) ex.add(user);
		}
	}

	private static final class Gap {
		final String owner;
		boolean classGone;
		String hint;
		final Use classUse = new Use(); // the class named as a type, supertype or constant
		final Map<String, Use> members = new TreeMap<>(); // "method name desc" / "field name:desc"
		final Map<String, String> memberHints = new HashMap<>();

		Gap(String owner) {
			this.owner = owner;
		}

		int total() {
			return classUse.total + members.values().stream().mapToInt(u -> u.total).sum();
		}

		/** The strongest category among all uses: real beats mixin-added beats datagen. */
		Cat category() {
			Cat best = Cat.DATAGEN;
			if (classUse.total > 0) best = strongest(best, classUse);
			for (Use u : members.values()) best = strongest(best, u);
			return best;
		}

		private static Cat strongest(Cat best, Use u) {
			for (Cat c : Cat.values()) if (u.cats.getOrDefault(c, 0) > 0 && c.ordinal() < best.ordinal()) best = c;
			return best;
		}
	}

	private static final class Summary {
		String file;
		Kind kind;
		int classes;
		int failed;
		int sites;
		final Set<String> refs = new TreeSet<>();
		final Map<Cat, Integer> catSites = new EnumMap<>(Cat.class);
		final Map<Cat, Set<String>> catRefs = new EnumMap<>(Cat.class);
	}

	/**
	 * A mapping that gives up part of what the mod asked for: a dropped call, a call sent to a shim method that does nothing, or a
	 * part of the mod that {@code disable.txt} switches off. Listed beside the gaps and never counted as one.
	 */
	private static final class Lossy {
		final String kind;
		final String rule;
		final String detail;
		final Map<String, Integer> byMod = new TreeMap<>();
		int total;

		Lossy(String kind, String rule, String detail) {
			this.kind = kind;
			this.rule = rule;
			this.detail = detail;
		}
	}

	/** Where the stand-ins of Uproot live; a call into one is judged by the compiled body of the method it reaches. */
	private static final String SHIM = "info/mudbourn/mmsuproot/shim/";

	/** The roots of every jar the scan opens, scanned or not: a mod's mixin may implement an interface one of them declares. */
	private static final List<Path> LIBRARIES = new ArrayList<>();

	private final GameClasses game;
	private final ClassLoader loader;
	private final Map<String, Header> headers = new HashMap<>();
	private final Map<String, Boolean> exists = new HashMap<>();
	private final Map<String, Gap> gaps = new TreeMap<>();
	private final Map<String, Summary> summaries = new TreeMap<>();
	private final Map<String, Set<String>> users = new HashMap<>(); // a mod class to the mod classes that mention it
	private Map<String, Set<String>> ownAdded; // what Uproot's own mixins give the game classes; read when first needed
	private final Map<String, Set<String>> added = new HashMap<>(); // a game class to the members ("name+desc", "name:desc") a mod's mixin or injected interface gives it
	private final Map<String, Lossy> lossy = new LinkedHashMap<>();
	private List<String[]> deadHooks = List.of(); // {mixin class, handler, reason} of each line of dead-hooks.txt
	private final List<String[]> typesOnlyCalls = new ArrayList<>(); // {mod, user class, 26.3 owner, "method name desc"} of a call of a member of a typesonly class
	private final Map<String, String> noops = new HashMap<>(); // "shim owner.name desc" -> what its body does, or "" when it does something
	private final Map<String, ClassNode> shimClasses = new HashMap<>();
	private final Map<String, Disabler.Applied> skipRules = new HashMap<>(); // the path and the full id of a skip rule -> the rule
	private final Map<String, String[]> clashes = new TreeMap<>(); // "class kind member" -> {mod, class, kind, member, resolution}: members two of a class share once named
	private Set<String> datagen = Set.of();

	private ScanTool(GameClasses game, ClassLoader loader) {
		this.game = game;
		this.loader = loader;
	}

	public static void main(String[] args) throws IOException {
		Path out = Path.of("build/uproot-scan");
		Path cache = Path.of("run/mms-uproot/cache");
		List<Path> inputs = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "--out" -> out = Path.of(args[++i]);
			case "--cache" -> cache = Path.of(args[++i]);
			default -> inputs.add(Path.of(args[i]));
			}
		}
		if (inputs.isEmpty()) inputs.add(Path.of("run/mods"));
		long start = System.nanoTime();
		Files.createDirectories(out);

		List<Path> classPath = Stream.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)).map(Path::of).toList();
		ClassLoader loader = ScanTool.class.getClassLoader();
		GameClasses game = new GameClasses(loader, classPath);
		if (!game.indexed()) throw new IllegalStateException("no Minecraft jar on the class path (net/minecraft/world/level/Level.class not found)");

		Report report = new Report(out.resolve("uproot-report.txt"));
		Disabler.init(report);
		List<FileSystem> open = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		try {
			List<Jar> jars = discover(inputs, out.resolve("nested"), open, notes);
			ScanTool tool = new ScanTool(game, loader);
			tool.run(jars, game, report, cache, notes);
			tool.write(out, notes, game.size(), (System.nanoTime() - start) / 1_000_000_000.0);
		} finally {
			for (FileSystem fs : open) fs.close();
		}
	}

	// ---- finding the target jars

	private static List<Jar> discover(List<Path> inputs, Path nestedDir, List<FileSystem> open, List<String> notes) throws IOException {
		List<Path> files = new ArrayList<>();
		for (Path in : inputs) {
			if (Files.isDirectory(in)) {
				try (DirectoryStream<Path> jars = Files.newDirectoryStream(in, "*.jar")) {
					jars.forEach(files::add);
				}
			} else if (Files.isRegularFile(in)) {
				files.add(in);
			} else {
				notes.add("not found: " + in);
			}
		}
		files.sort(Comparator.comparing(Path::toString));
		List<Jar> found = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (Path f : files) {
			try {
				open(f, f.getFileName().toString(), false, nestedDir, open, found, ids, notes);
			} catch (IOException | RuntimeException e) {
				notes.add(f.getFileName() + ": could not be read: " + e);
			}
		}
		return found;
	}

	@SuppressWarnings("unchecked")
	private static void open(Path file, String label, boolean inSkipped, Path nestedDir, List<FileSystem> open, List<Jar> found, Set<String> ids,
			List<String> notes) throws IOException {
		FileSystem fs = FileSystems.newFileSystem(file);
		open.add(fs);
		Path root = fs.getPath("/");
		LIBRARIES.add(root);
		Path fmjPath = root.resolve("fabric.mod.json");
		if (!Files.isRegularFile(fmjPath)) return;
		Map<String, Object> fmj = Json.parseObject(Files.readString(fmjPath, StandardCharsets.UTF_8));
		if (!(fmj.get("id") instanceof String id)) return;
		boolean skip = inSkipped || ModSource.skipId(id);

		if (fmj.get("jars") instanceof List<?> nested) {
			for (Object n : nested) {
				if (!(n instanceof Map<?, ?> m) || m.get("file") == null) continue;
				Path inner = root.resolve(String.valueOf(m.get("file")));
				if (!Files.isRegularFile(inner)) continue;
				Files.createDirectories(nestedDir);
				Path copy = nestedDir.resolve(Integer.toHexString((label + inner).hashCode()) + "-" + inner.getFileName());
				Files.copy(inner, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				try {
					open(copy, label + "!" + inner.getFileName(), skip, nestedDir, open, found, ids, notes);
				} catch (IOException | RuntimeException e) {
					notes.add(label + "!" + inner.getFileName() + ": could not be read: " + e);
				}
			}
		}
		if (skip) return;
		if (!ids.add(id)) {
			notes.add(label + ": mod id " + id + " already scanned from another jar, skipped");
			return;
		}
		found.add(new Jar(label, id, classify(root, fmj), root));
	}

	/** The boot hook's tests, in its order: intermediary names, then a 26.1 or 26.2 build. */
	private static Kind classify(Path root, Map<String, Object> fmj) throws IOException {
		if (fmj.get("accessWidener") instanceof String aw && Files.isRegularFile(root.resolve(aw))) {
			if ("intermediary".equals(AccessWideners.namespace(Files.readString(root.resolve(aw))))) return Kind.LEGACY;
		}
		List<Path> classes = classFiles(root);
		for (Path p : classes) {
			if (ModSource.mentionsIntermediary(Files.readAllBytes(p))) return Kind.LEGACY;
		}
		Boolean byRange = ModSource.olderByRange(fmj);
		if (byRange != null) return byRange ? Kind.MODERN : Kind.NONE;
		for (Path p : classes) {
			if (ModSource.namesPre263(Files.readAllBytes(p))) return Kind.MODERN;
		}
		return Kind.NONE;
	}

	private static List<Path> classFiles(Path root) throws IOException {
		try (Stream<Path> walk = Files.walk(root)) {
			return walk.filter(p -> p.toString().endsWith(".class") && !p.toString().startsWith("/META-INF/")).toList();
		}
	}

	private static String internal(Path root, Path p) {
		String rel = root.relativize(p).toString().replace('\\', '/');
		return rel.substring(0, rel.length() - 6);
	}

	// ---- remapping

	private record Remapped(String mod, String name, byte[] bytes) { }

	private void run(List<Jar> jars, GameClasses game, Report report, Path cache, List<String> notes) throws IOException {
		List<Jar> targets = jars.stream().filter(j -> j.kind() != Kind.NONE).toList();
		for (Jar j : jars) {
			if (j.kind() == Kind.NONE) notes.add(j.id() + ": not a target (no intermediary names, not built for 26.1 or 26.2), not scanned");
		}
		boolean legacy = targets.stream().anyMatch(j -> j.kind() == Kind.LEGACY);
		NameTable table = legacy ? MappingFetcher.obtain(cache) : new NameTable();
		if (table == null) {
			notes.add("the 1.21.11 name table could not be built, so the 1.21.11 mods were not scanned");
			table = new NameTable();
			targets = targets.stream().filter(j -> j.kind() != Kind.LEGACY).toList();
		}
		Renames renames = targets.stream().anyMatch(j -> j.kind() == Kind.LEGACY) ? Renames.load() : null;
		Renames renames26 = targets.stream().anyMatch(j -> j.kind() == Kind.MODERN) ? Renames.load("/uproot/renames-26x.txt") : null;
		for (Renames r : new Renames[] {renames, renames26}) {
			if (r == null) continue;
			for (String rule : r.callDrops()) lossy("calldrop", rule, "the call is removed and null, 0 or false stands in for its result");
			for (String helper : r.helperOwners()) {
				if (!helper.startsWith(SHIM)) continue;
				ClassNode cn = shimClasses.computeIfAbsent(helper, this::readShim);
				if (cn == null) continue;
				for (MethodNode m : cn.methods) {
					String approximate = approximation(m);
					if (approximate != null) lossy("shim-approx", helper.replace('/', '.') + "." + m.name + m.desc, approximate);
				}
			}
		}
		for (Disabler.Applied a : Disabler.applied()) {
			lossy("disable-" + a.kind(), a.describe(), a.reason());
			if (a.kind().equals("skip")) {
				skipRules.put(a.name(), a);
				skipRules.put(a.name().substring(a.name().indexOf(':') + 1), a);
			}
		}

		deadHooks = readDeadHooks();
		for (String[] h : deadHooks) lossy("dead-hook", h[0].replace('/', '.') + " " + h[1], h[2]);

		// Index every target before any name is mapped, so a class of one target is never renamed for another.
		ModClasses modClasses = new ModClasses();
		Map<String, List<Path>> files = new HashMap<>();
		Map<String, String> classOwner = new HashMap<>(); // a mod class to the id of the mod that has it
		for (Jar j : targets) {
			List<Path> classes = classFiles(j.root());
			files.put(j.id(), classes);
			for (Path p : classes) {
				modClasses.put(internal(j.root(), p), p);
				classOwner.put(internal(j.root(), p).replace('/', '.'), j.id());
			}
		}

		List<Remapped> remapped = new ArrayList<>();
		Map<String, Namer> namers = new HashMap<>();
		for (Jar j : targets) {
			Summary s = new Summary();
			s.file = j.label();
			s.kind = j.kind();
			summaries.put(j.id(), s);
			Namer namer = new Namer(table, j.kind() == Kind.LEGACY ? renames : renames26, game, modClasses, report.mod(j.id()));
			namers.put(j.id(), namer);
			Set<String> own = new HashSet<>();
			for (Path p : files.get(j.id())) own.add(internal(j.root(), p));
			for (Disabler.Applied a : Disabler.applied()) if (a.kind().equals("mixin") && own.contains(a.owner()) && (!a.legacyOnly() || j.kind() == Kind.LEGACY)) lossyUse(lossy("disable-mixin", a.describe(), a.reason()), j.id(), 1);
			for (String[] h : deadHooks) if (own.contains(h[0])) lossyUse(lossy("dead-hook", h[0].replace('/', '.') + " " + h[1], h[2]), j.id(), 1);
			for (Path p : files.get(j.id())) {
				String name = internal(j.root(), p);
				if (Disabler.mixinReason(name, j.kind() == Kind.LEGACY) != null) continue; // left out of its mixin config, so it never loads and nothing in it runs; the lossy list says so
				try {
					byte[] mapped = ClassRemap.remap(Files.readAllBytes(p), namer);
					for (String call : namer.takeTypesOnlyCalls().keySet()) {
						String[] part = call.split("\t", 2);
						typesOnlyCalls.add(new String[] {j.id(), name, part[0], "method " + part[1]});
					}
					Map<Integer, Integer> before = disableHits();
					byte[] disabled = Disabler.transform(name, mapped, j.kind() == Kind.LEGACY);
					for (Disabler.Applied a : Disabler.applied()) {
						int n = a.hits() - before.getOrDefault(a.line(), 0);
						if (n > 0 && !a.kind().equals("mixin")) lossyUse(lossy("disable-" + a.kind(), a.describe(), a.reason()), j.id(), n);
					}
					dump(j.id(), name, disabled);
					remapped.add(new Remapped(j.id(), name, disabled));
					s.classes++;
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				} catch (RuntimeException | LinkageError e) {
					s.failed++;
					notes.add(j.id() + ": " + name + " could not be remapped: " + e);
				}
			}
		}

		namers.forEach((mod, namer) -> namer.drops().forEach((rule, n) -> lossyUse(lossy("calldrop", rule, ""), mod, n)));
		// a clash is found by whichever namer asked about the class first; it belongs to the mod that has the class
		namers.values().forEach(namer -> namer.clashes().forEach(c -> clashes.putIfAbsent(c.modClass() + ' ' + c.kind() + ' ' + c.member(),
				c.unresolved() ? new String[] {classOwner.getOrDefault(c.modClass(), "?"), c.modClass(), c.kind(), c.member(), c.resolution(), "unresolved"}
					: new String[] {classOwner.getOrDefault(c.modClass(), "?"), c.modClass(), c.kind(), c.member(), c.resolution()})));
		// a clash the remapper could not resolve leaves a class the JVM refuses to load: a real gap, listed under the class itself
		for (String[] c : clashes.values()) {
			if (c.length < 6 || !summaries.containsKey(c[0])) continue;
			String owner = c[1].replace('.', '/');
			Gap g = gap(owner, false);
			String key = c[2] + " " + c[3];
			record(c[0], owner, g, g.members.computeIfAbsent(key, k -> new Use()), owner + "." + c[3]);
			g.memberHints.put(key, "member clash left unresolved: " + c[4]);
		}

		for (Remapped r : remapped) headers.put(r.name(), header(r.bytes()));
		for (Remapped r : remapped) {
			try {
				new ClassReader(r.bytes()).accept(new Walker(r.mod(), r.name()), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			} catch (RuntimeException e) {
				notes.add(r.mod() + ": " + r.name() + " could not be walked: " + e);
			}
		}
		for (Gap g : gaps.values()) hints(g);
		for (String[] c : typesOnlyCalls) {
			Gap g = gap(c[2], false);
			record(c[0], c[1], g, g.members.computeIfAbsent(c[3], k -> new Use()), c[2] + "." + c[3]);
			g.memberHints.put(c[3], "its class rule in renames.txt is typesonly: a type stands in for the old class, its members do not");
		}

		Set<String> entrypoints = new HashSet<>();
		for (Jar j : targets) readMetadata(j, namers.get(j.id()), entrypoints, notes);
		for (Remapped r : remapped) {
			try {
				collectMixin(r.bytes(), namers.get(r.mod()));
			} catch (RuntimeException e) {
				notes.add(r.mod() + ": " + r.name() + " could not be read for mixin members: " + e);
			}
		}
		datagen = datagenClasses(remapped, entrypoints);
		categorize();
	}

	// ---- lossy mappings

	/**
	 * The mixin injectors that match nothing in 26.3 and are accepted so ({@code uproot/dead-hooks.txt}, lines {@code CLASS HANDLER | reason}).
	 * A reason that starts with "real break" is tagged, so it stands apart from the harmless and the covered ones.
	 */
	private static List<String[]> readDeadHooks() {
		List<String[]> out = new ArrayList<>();
		try (java.io.InputStream in = ScanTool.class.getResourceAsStream("/uproot/dead-hooks.txt")) {
			if (in == null) return out;
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String t = line.strip();
				int bar = t.indexOf('|');
				if (t.isEmpty() || t.startsWith("#") || bar < 0) continue;
				String[] id = t.substring(0, bar).strip().split("\\s+");
				if (id.length != 2) continue;
				String reason = t.substring(bar + 1).strip();
				out.add(new String[] {id[0], id[1], reason.startsWith("real break") ? "[REAL BREAK] " + reason : reason});
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out;
	}

	private Lossy lossy(String kind, String rule, String detail) {
		return lossy.computeIfAbsent(kind + " " + rule, k -> new Lossy(kind, rule, detail));
	}

	private static void lossyUse(Lossy l, String mod, int n) {
		if (n <= 0) return;
		l.byMod.merge(mod, n, Integer::sum);
		l.total += n;
	}

	private static Map<Integer, Integer> disableHits() {
		Map<Integer, Integer> out = new HashMap<>();
		for (Disabler.Applied a : Disabler.applied()) out.put(a.line(), a.hits());
		return out;
	}

	/** What the compiled body of a shim method does when that is nothing, or the empty string. Read from the shim class, not listed by hand. */
	private String noop(String owner, String name, String desc) {
		return noops.computeIfAbsent(owner + "." + name + desc, k -> {
			for (String c = owner; c != null && c.startsWith(SHIM); ) {
				ClassNode cn = shimClasses.computeIfAbsent(c, this::readShim);
				if (cn == null) return "";
				for (MethodNode m : cn.methods) {
					if (!m.name.equals(name) || !m.desc.equals(desc)) continue;
					String approximate = approximation(m);
					return approximate != null ? APPROX + approximate : bodyKind(cn.name, m);
				}
				c = cn.superName;
			}
			return "";
		});
	}

	private ClassNode readShim(String name) {
		try (java.io.InputStream in = loader.getResourceAsStream(name + ".class")) {
			if (in == null) return null;
			ClassNode cn = new ClassNode();
			new ClassReader(in).accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return cn;
		} catch (IOException e) {
			return null;
		}
	}

	private static final String APPROXIMATE = "Linfo/mudbourn/mmsuproot/shim/Approximate;";

	/** What an {@code @Approximate} method gives up, or null. */
	private static String approximation(MethodNode m) {
		if (m.invisibleAnnotations == null) return null;
		for (org.objectweb.asm.tree.AnnotationNode a : m.invisibleAnnotations) {
			if (a.desc.equals(APPROXIMATE) && a.values != null && a.values.size() >= 2) return String.valueOf(a.values.get(1));
		}
		return null;
	}

	/** An empty body, or one that only returns a constant, a no-argument factory of the JDK, or one of its own arguments. */
	private static String bodyKind(String shim, MethodNode m) {
		if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0 || m.name.equals("<init>") || m.name.equals("<clinit>")) return "";
		List<AbstractInsnNode> code = new ArrayList<>();
		for (AbstractInsnNode i : m.instructions) if (i.getOpcode() >= 0) code.add(i);
		if (code.isEmpty()) return "";
		int last = code.get(code.size() - 1).getOpcode();
		if (code.size() == 1 && last == Opcodes.RETURN) return "empty body";
		if (last < Opcodes.IRETURN || last > Opcodes.ARETURN) return "";
		if (code.size() == 2) {
			AbstractInsnNode first = code.get(0);
			int op = first.getOpcode();
			if (op == Opcodes.ACONST_NULL) return "returns null";
			if ((op >= Opcodes.ICONST_M1 && op <= Opcodes.DCONST_1) || op == Opcodes.BIPUSH || op == Opcodes.SIPUSH || op == Opcodes.LDC) return "returns a constant";
			if (op == Opcodes.GETSTATIC) {
				org.objectweb.asm.tree.FieldInsnNode field = (org.objectweb.asm.tree.FieldInsnNode) first;
				return field.owner.equals(shim) ? "" : "returns the constant " + field.name; // the shim's own singleton is a factory, not a stand-in
			}
			if (op >= Opcodes.ILOAD && op <= Opcodes.ALOAD) {
				int slot = ((org.objectweb.asm.tree.VarInsnNode) first).var;
				return slot == 0 && (m.access & Opcodes.ACC_STATIC) == 0 ? "returns this" : "returns an argument unchanged";
			}
		}
		if (code.size() == 2 && code.get(0) instanceof org.objectweb.asm.tree.MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
				&& call.owner.startsWith("java/") && Type.getArgumentTypes(call.desc).length == 0) {
			return "returns " + call.owner.substring(call.owner.lastIndexOf('/') + 1) + "." + call.name + "()";
		}
		return "";
	}

	/** Prefix of a {@link #noop} result that comes from an {@code @Approximate} marker, not from the body. */
	private static final String APPROX = "approximate: ";

	/** Counts a call into a shim method whose body does nothing, or that is marked {@code @Approximate}. */
	private void shimCall(String mod, String owner, String name, String desc) {
		String kind = noop(owner, name, desc);
		if (kind.isEmpty()) return;
		boolean marked = kind.startsWith(APPROX);
		lossyUse(lossy(marked ? "shim-approx" : "shim-noop", owner.replace('/', '.') + "." + name + desc, marked ? kind.substring(APPROX.length()) : kind), mod, 1);
	}

	// ---- categories

	/** The datagen entrypoints of a mod and the interfaces its fabric.mod.json injects into Minecraft classes. */
	@SuppressWarnings("unchecked")
	private void readMetadata(Jar j, Namer namer, Set<String> entrypoints, List<String> notes) {
		try {
			Map<String, Object> fmj = Json.parseObject(Files.readString(j.root().resolve("fabric.mod.json"), StandardCharsets.UTF_8));
			if (fmj.get("entrypoints") instanceof Map<?, ?> eps && eps.get("fabric-datagen") instanceof List<?> list) {
				for (Object o : list) {
					Object value = o instanceof Map<?, ?> m ? m.get("value") : o;
					if (value instanceof String v) entrypoints.add(v.split("::")[0].replace('.', '/'));
				}
			}
			if (fmj.get("custom") instanceof Map<?, ?> custom && custom.get("loom:injected_interfaces") instanceof Map<?, ?> injected) {
				for (Map.Entry<?, ?> e : ((Map<String, Object>) injected).entrySet()) {
					if (!(e.getValue() instanceof List<?> ifaces)) continue;
					String target = namer.cls(String.valueOf(e.getKey()).replace('.', '/'));
					for (Object i : ifaces) {
						String name = String.valueOf(i);
						if (name.indexOf('<') > 0) name = name.substring(0, name.indexOf('<'));
						interfaceMembers(namer.cls(name.replace('.', '/')), added.computeIfAbsent(target, k -> new HashSet<>()), new HashSet<>());
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			notes.add(j.id() + ": fabric.mod.json could not be read for datagen entrypoints and injected interfaces: " + e);
		}
	}

	/** Every method an interface of a mod declares or inherits from the mod's own interfaces, as {@code name+desc}. */
	private void interfaceMembers(String itf, Set<String> into, Set<String> seen) {
		Header h = headers.get(itf);
		if (h == null) h = library(itf);
		if (h == null || !seen.add(itf)) return;
		into.addAll(h.methods());
		for (String up : h.interfaces()) interfaceMembers(up, into, seen);
	}

	/** The header of a class in a skipped library jar, or null. */
	private static Header library(String name) {
		for (Path root : LIBRARIES) {
			Path p = root.resolve(name + ".class");
			if (!Files.isRegularFile(p)) continue;
			try {
				return header(Files.readAllBytes(p));
			} catch (IOException | RuntimeException e) {
				return null;
			}
		}
		return null;
	}

	/** With {@code -Duproot.scan.dump=DIR} the classes of the mods named in {@code -Duproot.scan.dump.mods} (all when unset) are written as remapped, for javap. */
	private static void dump(String mod, String name, byte[] bytes) {
		String dir = System.getProperty("uproot.scan.dump");
		if (dir == null) return;
		String only = System.getProperty("uproot.scan.dump.mods");
		if (only != null && !List.of(only.split(",")).contains(mod)) return;
		try {
			Path file = Path.of(dir, mod, name + ".class");
			Files.createDirectories(file.getParent());
			Files.write(file, bytes);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** What a mixin class gives its targets: its own members that are not {@code @Shadow} or {@code @Overwrite}, and the methods of the interfaces it implements. */
	private void collectMixin(byte[] bytes, Namer namer) {
		collectMixin(bytes, namer::cls, added);
	}

	private void collectMixin(byte[] bytes, java.util.function.UnaryOperator<String> cls, Map<String, Set<String>> into) {
		ClassReader reader = new ClassReader(bytes);
		List<String> targets = new ArrayList<>();
		Set<String> members = new HashSet<>();
		boolean[] mixin = {false};
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
				if (!desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
				mixin[0] = true;
				return new AnnotationVisitor(Opcodes.ASM9) {
					@Override
					public AnnotationVisitor visitArray(String key) {
						return new AnnotationVisitor(Opcodes.ASM9) {
							@Override
							public void visit(String k, Object value) {
								if (value instanceof Type t && t.getSort() == Type.OBJECT) targets.add(t.getInternalName());
								else if (value instanceof String str && "targets".equals(key)) targets.add(str.replace('.', '/'));
							}
						};
					}
				};
			}

			@Override
			public FieldVisitor visitField(int access, String n, String d, String sig, Object value) {
				boolean[] skip = {false};
				return new FieldVisitor(Opcodes.ASM9) {
					@Override
					public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
						if (desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) skip[0] = true;
						return null;
					}

					@Override
					public void visitEnd() {
						if (!skip[0]) members.add(n + ":" + d);
					}
				};
			}

			@Override
			public MethodVisitor visitMethod(int access, String n, String d, String sig, String[] ex) {
				if (n.equals("<init>") || n.equals("<clinit>") || n.startsWith("lambda$") || (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) return null;
				boolean[] skip = {false};
				return new MethodVisitor(Opcodes.ASM9) {
					@Override
					public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
						if (desc.equals("Lorg/spongepowered/asm/mixin/Shadow;") || desc.equals("Lorg/spongepowered/asm/mixin/Overwrite;")) skip[0] = true;
						return null;
					}

					@Override
					public void visitEnd() {
						if (!skip[0]) members.add(n + d);
					}
				};
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		if (!mixin[0]) return;
		for (String itf : reader.getInterfaces()) interfaceMembers(itf, members, new HashSet<>());
		for (String t : targets) into.computeIfAbsent(cls.apply(t), k -> new HashSet<>()).addAll(members);
	}

	/**
	 * Datagen classes: a datagen entrypoint, a class that extends or implements a Fabric (or vanilla) datagen type, and every class
	 * only datagen classes mention.
	 */
	private Set<String> datagenClasses(List<Remapped> remapped, Set<String> entrypoints) {
		Set<String> out = new HashSet<>();
		for (Remapped r : remapped) if (entrypoints.contains(r.name()) || datagenType(r.name(), new HashSet<>())) out.add(r.name());
		boolean again = true;
		while (again) {
			again = false;
			for (Remapped r : remapped) {
				if (out.contains(r.name())) continue;
				Set<String> from = new HashSet<>(users.getOrDefault(r.name(), Set.of()));
				from.remove(r.name());
				if (!from.isEmpty() && out.containsAll(from)) {
					out.add(r.name());
					again = true;
				}
			}
		}
		return out;
	}

	private boolean datagenType(String cls, Set<String> seen) {
		Header h = headers.get(cls);
		if (h == null || !seen.add(cls)) return false;
		List<String> up = new ArrayList<>(h.interfaces());
		if (h.superName() != null) up.add(h.superName());
		for (String s : up) {
			if (s.startsWith("net/fabricmc/fabric/api/datagen/") || s.startsWith("net/fabricmc/fabric/impl/datagen/") || s.startsWith("net/minecraft/data/")) return true;
			if (datagenType(s, seen)) return true;
		}
		return false;
	}

	/** Whether a mod's mixin (or an interface injected by one) gives this member to the game class or something above it. */
	private boolean mixinAdded(String owner, String key) {
		return addedTo(added, owner, key.substring(key.indexOf(' ') + 1));
	}

	/** What Uproot's own mixins give the game classes (the ones listed in {@code mms_uproot.mixins.json}): these members exist at run time. */
	private Map<String, Set<String>> ownMixins() {
		if (ownAdded != null) return ownAdded;
		Map<String, Set<String>> out = new HashMap<>();
		try (java.io.InputStream in = loader.getResourceAsStream("mms_uproot.mixins.json")) {
			if (in != null) {
				Map<String, Object> config = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				String pkg = String.valueOf(config.get("package")).replace('.', '/');
				for (String list : List.of("mixins", "client")) {
					if (!(config.get(list) instanceof List<?> names)) continue;
					for (Object name : names) {
						try (java.io.InputStream cls = loader.getResourceAsStream(pkg + "/" + String.valueOf(name).replace('.', '/') + ".class")) {
							if (cls != null) collectMixin(cls.readAllBytes(), UnaryOperator.identity(), out);
						}
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("could not read Uproot's own mixins: " + e);
		}
		return ownAdded = out;
	}

	private boolean addedTo(Map<String, Set<String>> added, String owner, String member) {
		Set<String> seen = new HashSet<>();
		java.util.ArrayDeque<String> todo = new java.util.ArrayDeque<>(List.of(owner));
		while (!todo.isEmpty()) {
			String c = todo.pop();
			if (!seen.add(c)) continue;
			if (added.getOrDefault(c, Set.of()).contains(member)) return true;
			game.info(c).ifPresent(i -> {
				if (i.superName() != null) todo.add(i.superName());
				todo.addAll(i.interfaces());
			});
		}
		return false;
	}

	private void categorize() {
		for (Gap g : gaps.values()) {
			if (g.classUse.total > 0) categorize(g, g.classUse, null, g.owner);
			g.members.forEach((key, use) -> categorize(g, use, key, g.owner + "." + key));
		}
	}

	private void categorize(Gap g, Use use, String key, String ref) {
		use.bySite.forEach((site, n) -> {
			String mod = site.substring(0, site.indexOf('\t'));
			String user = site.substring(site.indexOf('\t') + 1);
			Cat c = datagen.contains(user) ? Cat.DATAGEN : key != null && mixinAdded(g.owner, key) ? Cat.MIXIN_ADDED : Cat.REAL;
			use.cats.merge(c, n, Integer::sum);
			use.catsByMod.computeIfAbsent(mod, k -> new EnumMap<>(Cat.class)).merge(c, n, Integer::sum);
			Summary s = summaries.get(mod);
			s.catSites.merge(c, n, Integer::sum);
			s.catRefs.computeIfAbsent(c, k -> new TreeSet<>()).add(ref);
		});
	}

	private static Header header(byte[] bytes) {
		ClassReader reader = new ClassReader(bytes);
		Set<String> fields = new HashSet<>();
		Set<String> methods = new HashSet<>();
		reader.accept(new ClassVisitor(Opcodes.ASM9) {
			@Override
			public FieldVisitor visitField(int access, String n, String desc, String sig, Object value) {
				fields.add(n + ":" + desc);
				return null;
			}

			@Override
			public MethodVisitor visitMethod(int access, String n, String desc, String sig, String[] ex) {
				methods.add(n + desc);
				return null;
			}
		}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		return new Header(reader.getSuperName(), List.of(reader.getInterfaces()), fields, methods, (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0);
	}

	// ---- resolving

	private static boolean game(String owner) {
		for (String p : GAME) if (owner.startsWith(p)) return true;
		return false;
	}

	private boolean exists(String cls) {
		return exists.computeIfAbsent(cls, c -> loader.getResource(c + ".class") != null);
	}

	private boolean has(String owner, String name, String desc, boolean field) {
		if (field ? game.findField(owner, name, desc) : game.findMethod(owner, name, desc) || game.findMethod("java/lang/Object", name, desc)) return true;
		return addedTo(ownMixins(), owner, field ? name + ":" + desc : name + desc);
	}

	/**
	 * A static method is found on the named class or its superclasses, and on an interface only when that interface is the named
	 * owner: static interface methods are not inherited, so a call that resolves only through an interface would fail at run time.
	 */
	private boolean hasStatic(String owner, String name, String desc) {
		for (String c = owner; c != null; ) {
			GameClasses.Info info = game.info(c).orElse(null);
			if (info == null) return false;
			if (info.hasMethod(name, desc)) return true;
			if (info.isInterface()) return false;
			c = info.superName();
		}
		return false;
	}

	private Res resolve(String owner, String name, String desc, boolean field, boolean statik, Set<String> seen) {
		if (owner.charAt(0) == '[') return FOUND;
		Header h = headers.get(owner);
		if (h != null) {
			if ((field ? h.fields() : h.methods()).contains(field ? name + ":" + desc : name + desc)) return FOUND;
			if (!seen.add(owner)) return NONE;
			Res gap = NONE;
			List<String> up = new ArrayList<>();
			if (h.superName() != null) up.add(h.superName());
			if (!statik) up.addAll(h.interfaces());
			for (String s : up) {
				Res r = resolve(s, name, desc, field, statik, seen);
				if (r == FOUND) return FOUND;
				if (r != NONE && gap == NONE) gap = r;
			}
			return gap;
		}
		if (game(owner)) {
			if (!exists(owner)) return new Res(owner, true);
			return (statik ? hasStatic(owner, name, desc) : has(owner, name, desc, field)) ? FOUND : new Res(owner, false);
		}
		if (exists(owner)) return has(owner, name, desc, field) ? FOUND : NONE;
		return FOUND; // a class we cannot see: not judged
	}

	/** Same descriptor under another name, or the same name under other descriptors, among what 26.3 declares. */
	private void hints(Gap g) {
		if (g.classGone) {
			String simple = g.owner.substring(g.owner.lastIndexOf('/') + 1);
			String other = simple.indexOf('$') < 0 ? game.uniqueBySimpleName(simple) : null;
			if (other != null) g.hint = "a class named " + simple + " exists at " + other;
			return;
		}
		for (String key : g.members.keySet()) {
			boolean field = key.startsWith("field ");
			String rest = key.substring(field ? 6 : 7);
			int split = field ? rest.indexOf(':') : rest.indexOf('(');
			String name = rest.substring(0, split);
			String desc = field ? rest.substring(split + 1) : rest.substring(split);
			List<String> sameShape = new ArrayList<>();
			List<String> sameName = new ArrayList<>();
			for (String m : declared(g.owner, field)) {
				int s = field ? m.indexOf(':') : m.indexOf('(');
				String n = m.substring(0, s);
				String d = field ? m.substring(s + 1) : m.substring(s);
				if (n.equals(name)) sameName.add(d);
				else if (d.equals(desc) && !n.startsWith("lambda$") && !n.startsWith("access$") && !n.equals("<init>") && !n.equals("<clinit>")) sameShape.add(n);
			}
			sameShape = sameShape.stream().distinct().sorted().toList();
			sameName = sameName.stream().distinct().sorted().toList();
			StringBuilder hint = new StringBuilder();
			if (sameShape.size() == 1) hint.append("likely rename: ").append(sameShape.get(0));
			if (!sameName.isEmpty()) {
				if (hint.length() > 0) hint.append("; ");
				hint.append("26.3 has ").append(name).append(" as ").append(String.join(", ", sameName.stream().limit(4).toList()));
			}
			if (hint.length() > 0) g.memberHints.put(key, hint.toString());
		}
	}

	/** Members ({@code name+desc} or {@code name:desc}) of a game class and everything above it. */
	private Set<String> declared(String owner, boolean field) {
		Set<String> out = new HashSet<>();
		Set<String> seen = new HashSet<>();
		java.util.ArrayDeque<String> todo = new java.util.ArrayDeque<>(List.of(owner));
		while (!todo.isEmpty()) {
			String c = todo.pop();
			if (!seen.add(c)) continue;
			game.info(c).ifPresent(i -> {
				out.addAll(field ? i.fields() : i.methods());
				if (i.superName() != null) todo.add(i.superName());
				todo.addAll(i.interfaces());
			});
		}
		return out;
	}

	// ---- walking the remapped bytecode

	private Gap gap(String owner, boolean classGone) {
		Gap g = gaps.computeIfAbsent(owner, Gap::new);
		g.classGone |= classGone;
		return g;
	}

	private void record(String mod, String user, Gap g, Use use, String ref) {
		use.add(mod, user);
		Summary s = summaries.get(mod);
		s.sites++;
		s.refs.add(ref);
	}

	private final class Walker extends ClassVisitor {
		private final String mod;
		private final String user;

		Walker(String mod, String user) {
			super(Opcodes.ASM9);
			this.mod = mod;
			this.user = user;
		}

		/** A class (internal name, or an array descriptor) used as a type; counts only when it is a game class 26.3 lacks. */
		void type(String name) {
			if (name == null) return;
			if (name.charAt(0) == '[') {
				typeDesc(name);
				return;
			}
			if (headers.containsKey(name)) {
				users.computeIfAbsent(name, k -> new HashSet<>()).add(user);
				return;
			}
			if (!game(name) || exists(name)) return;
			Gap g = gap(name, true);
			record(mod, user, g, g.classUse, name);
		}

		/** The class a field descriptor names, if any. */
		void typeDesc(String d) {
			Type t = Type.getType(d);
			if (t.getSort() == Type.ARRAY) t = t.getElementType();
			if (t.getSort() == Type.OBJECT) type(t.getInternalName());
		}

		void desc(String desc) {
			if (desc == null) return;
			if (desc.charAt(0) == '(') {
				for (Type t : Type.getArgumentTypes(desc)) typeDesc(t.getDescriptor());
				typeDesc(Type.getReturnType(desc).getDescriptor());
			} else {
				typeDesc(desc);
			}
		}

		void signature(String sig, boolean field) {
			if (sig == null) return;
			SignatureVisitor v = new SignatureVisitor(Opcodes.ASM9) {
				private String current;

				@Override
				public void visitClassType(String name) {
					current = name;
					type(name);
				}

				@Override
				public void visitInnerClassType(String name) {
					current = current + "$" + name;
					type(current);
				}
			};
			if (field) new SignatureReader(sig).acceptType(v);
			else new SignatureReader(sig).accept(v);
		}

		void member(String owner, String name, String desc, boolean field, boolean statik) {
			desc(desc);
			if (!field && owner.startsWith(SHIM)) shimCall(mod, owner, name, desc);
			if (headers.containsKey(owner)) users.computeIfAbsent(owner, k -> new HashSet<>()).add(user);
			Res r = resolve(owner, name, desc, field, statik, new HashSet<>());
			if (r == FOUND || r == NONE) return;
			Gap g = gap(r.owner(), r.classGone());
			String key = (field ? "field " + name + ":" : "method " + name) + desc;
			record(mod, user, g, g.members.computeIfAbsent(key, k -> new Use()), r.owner() + "." + key);
		}

		void handle(Handle h) {
			member(h.getOwner(), h.getName(), h.getDesc(), h.getTag() <= Opcodes.H_PUTSTATIC, h.getTag() == Opcodes.H_INVOKESTATIC);
		}

		void constant(Object c) {
			if (c instanceof String str && !skipRules.isEmpty()) {
				Disabler.Applied a = skipRules.get(str);
				Summary owner = summaries.get(mod);
				if (a != null && (!a.legacyOnly() || owner != null && owner.kind == Kind.LEGACY)) lossyUse(lossy("disable-skip", a.describe(), a.reason()), mod, 1);
			}
			if (c instanceof Type t) {
				if (t.getSort() == Type.METHOD) desc(t.getDescriptor());
				else typeDesc(t.getDescriptor());
			} else if (c instanceof Handle h) {
				handle(h);
			} else if (c instanceof ConstantDynamic cd) {
				desc(cd.getDescriptor());
				handle(cd.getBootstrapMethod());
				for (int i = 0; i < cd.getBootstrapMethodArgumentCount(); i++) constant(cd.getBootstrapMethodArgument(i));
			}
		}

		AnnotationVisitor annotation(String desc) {
			typeDesc(desc);
			return annotationValues();
		}

		AnnotationVisitor annotationValues() {
			return new AnnotationVisitor(Opcodes.ASM9) {
				@Override
				public void visit(String n, Object value) {
					constant(value);
				}

				@Override
				public void visitEnum(String n, String desc, String value) {
					typeDesc(desc);
				}

				@Override
				public AnnotationVisitor visitAnnotation(String n, String desc) {
					return annotation(desc);
				}

				@Override
				public AnnotationVisitor visitArray(String n) {
					return annotationValues();
				}
			};
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			type(superName);
			if (interfaces != null) for (String i : interfaces) type(i);
			signature(signature, false);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			return annotation(desc);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
			desc(desc);
			signature(signature, true);
			return new FieldVisitor(Opcodes.ASM9) {
				@Override
				public AnnotationVisitor visitAnnotation(String d, boolean visible) {
					return annotation(d);
				}
			};
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			desc(desc);
			signature(signature, false);
			if (exceptions != null) for (String e : exceptions) type(e);
			return new MethodVisitor(Opcodes.ASM9) {
				@Override
				public AnnotationVisitor visitAnnotation(String d, boolean visible) {
					return annotation(d);
				}

				@Override
				public AnnotationVisitor visitParameterAnnotation(int parameter, String d, boolean visible) {
					return annotation(d);
				}

				@Override
				public void visitTypeInsn(int opcode, String t) {
					type(t);
				}

				@Override
				public void visitFieldInsn(int opcode, String owner, String n, String d) {
					member(owner, n, d, true, false);
				}

				@Override
				public void visitMethodInsn(int opcode, String owner, String n, String d, boolean itf) {
					member(owner, n, d, false, opcode == Opcodes.INVOKESTATIC);
				}

				@Override
				public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... args) {
					desc(d);
					handle(bsm);
					for (Object a : args) constant(a);
				}

				@Override
				public void visitLdcInsn(Object value) {
					constant(value);
				}

				@Override
				public void visitMultiANewArrayInsn(String d, int dims) {
					typeDesc(d);
				}

				@Override
				public void visitTryCatchBlock(org.objectweb.asm.Label s, org.objectweb.asm.Label e, org.objectweb.asm.Label h, String t) {
					type(t);
				}
			};
		}
	}

	// ---- output

	private void write(Path out, List<String> notes, int gameClasses, double seconds) throws IOException {
		List<Gap> sorted = new ArrayList<>(gaps.values());
		sorted.sort(Comparator.<Gap>comparingInt(g -> g.category().ordinal()).thenComparing(Comparator.comparingInt(Gap::total).reversed()).thenComparing(g -> g.owner));

		List<Object> jsonGaps = new ArrayList<>();
		for (Gap g : sorted) {
			Map<String, Object> o = new LinkedHashMap<>();
			o.put("owner", g.owner);
			o.put("classMissing", g.classGone);
			o.put("category", g.category().label);
			o.put("uses", g.total());
			if (g.hint != null) o.put("hint", g.hint);
			if (g.classUse.total > 0) o.put("classUses", use(g.classUse));
			List<Object> members = new ArrayList<>();
			g.members.entrySet().stream().sorted(Comparator.<Map.Entry<String, Use>>comparingInt(e -> e.getValue().total).reversed()
					.thenComparing(Map.Entry::getKey)).forEach(e -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("member", e.getKey());
				m.putAll(use(e.getValue()));
				if (g.memberHints.containsKey(e.getKey())) m.put("hint", g.memberHints.get(e.getKey()));
				members.add(m);
			});
			o.put("members", members);
			jsonGaps.add(o);
		}
		List<Object> jsonMods = new ArrayList<>();
		for (Map.Entry<String, Summary> e : summaries.entrySet()) {
			Summary s = e.getValue();
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", e.getKey());
			m.put("file", s.file);
			m.put("rules", s.kind == Kind.LEGACY ? "renames.txt" : "renames-26x.txt");
			m.put("classesScanned", (long) s.classes);
			m.put("classesFailed", (long) s.failed);
			m.put("missingRefs", (long) s.refs.size());
			m.put("callSites", (long) s.sites);
			Map<String, Object> byCat = new LinkedHashMap<>();
			for (Cat c : Cat.values()) {
				Map<String, Object> counts = new LinkedHashMap<>();
				counts.put("callSites", (long) s.catSites.getOrDefault(c, 0));
				counts.put("missingRefs", (long) s.catRefs.getOrDefault(c, Set.of()).size());
				byCat.put(c.label, counts);
			}
			m.put("categories", byCat);
			jsonMods.add(m);
		}
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("gameClassesIndexed", (long) gameClasses);
		root.put("seconds", Math.round(seconds * 10) / 10.0);
		Map<String, Object> totals = new LinkedHashMap<>();
		for (Cat c : Cat.values()) {
			Map<String, Object> counts = new LinkedHashMap<>();
			counts.put("callSites", (long) summaries.values().stream().mapToInt(s -> s.catSites.getOrDefault(c, 0)).sum());
			counts.put("ownerClasses", sorted.stream().filter(g -> g.category() == c).count());
			totals.put(c.label, counts);
		}
		root.put("categoryTotals", totals);
		root.put("mods", jsonMods);
		root.put("gaps", jsonGaps);
		List<Lossy> lossies = new ArrayList<>(lossy.values());
		lossies.sort(Comparator.<Lossy>comparingInt(l -> -l.total).thenComparing(l -> l.kind).thenComparing(l -> l.rule));
		List<Object> jsonLossy = new ArrayList<>();
		for (Lossy l : lossies) {
			Map<String, Object> o = new LinkedHashMap<>();
			o.put("kind", l.kind);
			o.put("rule", l.rule);
			o.put("detail", l.detail);
			o.put("sites", (long) l.total);
			Map<String, Object> byMod = new LinkedHashMap<>();
			l.byMod.forEach((k, v) -> byMod.put(k, (long) v));
			o.put("mods", byMod);
			jsonLossy.add(o);
		}
		root.put("lossy", jsonLossy);
		List<Object> jsonClashes = new ArrayList<>();
		for (String[] c : clashes.values()) {
			Map<String, Object> o = new LinkedHashMap<>();
			o.put("mod", c[0]);
			o.put("class", c[1]);
			o.put("kind", c[2]);
			o.put("member", c[3]);
			o.put("resolution", c[4]);
			o.put("unresolved", c.length > 5);
			jsonClashes.add(o);
		}
		root.put("clashes", jsonClashes);
		root.put("notes", notes);
		Files.writeString(out.resolve("gaps.json"), Json.write(root), StandardCharsets.UTF_8);

		StringBuilder md = new StringBuilder("# Uproot gap scan\n\n");
		md.append("References in the target mods that 26.3 and Fabric API still lack after Uproot's remap, by owner class, most used first.\n");
		md.append("Counts are call sites. Mixin targets and string-based references are not checked.\n\n");
		md.append("Each use is `real` (a reference to fix), `mixin-added` (a mixin of a scanned mod, or an interface its fabric.mod.json injects, gives the owner that member) or\n");
		md.append("`datagen` (made only by datagen classes: entrypoints, subclasses of Fabric datagen types, and classes only those mention). A gap lists under its strongest category.\n\n");
		md.append("## Mods\n\n");
		md.append("| mod | rules | classes | failed | real | mixin-added | datagen | missing refs | call sites |\n|---|---|---|---|---|---|---|---|---|\n");
		for (Map.Entry<String, Summary> e : summaries.entrySet()) {
			Summary s = e.getValue();
			md.append("| ").append(e.getKey()).append(" | ").append(s.kind == Kind.LEGACY ? "renames.txt" : "renames-26x.txt").append(" | ").append(s.classes)
					.append(" | ").append(s.failed);
			for (Cat c : Cat.values()) md.append(" | ").append(s.catSites.getOrDefault(c, 0));
			md.append(" | ").append(s.refs.size()).append(" | ").append(s.sites).append(" |\n");
		}
		md.append("\nCategory columns are call sites.\n");
		Cat section = null;
		for (Gap g : sorted) {
			if (g.category() != section) {
				section = g.category();
				Cat now = section;
				md.append("\n## ").append(now.label).append(" gaps (").append(sorted.stream().filter(x -> x.category() == now).count()).append(" owner classes)\n");
			}
			md.append("\n### ").append(g.owner.replace('/', '.')).append(g.classGone ? "  (class missing)" : "").append(" - ").append(g.total()).append(" uses").append(cats(g.classUse, g.members.values())).append('\n');
			if (g.hint != null) md.append("- ").append(g.hint).append('\n');
			if (g.classUse.total > 0) md.append("- as a type: ").append(g.classUse.total).append(" (").append(mods(g.classUse)).append(")").append(cats(g.classUse, List.of())).append('\n');
			g.members.entrySet().stream().sorted(Comparator.<Map.Entry<String, Use>>comparingInt(e -> e.getValue().total).reversed()
					.thenComparing(Map.Entry::getKey)).forEach(e -> {
				md.append("- `").append(e.getKey()).append("` - ").append(e.getValue().total).append(" (").append(mods(e.getValue())).append(')').append(cats(e.getValue(), List.of()));
				if (g.memberHints.containsKey(e.getKey())) md.append(" [").append(g.memberHints.get(e.getKey())).append(']');
				md.append('\n');
			});
		}
		md.append("\n## Lossy mappings (not gaps, not counted above)\n\n");
		md.append("Rules that drop a call or answer it with a stand-in that does nothing, so a mod's behaviour is lost without an error. Each wants a real\n");
		md.append("mapping or a written reason. Kinds: `calldrop` (a rule of renames.txt removes the call), `shim-noop` (a call lands on a shim method whose compiled body\n");
		md.append("is empty or only returns a constant or an argument), `shim-approx` (a call lands on a shim method marked `@Approximate`, with what it gives up), `disable-stub`, `disable-noop`, `disable-skip` and `disable-mixin` (entries of disable.txt; a rule marked `legacy` applies only to mods remapped from 1.21.11), and `dead-hook` (a mixin injector of a mod that matches nothing in 26.3, from dead-hooks.txt; the real breaks are tagged).\n");
		md.append("Sites are call sites for the first two, rule applications for stub and noop, and, for skip and mixin, the strings that name the id and the mixin classes present (a rough count).\n\n");
		md.append("| kind | rule | sites | mods | what it does or why |\n|---|---|---|---|---|\n");
		for (Lossy l : lossies) {
			md.append("| ").append(l.kind).append(" | `").append(l.rule).append("` | ").append(l.total).append(" | ").append(l.byMod.isEmpty() ? "none" : mods(l.byMod)).append(" | ")
					.append(l.detail.replace("|", "/")).append(" |\n");
		}
		md.append("\n## Member clashes (resolved by the remapper, not gaps; the unresolved ones are listed as gaps too)\n\n");
		md.append("A class that would have two methods or fields with the same name and descriptor once named (the JVM refuses such a class). The mod's own member is\n");
		md.append("renamed with the suffix `$clash`, with its calls, its whole override family (every mod class that overrides or implements it, or that it does), and the mixin\n");
		md.append("shadows, invokers, accessors, `method=` strings and lambdas that name it, or a compiler bridge is merged into the method it forwards to; see remap.Clashes.\n");
		md.append("**Not followed:** a member looked up by name through reflection (`Class.getMethod(\"foo\")`) keeps reaching the member that kept the name.\n\n");
		if (clashes.isEmpty()) md.append("None.\n");
		else {
			md.append("| mod | class | kind | member | resolution |\n|---|---|---|---|---|\n");
			for (String[] c : clashes.values()) md.append("| ").append(c[0]).append(" | `").append(c[1]).append("` | ").append(c[2]).append(" | `").append(c[3]).append("` | ").append(c[4].replace("|", "/")).append(" |\n");
		}
		if (!notes.isEmpty()) {
			md.append("\n## Notes\n\n");
			for (String n : notes) md.append("- ").append(n).append('\n');
		}
		Files.writeString(out.resolve("gaps.md"), md.toString(), StandardCharsets.UTF_8);

		int sites = summaries.values().stream().mapToInt(s -> s.sites).sum();
		System.out.println("scanned " + summaries.size() + " mods, " + sorted.size() + " owner classes with gaps, " + sites + " call sites, "
				+ Math.round(seconds) + "s; wrote " + out.resolve("gaps.md") + " and gaps.json");
		System.out.println("  lossy (not gaps): " + lossies.stream().filter(l -> l.total > 0).count() + " rules hit, " + lossies.stream().mapToInt(l -> l.total).sum() + " sites, "
				+ lossies.stream().filter(l -> l.total == 0).count() + " rules never hit");
		System.out.println("  member clashes (resolved by the remapper, " + clashes.values().stream().filter(c -> c.length > 5).count() + " unresolved): " + clashes.size() + " in " + clashes.values().stream().map(c -> c[0]).distinct().count() + " mods");
		for (Cat c : Cat.values()) {
			System.out.println("  " + c.label + ": " + summaries.values().stream().mapToInt(s -> s.catSites.getOrDefault(c, 0)).sum() + " call sites, "
					+ sorted.stream().filter(g -> g.category() == c).count() + " owner classes");
		}
	}

	/** The category split of a gap's uses, when they are not all real ({@code " [real 2, datagen 10]"}); empty when every use is real. */
	private static String cats(Use first, java.util.Collection<Use> more) {
		Map<Cat, Integer> sum = new EnumMap<>(Cat.class);
		first.cats.forEach((c, n) -> sum.merge(c, n, Integer::sum));
		for (Use u : more) u.cats.forEach((c, n) -> sum.merge(c, n, Integer::sum));
		if (sum.getOrDefault(Cat.REAL, 0) == sum.values().stream().mapToInt(Integer::intValue).sum()) return "";
		return " [" + sum.entrySet().stream().map(e -> e.getKey().label + " " + e.getValue()).collect(Collectors.joining(", ")) + "]";
	}

	private static String mods(Map<String, Integer> byMod) {
		return byMod.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
	}

	private static String mods(Use u) {
		return u.byMod.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
	}

	private static Map<String, Object> use(Use u) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("uses", (long) u.total);
		Map<String, Object> byMod = new LinkedHashMap<>();
		u.byMod.forEach((k, v) -> byMod.put(k, (long) v));
		m.put("mods", byMod);
		Map<String, Object> cats = new LinkedHashMap<>();
		for (Cat c : Cat.values()) if (u.cats.getOrDefault(c, 0) > 0) cats.put(c.label, (long) u.cats.get(c));
		m.put("categories", cats);
		Map<String, Object> ex = new LinkedHashMap<>();
		u.examples.forEach((k, v) -> ex.put(k, new ArrayList<>(v)));
		m.put("examples", ex);
		return m;
	}
}
