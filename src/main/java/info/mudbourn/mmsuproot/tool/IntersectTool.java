package info.mudbourn.mmsuproot.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import info.mudbourn.mmsuproot.boot.Report;
import info.mudbourn.mmsuproot.disable.Disabler;
import info.mudbourn.mmsuproot.map.GameClasses;
import info.mudbourn.mmsuproot.remap.Namer;
import info.mudbourn.mmsuproot.tool.IntersectCode.MixinCfg;
import info.mudbourn.mmsuproot.tool.IntersectContent.ModContent;
import info.mudbourn.mmsuproot.tool.ScanTool.Jar;
import info.mudbourn.mmsuproot.tool.ScanTool.Kind;
import info.mudbourn.mmsuproot.util.Json;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Offline intersection heat: finds every point where two or more mods of a pack touch the same thing and scores how fragile each
 * point is, so conflicts can be fixed without play-testing.
 *
 * <pre>java -cp &lt;mms-uproot and the game classes&gt; info.mudbourn.mmsuproot.tool.IntersectTool [--out dir] [--cache dir] [--max-nodes N] &lt;mods dir or jar&gt;...</pre>
 *
 * <p>Jar discovery (nested jars, duplicate ids, {@code .disabled}), the three kinds of mod and the remap to 26.3 names are
 * {@link ScanTool}'s, so every class, method and field a 1.21.11 or 26.x mod mentions is compared in 26.3 Mojang names. The code of
 * each mod ({@link IntersectCode}) and the content of each jar ({@link IntersectContent}, vanilla 26.3 included as the pseudo-mod
 * {@code minecraft}) become <em>touches</em>: a mod, a kind, a detail, a weight and whether Uproot remapped the mod. Touches meet on
 * <em>nodes</em>.
 *
 * <p>Scores. {@code heat} of a node = (sum of the weights of its touches) x (non-library parties - 1), x1.5 when any touch is of a
 * remapped mod; only nodes of two or more parties and a heat above 0 are kept. A {@code pair} scores, over the nodes both mods touch,
 * the smaller of their two weights x the category factor (method 1, site 1.5, class 0.5, access 0.5, id 1, name 0.7, tag 0.5, override
 * 1.2, recipe 1, modifier 1); library mods and the vanilla pseudo-mod form no pair, and a node with more than 30 parties counts its 30
 * heaviest. A mod scores the sum of the heat of the nodes it touches.
 *
 * <p>Output {@code intersections.json}, keys stable:
 * <pre>
 * { generated, modsDir, nodesTotal, nodesWritten,
 *   mods:  [ { id, version, kind: native|older26|legacy1_21_11|vanilla, library, file, nodeCount, score } ],
 *   nodes: [ { key, category, heat, worst: [modA, modB], mods: [id...], owner: { repo, reason }, touchCount,
 *              touches: [ { mod, kind, detail, weight, legacy } ] } ],
 *   pairs: [ { a, b, score, byCategory: { category: score }, topNodes: [key...], owner: { repo, reason } } ],
 *   coverage: [ { mod, repo, weaponItems, covered, hasOldJar, uncovered: [ { item, evidence, newSinceOld } ] } ],
 *   updates:  [ { mod, oldFile, newFile, added: { items, blocks, entities, enchantments }, removed: { ... },
 *                 mixinTargets: { added, removed, addedCount, removedCount } } ] }
 * </pre>
 * A node key is {@code CATEGORY:...} (see {@link IntersectCode} and {@link IntersectContent}); {@code category} is that prefix.
 * {@code touches} are the heaviest 20 of {@code touchCount}. {@code owner} is the repo a fix most likely belongs to, by the rules of
 * {@code intersect-owners.txt}.
 */
public final class IntersectTool {
	private static final Map<String, Double> FACTOR = Map.of("method", 1.0, "site", 1.5, "class", 0.5, "access", 0.5, "id", 1.0, "name", 0.7,
			"tag", 0.5, "override", 1.2, "recipe", 1.0, "modifier", 1.0);
	private static final int MAX_PARTIES = 30;
	private static final int MAX_TOUCHES = 20;

	private final IntersectRules rules = new IntersectRules();
	private final List<String> notes = new ArrayList<>();
	private final Map<String, ModInfo> infos = new TreeMap<>();
	private final Map<String, ModContent> contents = new HashMap<>();
	private final Map<String, ModContent> oldContents = new HashMap<>();
	private final Map<String, String> oldFiles = new HashMap<>();
	private final Map<String, Set<String>> codeKeys = new HashMap<>();
	private final Map<String, Set<String>> oldCodeKeys = new HashMap<>();
	private ModContent vanilla;

	private record ModInfo(String id, String version, String kind, boolean library, String file, boolean legacy) { }

	private IntersectTool() { }

	public static void main(String[] args) throws IOException {
		Path out = Path.of("build/uproot-intersect");
		Path cache = Path.of("run/mms-uproot/cache");
		int maxNodes = 30000;
		List<Path> inputs = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "--out" -> out = Path.of(args[++i]);
			case "--cache" -> cache = Path.of(args[++i]);
			case "--max-nodes" -> maxNodes = Integer.parseInt(args[++i]);
			default -> inputs.add(Path.of(args[i]));
			}
		}
		if (inputs.isEmpty()) inputs.add(Path.of("run/mods"));
		long start = System.nanoTime();
		Files.createDirectories(out);
		new IntersectTool().run(inputs, out, cache, maxNodes, start);
	}

	private void run(List<Path> inputs, Path out, Path cache, int maxNodes, long start) throws IOException {
		List<Path> classPath = Stream.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)).map(Path::of).toList();
		ClassLoader loader = IntersectTool.class.getClassLoader();
		GameClasses game = new GameClasses(loader, classPath);
		if (!game.indexed()) throw new IllegalStateException("no Minecraft jar on the class path (net/minecraft/world/level/Level.class not found)");
		Report report = new Report(out.resolve("uproot-report.txt"));
		Disabler.init(report);
		List<FileSystem> open = new ArrayList<>();
		try {
			Path nested = out.resolve("nested");
			List<Jar> jars = ScanTool.discover(inputs, nested, open, notes);
			log(start, "discovered " + jars.size() + " mods");

			// the pre-update jars: *.jar.disabled beside the mods, copied under a plain name so that the same discovery reads them
			List<Jar> oldJars = new ArrayList<>();
			Path disabledCopies = out.resolve("disabled");
			List<Path> copied = copyDisabled(inputs, disabledCopies);
			if (!copied.isEmpty()) oldJars = ScanTool.discover(List.of(disabledCopies), nested, open, notes);
			log(start, "found " + oldJars.size() + " pre-update jars");

			for (Jar j : jars) infos.put(j.id(), info(j));
			infos.put("minecraft", new ModInfo("minecraft", "26.3", "vanilla", false, "minecraft-merged.jar", false));

			// content, in parallel; the vanilla jar is one more job
			Path vanillaJar = vanillaJar(classPath);
			FileSystem vfs = FileSystems.newFileSystem(vanillaJar);
			open.add(vfs);
			record Job(String mod, Path root, boolean legacy, boolean old) { }
			List<Job> jobs = new ArrayList<>();
			jobs.add(new Job("minecraft", vfs.getPath("/"), false, false));
			for (Jar j : jars) jobs.add(new Job(j.id(), j.root(), j.kind() != Kind.NONE, false));
			for (Jar j : oldJars) jobs.add(new Job(j.id(), j.root(), true, true));
			record Done(Job job, ModContent content, Touches touches, List<String> notes) { }
			List<Done> done = jobs.parallelStream().map(job -> {
				Touches t = new Touches();
				List<String> n = new ArrayList<>();
				ModContent c = IntersectContent.scan(job.mod(), job.root(), job.legacy(), job.mod().equals("minecraft"), t, n);
				return new Done(job, c, t, n);
			}).toList();
			Touches all = new Touches();
			for (Done d : done) {
				notes.addAll(d.notes());
				if (d.job().old()) {
					oldContents.put(d.job().mod(), d.content());
					continue;
				}
				if (d.job().mod().equals("minecraft")) vanilla = d.content();
				else contents.put(d.job().mod(), d.content());
				all.merge(d.touches());
			}
			for (Jar j : oldJars) oldFiles.put(j.id(), j.label());
			log(start, "content scanned");

			// code
			Touches code = code(jars, game, loader, report, cache, true);
			code.byNode.forEach((k, v) -> v.forEach(t -> codeKeys.computeIfAbsent(t.mod(), x -> new HashSet<>()).add(k)));
			all.merge(code);
			log(start, "code scanned");
			if (!oldJars.isEmpty()) {
				Touches oldCode = code(oldJars, game, loader, report, cache, false);
				oldCode.byNode.forEach((k, v) -> v.forEach(t -> oldCodeKeys.computeIfAbsent(t.mod(), x -> new HashSet<>()).add(k)));
				log(start, "pre-update code scanned");
			}

			// the vanilla copy of a file a mod overrides is a party of that node
			for (Map.Entry<String, List<Touch>> e : all.byNode.entrySet()) {
				if (!e.getKey().startsWith("override:")) continue;
				if (vanilla.paths.contains(e.getKey().substring("override:".length()))) e.getValue().add(new Touch("minecraft", "vanilla", "vanilla ships this file", 3, false, false));
			}

			Scored scored = score(all, jars);
			log(start, "scored");
			List<Map<String, Object>> coverage = coverage(jars);
			List<Map<String, Object>> updates = updates();
			write(out, inputs, scored, coverage, updates, maxNodes, (System.nanoTime() - start) / 1_000_000_000.0);
			log(start, "written");
		} finally {
			for (FileSystem fs : open) {
				try { fs.close(); } catch (IOException ignored) { }
			}
		}
	}

	private static void log(long start, String what) {
		System.out.printf("[%6.1fs] %s%n", (System.nanoTime() - start) / 1e9, what);
	}

	private ModInfo info(Jar j) {
		String version = "?";
		try {
			version = String.valueOf(Json.parseObject(Files.readString(j.root().resolve("fabric.mod.json"), StandardCharsets.UTF_8)).getOrDefault("version", "?"));
		} catch (IOException | RuntimeException e) {
			notes.add(j.id() + ": version unreadable");
		}
		String kind = switch (j.kind()) { case NONE -> "native"; case MODERN -> "older26"; case LEGACY -> "legacy1_21_11"; };
		return new ModInfo(j.id(), version, kind, rules.isLibrary(j.id()), j.label(), j.kind() != Kind.NONE);
	}

	/** The *.jar.disabled files beside the mods, as plain .jar copies in one folder. */
	private List<Path> copyDisabled(List<Path> inputs, Path into) throws IOException {
		List<Path> out = new ArrayList<>();
		for (Path in : inputs) {
			if (!Files.isDirectory(in)) continue;
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(in, "*.jar.disabled")) {
				for (Path p : ds) {
					Files.createDirectories(into);
					String name = p.getFileName().toString();
					Path copy = into.resolve(name.substring(0, name.length() - ".disabled".length()));
					Files.copy(p, copy, StandardCopyOption.REPLACE_EXISTING);
					out.add(copy);
				}
			}
		}
		return out;
	}

	private static Path vanillaJar(List<Path> classPath) throws IOException {
		Path fallback = null;
		for (Path p : classPath) {
			if (!Files.isRegularFile(p)) continue;
			try (ZipFile z = new ZipFile(p.toFile())) {
				if (z.getEntry("net/minecraft/world/level/Level.class") == null) continue;
				if (z.getEntry("assets/minecraft/lang/en_us.json") != null) return p;
				fallback = p;
			} catch (IOException e) {
				// not a jar
			}
		}
		if (fallback == null) throw new IllegalStateException("no Minecraft jar with assets on the class path");
		return fallback;
	}

	// ---- code

	@SuppressWarnings("unchecked")
	private Map<String, MixinCfg> mixinClasses(Jar j) {
		Map<String, MixinCfg> out = new HashMap<>();
		try {
			Map<String, Object> fmj = Json.parseObject(Files.readString(j.root().resolve("fabric.mod.json"), StandardCharsets.UTF_8));
			if (!(fmj.get("mixins") instanceof List<?> configs)) return out;
			for (Object c : configs) {
				String file = c instanceof String s ? s : c instanceof Map<?, ?> m && m.get("config") instanceof String s2 ? s2 : null;
				if (file == null || !Files.isRegularFile(j.root().resolve(file))) continue;
				Map<String, Object> cfg = Json.parseObject(Files.readString(j.root().resolve(file), StandardCharsets.UTF_8));
				String pkg = cfg.get("package") instanceof String s ? s + "." : "";
				int priority = cfg.get("priority") instanceof Number n ? n.intValue() : 1000;
				Integer require = cfg.get("injectors") instanceof Map<?, ?> inj && inj.get("defaultRequire") instanceof Number n ? n.intValue() : null;
				MixinCfg mc = new MixinCfg(priority, require);
				for (String key : new String[] {"mixins", "client", "server"}) {
					if (cfg.get(key) instanceof List<?> l) for (Object name : l) out.put((pkg + name).replace('.', '/'), mc);
				}
			}
		} catch (IOException | RuntimeException e) {
			notes.add(j.id() + ": its mixin configs could not be read: " + e);
		}
		return out;
	}

	/** Mixin handlers and access wideners of every mod, named in 26.3 terms. {@code live}: the pack's own jars (not the pre-update ones). */
	@SuppressWarnings("unchecked")
	private Touches code(List<Jar> jars, GameClasses game, ClassLoader loader, Report report, Path cache, boolean live) {
		Touches out = new Touches();
		Map<String, Map<String, MixinCfg>> cfgs = new HashMap<>();
		for (Jar j : jars) cfgs.put(j.id(), mixinClasses(j));
		ScanTool tool = new ScanTool(game, loader);
		tool.sink = r -> {
			MixinCfg c = cfgs.getOrDefault(r.mod(), Map.of()).get(r.name());
			if (c == null) return;
			try {
				IntersectCode.analyze(r.mod(), true, r.bytes(), c, game, out, rules);
			} catch (RuntimeException e) {
				notes.add(r.mod() + ": " + r.name() + " could not be read for mixin handlers: " + e);
			}
		};
		try {
			tool.run(jars, game, report, cache, live ? notes : new ArrayList<>());
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		for (Jar j : jars) {
			if (j.kind() == Kind.NONE) {
				for (Map.Entry<String, MixinCfg> e : cfgs.get(j.id()).entrySet()) {
					Path p = j.root().resolve(e.getKey() + ".class");
					if (!Files.isRegularFile(p)) continue;
					try {
						IntersectCode.analyze(j.id(), false, Files.readAllBytes(p), e.getValue(), game, out, rules);
					} catch (IOException | RuntimeException ex) {
						notes.add(j.id() + ": " + e.getKey() + " could not be read for mixin handlers: " + ex);
					}
				}
			}
			try {
				Map<String, Object> fmj = Json.parseObject(Files.readString(j.root().resolve("fabric.mod.json"), StandardCharsets.UTF_8));
				Object aw = fmj.get("accessWidener") != null ? fmj.get("accessWidener") : fmj.get("classTweaker");
				if (aw instanceof String file && Files.isRegularFile(j.root().resolve(file))) {
					Namer namer = tool.namersOut.get(j.id());
					IntersectCode.widener(j.id(), j.kind() != Kind.NONE, Files.readString(j.root().resolve(file)), namer, out, rules);
				}
			} catch (IOException | RuntimeException ex) {
				notes.add(j.id() + ": its access widener could not be read: " + ex);
			}
		}
		return out;
	}

	// ---- scoring

	private record NodeOut(String key, String category, double heat, String[] worst, List<String> mods, IntersectRules.Owner owner, int touchCount,
			List<Touch> touches, double weight, int parties) { }

	private static final class Pair {
		double score;
		final Map<String, Double> byCategory = new TreeMap<>();
		final List<Object[]> top = new ArrayList<>(); // {contribution, key}
		final Map<String, Double> byRepo = new HashMap<>();
		final Map<String, String> repoReason = new HashMap<>();
	}

	private record Scored(List<NodeOut> nodes, Map<String, Pair> pairs, Map<String, double[]> modScore, int nodesTotal, Map<String, Integer> byCategory) { }

	private static String category(String key) {
		return key.substring(0, key.indexOf(':'));
	}

	private Scored score(Touches all, List<Jar> jars) {
		Map<String, Pair> pairs = new HashMap<>();
		Map<String, double[]> modScore = new HashMap<>(); // {score, nodeCount}
		Map<String, Integer> byCategory = new TreeMap<>();
		List<NodeOut> nodes = new ArrayList<>();
		for (Map.Entry<String, List<Touch>> e : all.byNode.entrySet()) {
			String key = e.getKey();
			List<Touch> touches = e.getValue();
			Set<String> mods = new TreeSet<>();
			for (Touch t : touches) mods.add(t.mod());
			if (mods.size() < 2) continue;
			String cat = category(key);
			if (cat.equals("recipe")) {
				Set<String> outputs = new HashSet<>();
				for (Touch t : touches) outputs.add(t.detail());
				if (outputs.size() > 1) {
					List<Touch> raised = new ArrayList<>();
					for (Touch t : touches) raised.add(t.withWeight(5));
					touches = raised;
				}
			}
			Map<String, Double> tw = new LinkedHashMap<>();
			double sum = 0;
			boolean legacy = false;
			for (Touch t : touches) {
				tw.merge(t.mod(), t.weight(), Double::sum);
				sum += t.weight();
				legacy |= t.legacy();
			}
			long nonLib = mods.stream().filter(m -> !isLibrary(m)).count();
			double heat = sum * (nonLib - 1) * (legacy ? 1.5 : 1.0);
			if (heat <= 0) continue;
			// the parties that can form pairs: mods, not libraries, not vanilla; the heaviest MAX_PARTIES
			List<Map.Entry<String, Double>> parties = new ArrayList<>();
			for (Map.Entry<String, Double> m : tw.entrySet()) if (!isLibrary(m.getKey()) && !m.getKey().equals("minecraft")) parties.add(m);
			parties.sort(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
			if (parties.size() > MAX_PARTIES) parties = parties.subList(0, MAX_PARTIES);
			String[] worst = new String[2];
			double worstMin = -1;
			double factor = FACTOR.getOrDefault(cat, 1.0);
			for (int a = 0; a < parties.size(); a++) {
				for (int b = a + 1; b < parties.size(); b++) {
					double min0 = Math.min(parties.get(a).getValue(), parties.get(b).getValue());
					if (min0 > worstMin) {
						worstMin = min0;
						String x = parties.get(a).getKey();
						String y = parties.get(b).getKey();
						worst = x.compareTo(y) < 0 ? new String[] {x, y} : new String[] {y, x};
					}
				}
			}
			if (worst[0] == null) {
				List<String> m = new ArrayList<>(mods);
				worst = new String[] {m.get(0), m.get(1)};
			}
			IntersectRules.Owner owner = rules.owner(key, touches, worst);
			for (int a = 0; a < parties.size(); a++) {
				for (int b = a + 1; b < parties.size(); b++) {
					String ma = parties.get(a).getKey();
					String mb = parties.get(b).getKey();
					double min = Math.min(parties.get(a).getValue(), parties.get(b).getValue());
					double contribution = min * factor;
					Pair p = pairs.computeIfAbsent(worstKey(ma, mb), k -> new Pair());
					p.score += contribution;
					p.byCategory.merge(cat, contribution, Double::sum);
					p.top.add(new Object[] {contribution, key});
					if (p.top.size() > 12) {
						p.top.sort((x, y) -> Double.compare((Double) y[0], (Double) x[0]));
						p.top.subList(5, p.top.size()).clear();
					}
					p.byRepo.merge(owner.repo(), contribution, Double::sum);
					p.repoReason.putIfAbsent(owner.repo(), owner.reason());
				}
			}
			double h = Math.round(heat * 100) / 100.0;
			for (String m : mods) {
				double[] s = modScore.computeIfAbsent(m, k -> new double[2]);
				s[0] += h;
				s[1]++;
			}
			byCategory.merge(cat, 1, Integer::sum);
			List<Touch> top = new ArrayList<>(touches);
			top.sort(Comparator.comparingDouble(Touch::weight).reversed());
			int count = top.size();
			if (top.size() > MAX_TOUCHES) top = new ArrayList<>(top.subList(0, MAX_TOUCHES));
			nodes.add(new NodeOut(key, cat, h, worst, new ArrayList<>(mods), owner, count, top, sum, mods.size()));
		}
		nodes.sort(Comparator.comparingDouble(NodeOut::heat).reversed().thenComparing(NodeOut::key));
		return new Scored(nodes, pairs, modScore, nodes.size(), byCategory);
	}

	private static String worstKey(String a, String b) {
		return a.compareTo(b) < 0 ? a + "\u0001" + b : b + "\u0001" + a;
	}

	private boolean isLibrary(String mod) {
		ModInfo i = infos.get(mod);
		return i != null && i.library();
	}

	// ---- coverage

	private static final Pattern ID = Pattern.compile("#?([a-z0-9_.-]+):([a-z0-9_./-]*\\*?)");

	/** Item ids, tag ids and wildcard namespaces that the mms mods mention in their data, assets, config files and string constants. */
	private record Refs(Set<String> ids, Set<String> namespaces) { }

	private Refs references(List<Jar> jars) {
		Set<String> ids = new HashSet<>();
		Set<String> ns = new HashSet<>();
		Set<String> found = new TreeSet<>();
		for (Jar j : jars) {
			if (!rules.coverageMods.contains(j.id())) continue;
			found.add(j.id());
			try (Stream<Path> walk = Files.walk(j.root())) {
				for (Path p : walk.filter(Files::isRegularFile).toList()) {
					String name = p.getFileName().toString();
					try {
						if (name.endsWith(".class")) {
							classStrings(Files.readAllBytes(p), ids, ns);
						} else if (name.matches(".*\\.(json|json5|toml|properties|cfg|conf|yml|yaml|mcmeta|lang|snbt|xml)$") && Files.size(p) < 4_000_000) {
							tokens(Files.readString(p, StandardCharsets.UTF_8), ids, ns);
						}
					} catch (IOException | RuntimeException e) {
						// an unreadable file is not a reference
					}
				}
			} catch (IOException e) {
				notes.add(j.id() + ": could not be read for references: " + e);
			}
		}
		for (String m : rules.coverageMods) if (!found.contains(m)) notes.add("coverage mod " + m + " is not in the mods folder, so it references nothing");
		return new Refs(ids, ns);
	}

	private static void tokens(String text, Set<String> ids, Set<String> ns) {
		Matcher m = ID.matcher(text);
		while (m.find()) {
			String namespace = m.group(1);
			String path = m.group(2);
			if (path.isEmpty() || path.equals("*")) {
				if (!namespace.equals("minecraft")) ns.add(namespace);
			} else {
				ids.add(namespace + ":" + path);
			}
		}
	}

	private static void classStrings(byte[] bytes, Set<String> ids, Set<String> ns) {
		ClassNode cn = new ClassNode();
		new ClassReader(bytes).accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		for (FieldNode f : cn.fields) if (f.value instanceof String s) tokens(s, ids, ns);
		for (MethodNode m : cn.methods) {
			for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
				if (i instanceof LdcInsnNode l && l.cst instanceof String s && s.indexOf(':') > 0 && s.length() < 200) tokens(s, ids, ns);
			}
		}
	}

	private static void closure(String tag, Map<String, Set<String>> tags, Set<String> into, Set<String> seen) {
		if (!seen.add(tag)) return;
		for (String v : tags.getOrDefault(tag, Set.of())) {
			if (v.startsWith("#")) closure(v.substring(1), tags, into, seen);
			else into.add(v);
		}
	}

	private List<Map<String, Object>> coverage(List<Jar> jars) {
		Map<String, Set<String>> tags = new HashMap<>();
		Map<String, ModContent> everything = new HashMap<>(contents);
		everything.put("minecraft", vanilla);
		for (ModContent c : everything.values()) c.itemTags.forEach((k, v) -> tags.computeIfAbsent(k, x -> new TreeSet<>()).addAll(v));
		Map<String, String> evidence = new HashMap<>();
		for (String tag : tags.keySet()) {
			if (!rules.isDomainTag(tag)) continue;
			Set<String> items = new TreeSet<>();
			closure(tag, tags, items, new HashSet<>());
			for (String item : items) evidence.putIfAbsent(item, "tag #" + tag);
		}
		for (ModContent c : everything.values()) for (String item : c.weaponAttributes) evidence.putIfAbsent(item, "weapon_attributes file");
		Refs refs = references(jars);
		Set<String> covered = new HashSet<>(refs.ids());
		Map<String, String> coveredBy = new HashMap<>();
		for (String id : refs.ids()) {
			if (!tags.containsKey(id)) continue;
			Set<String> items = new TreeSet<>();
			closure(id, tags, items, new HashSet<>());
			for (String item : items) {
				covered.add(item);
				coveredBy.putIfAbsent(item, id);
			}
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map.Entry<String, ModContent> e : new TreeMap<>(contents).entrySet()) {
			String mod = e.getKey();
			if (rules.modRepo.containsKey(mod) || mod.startsWith("mms_") || isLibrary(mod)) continue;
			ModContent old = oldContents.get(mod);
			int weapons = 0;
			int coveredCount = 0;
			List<Map<String, Object>> uncovered = new ArrayList<>();
			for (String item : e.getValue().items) {
				String ev = evidence.get(item);
				String path = item.substring(item.indexOf(':') + 1);
				if (ev == null && rules.isDomainItem(path)) ev = "item name";
				if (ev == null) continue;
				weapons++;
				String ns = item.substring(0, item.indexOf(':'));
				if (covered.contains(item) || refs.namespaces().contains(ns)) {
					coveredCount++;
					continue;
				}
				Map<String, Object> u = new LinkedHashMap<>();
				u.put("item", item);
				u.put("evidence", ev);
				u.put("newSinceOld", old == null ? null : !old.items.contains(item));
				uncovered.add(u);
			}
			if (uncovered.isEmpty()) continue;
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("mod", mod);
			m.put("repo", rules.routeCoverage(String.valueOf(uncovered.get(0).get("item"))));
			m.put("weaponItems", weapons);
			m.put("covered", coveredCount);
			m.put("hasOldJar", old != null);
			m.put("uncovered", uncovered);
			out.add(m);
		}
		out.sort(Comparator.comparingInt((Map<String, Object> m) -> -((List<?>) m.get("uncovered")).size()).thenComparing(m -> String.valueOf(m.get("mod"))));
		return out;
	}

	// ---- updates

	private List<Map<String, Object>> updates() {
		List<Map<String, Object>> out = new ArrayList<>();
		for (String mod : new TreeSet<>(oldContents.keySet())) {
			ModContent now = contents.get(mod);
			ModContent old = oldContents.get(mod);
			if (now == null) continue; // no live jar to compare with
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("mod", mod);
			m.put("oldFile", oldFiles.get(mod));
			m.put("newFile", infos.get(mod) == null ? "?" : infos.get(mod).file());
			m.put("added", diff(now, old, true));
			m.put("removed", diff(now, old, false));
			Set<String> a = new TreeSet<>(codeKeys.getOrDefault(mod, Set.of()));
			a.removeAll(oldCodeKeys.getOrDefault(mod, Set.of()));
			Set<String> r = new TreeSet<>(oldCodeKeys.getOrDefault(mod, Set.of()));
			r.removeAll(codeKeys.getOrDefault(mod, Set.of()));
			Map<String, Object> mt = new LinkedHashMap<>();
			mt.put("addedCount", a.size());
			mt.put("removedCount", r.size());
			mt.put("added", new ArrayList<>(a).subList(0, Math.min(a.size(), 100)));
			mt.put("removed", new ArrayList<>(r).subList(0, Math.min(r.size(), 100)));
			m.put("mixinTargets", mt);
			out.add(m);
		}
		return out;
	}

	private static Map<String, Object> diff(ModContent now, ModContent old, boolean added) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("items", sub(added ? now.items : old.items, added ? old.items : now.items));
		m.put("blocks", sub(added ? now.blocks : old.blocks, added ? old.blocks : now.blocks));
		m.put("entities", sub(added ? now.entities : old.entities, added ? old.entities : now.entities));
		m.put("enchantments", sub(added ? now.enchantments : old.enchantments, added ? old.enchantments : now.enchantments));
		return m;
	}

	private static List<String> sub(Set<String> a, Set<String> b) {
		List<String> out = new ArrayList<>();
		for (String s : a) if (!b.contains(s)) out.add(s);
		return out;
	}

	// ---- output

	private static Map<String, Object> ownerJson(IntersectRules.Owner o) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("repo", o.repo());
		m.put("reason", o.reason());
		return m;
	}

	private IntersectRules.Owner pairOwner(Pair p) {
		String best = p.byRepo.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("mms-mod-compat-support");
		return new IntersectRules.Owner(best, p.repoReason.getOrDefault(best, "") + " (most of the pair's score)");
	}

	private void write(Path out, List<Path> inputs, Scored s, List<Map<String, Object>> coverage, List<Map<String, Object>> updates, int maxNodes, double seconds) throws IOException {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("generated", Instant.now().toString());
		root.put("modsDir", inputs.stream().map(Path::toString).toList().toString());
		root.put("nodesTotal", s.nodesTotal());
		int written = Math.min(maxNodes, s.nodes().size());
		root.put("nodesWritten", written);

		List<Object> mods = new ArrayList<>();
		List<Map.Entry<String, ModInfo>> sortedMods = new ArrayList<>(infos.entrySet());
		sortedMods.sort(Comparator.comparingDouble((Map.Entry<String, ModInfo> e) -> -s.modScore().getOrDefault(e.getKey(), new double[2])[0]).thenComparing(Map.Entry::getKey));
		for (Map.Entry<String, ModInfo> e : sortedMods) {
			ModInfo i = e.getValue();
			double[] sc = s.modScore().getOrDefault(i.id(), new double[2]);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("id", i.id());
			m.put("version", i.version());
			m.put("kind", i.kind());
			m.put("library", i.library());
			m.put("file", i.file());
			m.put("nodeCount", (int) sc[1]);
			m.put("score", Math.round(sc[0] * 100) / 100.0);
			mods.add(m);
		}
		root.put("mods", mods);

		List<Object> nodes = new ArrayList<>();
		for (NodeOut n : s.nodes().subList(0, written)) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("key", n.key());
			m.put("category", n.category());
			m.put("heat", n.heat());
			m.put("worst", List.of(n.worst()[0], n.worst()[1]));
			m.put("mods", n.mods());
			m.put("owner", ownerJson(n.owner()));
			m.put("touchCount", n.touchCount());
			List<Object> ts = new ArrayList<>();
			for (Touch t : n.touches()) {
				Map<String, Object> tm = new LinkedHashMap<>();
				tm.put("mod", t.mod());
				tm.put("kind", t.kind());
				tm.put("detail", IntersectCode.cap(t.detail()));
				tm.put("weight", t.weight());
				tm.put("legacy", t.legacy());
				ts.add(tm);
			}
			m.put("touches", ts);
			nodes.add(m);
		}
		root.put("nodes", nodes);

		List<Map.Entry<String, Pair>> pairList = new ArrayList<>(s.pairs().entrySet());
		pairList.sort(Comparator.comparingDouble((Map.Entry<String, Pair> e) -> -e.getValue().score).thenComparing(Map.Entry::getKey));
		List<Object> pairs = new ArrayList<>();
		for (Map.Entry<String, Pair> e : pairList) {
			Pair p = e.getValue();
			String[] ab = e.getKey().split("\u0001");
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("a", ab[0]);
			m.put("b", ab[1]);
			m.put("score", Math.round(p.score * 100) / 100.0);
			Map<String, Object> bc = new LinkedHashMap<>();
			p.byCategory.forEach((k, v) -> bc.put(k, Math.round(v * 100) / 100.0));
			m.put("byCategory", bc);
			p.top.sort((x, y) -> Double.compare((Double) y[0], (Double) x[0]));
			m.put("topNodes", p.top.stream().limit(5).map(x -> (String) x[1]).toList());
			m.put("owner", ownerJson(pairOwner(p)));
			pairs.add(m);
		}
		root.put("pairs", pairs);
		root.put("coverage", coverage);
		root.put("updates", updates);
		Files.writeString(out.resolve("intersections.json"), Json.write(root), StandardCharsets.UTF_8);
		Files.writeString(out.resolve("intersections.md"), markdown(s, pairList, coverage, updates, seconds), StandardCharsets.UTF_8);
		System.out.println("mods " + infos.size() + ", nodes " + s.nodesTotal() + " " + s.byCategory() + ", pairs " + pairList.size() + ", coverage mods " + coverage.size() + ", updates " + updates.size());
	}

	// ---- markdown

	private static String esc(String s) {
		return s.replace("|", "\\|").replace("\n", " ");
	}

	private String markdown(Scored s, List<Map.Entry<String, Pair>> pairList, List<Map<String, Object>> coverage, List<Map<String, Object>> updates, double seconds) {
		StringBuilder md = new StringBuilder("# Intersection heat\n\n");
		md.append(String.format(Locale.ROOT, "%d mods, %d intersecting nodes %s, %d mod pairs, %.0f s. Details in `intersections.json`.%n%n",
				infos.size(), s.nodesTotal(), s.byCategory(), pairList.size(), seconds));
		md.append("Heat of a node = sum of its touch weights x (non-library mods - 1), x1.5 when a remapped (legacy) mod touches it. ")
				.append("A pair scores the smaller weight of the two mods per shared node, times a category factor. Vanilla 26.3 is the party `minecraft`; ")
				.append("it forms no pair. The owner is the repo a fix most likely belongs to (`intersect-owners.txt`).\n\n");

		md.append("## Top 100 nodes\n\n");
		int rank = 0;
		for (NodeOut n : s.nodes()) {
			if (++rank > 100) break;
			Touch first = n.touches().get(0);
			md.append(String.format(Locale.ROOT, "%d. `%s` heat %.1f, %d parties (%s), worst pair %s + %s; top touch %s %s %s; owner **%s** (%s)%n",
					rank, esc(n.key()), n.heat(), n.parties(), String.join(", ", n.mods().subList(0, Math.min(6, n.mods().size()))) + (n.mods().size() > 6 ? ", ..." : ""),
					n.worst()[0], n.worst()[1], first.mod(), first.kind(), esc(first.detail()), n.owner().repo(), n.owner().reason()));
		}

		md.append("\n## Top 50 pairs\n\n");
		rank = 0;
		for (Map.Entry<String, Pair> e : pairList) {
			if (++rank > 50) break;
			Pair p = e.getValue();
			String[] ab = e.getKey().split("\u0001");
			p.top.sort((x, y) -> Double.compare((Double) y[0], (Double) x[0]));
			String cats = p.byCategory.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(3)
					.map(c -> c.getKey() + " " + Math.round(c.getValue() * 10) / 10.0).reduce((a, b) -> a + ", " + b).orElse("");
			md.append(String.format(Locale.ROOT, "%d. **%s** + **%s** score %.1f (%s); e.g. `%s`; owner **%s**%n", rank, ab[0], ab[1], p.score, cats,
					esc((String) p.top.get(0)[1]), pairOwner(p).repo()));
		}

		md.append("\n## Top 30 mods\n\n");
		List<Map.Entry<String, double[]>> ms = new ArrayList<>(s.modScore().entrySet());
		ms.removeIf(e -> e.getKey().equals("minecraft"));
		ms.sort(Comparator.comparingDouble((Map.Entry<String, double[]> e) -> -e.getValue()[0]).thenComparing(Map.Entry::getKey));
		Map<String, NodeOut> hottest = new HashMap<>();
		for (NodeOut n : s.nodes()) for (String m : n.mods()) hottest.putIfAbsent(m, n);
		rank = 0;
		for (Map.Entry<String, double[]> e : ms) {
			if (++rank > 30) break;
			ModInfo i = infos.get(e.getKey());
			NodeOut h = hottest.get(e.getKey());
			md.append(String.format(Locale.ROOT, "%d. **%s** (%s%s) score %.1f over %d nodes; hottest `%s` heat %.1f%n", rank, e.getKey(), i == null ? "?" : i.kind(),
					i != null && i.library() ? ", library" : "", e.getValue()[0], (int) e.getValue()[1], h == null ? "" : esc(h.key()), h == null ? 0 : h.heat()));
		}

		md.append("\n## Weapons and tools no mms mod references\n\n");
		md.append("Items in the weapon and tool domain (tag, weapon_attributes file or name) that `mms_weapons`, `mms_combat` and `mms_arsenal` never mention in data, assets, config files or string constants, grouped by source mod. `new` = not in the mod's pre-update (.jar.disabled) jar.\n\n");
		if (coverage.isEmpty()) md.append("None.\n");
		int shown = 0;
		for (Map<String, Object> m : coverage) {
			if (++shown > 40) {
				md.append("- ... ").append(coverage.size() - 40).append(" more mods in the JSON\n");
				break;
			}
			List<?> un = (List<?>) m.get("uncovered");
			md.append(String.format(Locale.ROOT, "- **%s** -> %s: %d of %d weapon items uncovered: ", m.get("mod"), m.get("repo"), un.size(), m.get("weaponItems")));
			List<String> names = new ArrayList<>();
			for (Object o : un.subList(0, Math.min(8, un.size()))) {
				Map<?, ?> u = (Map<?, ?>) o;
				names.add("`" + u.get("item") + "`" + (Boolean.TRUE.equals(u.get("newSinceOld")) ? " (new)" : ""));
			}
			md.append(String.join(", ", names)).append(un.size() > 8 ? ", ..." : "").append('\n');
		}

		md.append("\n## Changed by update\n\n");
		md.append("Mods with a 1.21.11 `.jar.disabled` beside the live jar: content ids and mixin targets (in 26.3 names) that differ.\n\n");
		if (updates.isEmpty()) md.append("None.\n");
		for (Map<String, Object> u : updates) {
			Map<?, ?> a = (Map<?, ?>) u.get("added");
			Map<?, ?> r = (Map<?, ?>) u.get("removed");
			Map<?, ?> mt = (Map<?, ?>) u.get("mixinTargets");
			int n = 0;
			for (Object o : a.values()) n += ((List<?>) o).size();
			for (Object o : r.values()) n += ((List<?>) o).size();
			if (n == 0 && ((Integer) mt.get("addedCount")) + ((Integer) mt.get("removedCount")) == 0) continue;
			md.append(String.format(Locale.ROOT, "- **%s**: +%d/-%d items, +%d/-%d blocks, +%d/-%d entities, +%d/-%d enchantments, mixin targets +%s/-%s", u.get("mod"),
					((List<?>) a.get("items")).size(), ((List<?>) r.get("items")).size(), ((List<?>) a.get("blocks")).size(), ((List<?>) r.get("blocks")).size(),
					((List<?>) a.get("entities")).size(), ((List<?>) r.get("entities")).size(), ((List<?>) a.get("enchantments")).size(), ((List<?>) r.get("enchantments")).size(),
					mt.get("addedCount"), mt.get("removedCount")));
			List<?> ai = (List<?>) a.get("items");
			if (!ai.isEmpty()) md.append("; new items ").append(ai.subList(0, Math.min(5, ai.size())));
			md.append('\n');
		}

		md.append("\n## Not done, approximated\n\n");
		md.append("- TODO: default keybinds are not scanned (v1).\n");
		md.append("- A mixin class counts when a mixin config of the mod lists it; the config `priority` is used when the annotation has none.\n");
		md.append("- Whether Uproot has a rule for a node is judged by the 26.3 class (class nodes) or class and member name (other nodes) being named by a rule in `renames*.txt` or `dead-hooks.txt` (a rule on a superclass is not followed).\n");
		md.append("- Bare mixin method names resolve to a descriptor only when the 26.3 class declares exactly one method of the name.\n");
		md.append("- Biome and loot modifiers are read only where they are data (forge, neoforge folders); Fabric code-side modifications are not visible.\n");
		md.append("- A mod whose items are only named in lang or item models is counted by those ids; item tags are read from the mod's own jar.\n");
		md.append(String.format(Locale.ROOT, "- %d notes: jars or files that could not be read are in `uproot-report.txt` and below.%n", notes.size()));
		List<String> real = notes.stream().filter(n -> !n.contains("already scanned from") && !n.contains("which the scan prefers, skipped")).toList();
		md.append(String.format(Locale.ROOT, "- %d of them are only duplicate mod ids skipped; the others:%n", notes.size() - real.size()));
		for (String note : real.stream().limit(60).toList()) md.append("  - ").append(esc(note)).append('\n');
		return md.toString();
	}
}
