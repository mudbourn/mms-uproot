package info.mudbourn.mmsuproot.remap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.mudbourn.mmsuproot.boot.Report;
import info.mudbourn.mmsuproot.map.GameClasses;
import info.mudbourn.mmsuproot.map.NameTable;
import info.mudbourn.mmsuproot.map.Renames;
import info.mudbourn.mmsuproot.util.Json;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Turns intermediary names into 26.3 names for one mod: intermediary to 1.21.11 Mojang through the
 * {@link NameTable}, then through the rename table, then to whichever class kept its simple name
 * but moved. What it cannot map it leaves alone and records. Owner-specific member renames follow
 * the class hierarchy, so an override in a mod class or a call through a subclass is caught too.
 */
public final class Namer {
	private static final Pattern SLASH_CLASS = Pattern.compile("net/minecraft/[\\w/$]*\\w");
	private static final Pattern DOT_CLASS = Pattern.compile("net\\.minecraft\\.[\\w.$]*\\w");
	private static final Pattern MEMBER = Pattern.compile("\\b(method|field)_\\d+\\b");
	private static final Pattern DESC_CLASS = Pattern.compile("L([^;<]+)");
	/** {@code Lowner;name(} or {@code Lowner;name:}, the member reference form Mixin reads. */
	private static final Pattern MEMBER_REF = Pattern.compile("(?<![\\w/$])L([\\w/$]+);([\\w$<>]+)(?=[(:])");
	/** A field reference after class renames: {@code Lowner;NAME:desc}. */
	private static final Pattern FIELD_REF = Pattern.compile("(?<![\\w/$])L([\\w/$]+);(\\w+):(\\[*(?:L[\\w/$]+;|[BCDFIJSZ]))");
	private static final Pattern BARE = Pattern.compile("([\\w$<>]+)(\\(.*|:.*)?");

	private final NameTable table;
	private final Renames renames;
	private final GameClasses game;
	private final Report.Entry report;
	private final ModClasses mods;
	private final Map<String, String> classCache = new ConcurrentHashMap<>();
	private final Map<String, Set<String>> ancestorCache = new ConcurrentHashMap<>();
	private final Map<String, List<String>> headerCache = new ConcurrentHashMap<>();

	public Namer(NameTable table, Renames renames, GameClasses game, ModClasses mods, Report.Entry report) {
		this.table = table;
		this.renames = renames;
		this.game = game;
		this.mods = mods;
		this.report = report;
	}

	/**
	 * Notes a call or field read of a Minecraft class that no member of 26.3 answers. Runs on the final
	 * owner and descriptor, so what is listed is what would fail at run time with NoSuchMethodError or
	 * NoSuchFieldError once the line executes.
	 */
	public void audit(String user, int opcode, String owner, String name, String desc, boolean field) {
		if (!game.has(owner) || owner.indexOf('[') >= 0) return;
		boolean found = field ? game.findField(owner, name, desc) : game.findMethod(owner, name, desc);
		if (!found && !field && game.findMethod("java/lang/Object", name, desc)) found = true;
		if (found && field && game.isPrivateField(owner, name, desc)) {
			report.unresolved.add(owner + "." + name + ":" + desc + " (private)  <- " + user);
			return;
		}
		if (!found) report.unresolved.add(owner + "." + name + (field ? ":" : "") + desc + "  <- " + user);
	}

	/** Adds a line to the mod's report notes, once. */
	public void note(String text) {
		if (!report.notes.contains(text)) report.notes.add(text);
	}

	public ModClasses mods() {
		return mods;
	}

	public GameClasses game() {
		return game;
	}

	public Renames renames() {
		return renames;
	}

	/** Intermediary (or already Mojang) internal name to the 26.3 internal name. */
	public String cls(String name) {
		return classCache.computeIfAbsent(name, this::computeClass);
	}

	private String computeClass(String name) {
		if (mods.has(name)) return name; // a target mod's own class, whatever package rule would match it
		String mojang = mojang(name);
		if (mods.has(mojang)) return mojang;
		String result = renames.cls(mojang);
		if (result.equals(mojang) && mojang.startsWith("net/minecraft/") && game.indexed()) {
			int dollar = mojang.indexOf('$');
			String outer = dollar < 0 ? mojang : mojang.substring(0, dollar);
			if (!game.has(outer)) {
				String found = game.uniqueBySimpleName(outer.substring(outer.lastIndexOf('/') + 1));
				if (found != null) {
					result = found + mojang.substring(outer.length());
					report.moved.put(mojang, result);
				}
			}
		}
		return result;
	}

	private String mojang(String name) {
		String m = table.classes.get(name);
		if (m != null) return m;
		int dollar = name.indexOf('$');
		if (dollar > 0) {
			String outer = table.classes.get(name.substring(0, dollar));
			if (outer != null) {
				String suffix = name.substring(dollar);
				if (suffix.contains("class_")) report.unmapped.add(name);
				return outer + suffix;
			}
		}
		if (name.startsWith("net/minecraft/class_")) report.unmapped.add(name);
		return name;
	}

	/**
	 * Member name for a method. {@code owner} is the intermediary owner, or null when unknown;
	 * {@code desc} the intermediary descriptor, or null when unknown.
	 */
	public String method(String owner, String name, String desc) {
		if (name.startsWith("method_") || name.startsWith("comp_")) { // comp_: a record component's accessor
			String m = table.methods.get(name);
			if (m == null) {
				report.unmapped.add(name);
				return name;
			}
			name = m;
		} else if (name.indexOf("method_") > 0) {
			name = replaceMembers(name);
		}
		if (!renames.hasMethod(name)) return name;
		return renames.method(owner == null ? null : ancestors(owner), name, argCount(desc), desc == null ? null : mojangDesc(desc));
	}

	public String field(String owner, String name) {
		if (name.startsWith("field_") || name.startsWith("comp_")) { // comp_: a record component's field
			String f = table.fields.get(name);
			if (f == null) {
				report.unmapped.add(name);
				return name;
			}
			name = f;
		}
		if (!renames.hasField(name)) return name;
		return renames.field(owner == null ? null : ancestors(owner), name);
	}

	private static int argCount(String desc) {
		if (desc == null || !desc.startsWith("(")) return -1;
		try {
			return Type.getArgumentTypes(desc).length;
		} catch (RuntimeException e) {
			return -1;
		}
	}

	/** A descriptor with classes in 1.21.11 Mojang names, which is how rename rules write them. */
	private String mojangDesc(String desc) {
		Matcher m = DESC_CLASS.matcher(desc);
		StringBuilder sb = new StringBuilder();
		while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement("L" + mojang(m.group(1))));
		return m.appendTail(sb).toString();
	}

	/**
	 * The 26.3 names of a class and everything above it: superclasses, interfaces and, for a mixin
	 * class, the classes it targets. Minecraft classes come from {@link GameClasses}, mod classes from
	 * their own headers.
	 */
	private Set<String> ancestors(String owner) {
		Set<String> cached = ancestorCache.get(owner);
		if (cached != null) return cached;
		Set<String> out = new LinkedHashSet<>();
		collect(cls(owner), out);
		ancestorCache.put(owner, out);
		return out;
	}

	/** The 26.3 names of a class (given in 26.3 names) and everything above it. */
	public Set<String> ancestorsOf(String name26) {
		Set<String> out = new LinkedHashSet<>();
		collect(name26, out);
		return out;
	}

	private void collect(String name, Set<String> out) {
		if (name == null || !out.add(name)) return;
		if (mods.has(name)) {
			for (String parent : header(name)) collect(cls(parent), out);
			return;
		}
		game.info(name).ifPresent(i -> {
			collect(i.superName(), out);
			for (String itf : i.interfaces()) collect(itf, out);
		});
	}

	/** Super class, interfaces and {@code @Mixin} targets of a mod class, in the mod's own names. */
	private List<String> header(String name) {
		List<String> cached = headerCache.get(name);
		if (cached != null) return cached;
		List<String> parents = new ArrayList<>();
		Path p = mods.path(name);
		if (p != null) {
			try {
				ClassReader reader = new ClassReader(Files.readAllBytes(p));
				if (reader.getSuperName() != null) parents.add(reader.getSuperName());
				parents.addAll(List.of(reader.getInterfaces()));
				reader.accept(new ClassVisitor(Opcodes.ASM9) {
					@Override
					public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
						if (!desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
						return new AnnotationVisitor(Opcodes.ASM9) {
							@Override
							public AnnotationVisitor visitArray(String key) {
								return new AnnotationVisitor(Opcodes.ASM9) {
									@Override
									public void visit(String k, Object value) {
										if (value instanceof Type t && t.getSort() == Type.OBJECT) parents.add(t.getInternalName());
										else if (value instanceof String s && "targets".equals(key)) parents.add(s.replace('.', '/'));
									}
								};
							}
						};
					}
				}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			} catch (IOException | RuntimeException e) {
				report.notes.add("could not read the header of " + name + ": " + e);
			}
		}
		headerCache.put(name, parents);
		return parents;
	}

	/**
	 * The class that holds a static constant now: the owner itself, or the class the rename table
	 * says took over its constants when the owner no longer has the field. Names are 26.3 names.
	 */
	public String movedOwner(String owner, String name, String desc) {
		String target = renames.fieldOwner(owner);
		if (target == null || !game.indexed()) return owner;
		if (!game.findField(owner, name, desc) && game.findField(target, name, desc)) return target;
		return owner;
	}

	private String replaceMembers(String s) {
		Matcher m = MEMBER.matcher(s);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String token = m.group();
			String mapped = (m.group(1).equals("method") ? table.methods : table.fields).get(token);
			if (mapped == null) report.unmapped.add(token);
			m.appendReplacement(sb, Matcher.quoteReplacement(mapped != null ? mapped : token));
		}
		return m.appendTail(sb).toString();
	}

	/** Maps every class named in a descriptor or signature. */
	public String desc(String descriptor) {
		Matcher m = DESC_CLASS.matcher(descriptor);
		StringBuilder sb = new StringBuilder();
		while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement("L" + cls(m.group(1))));
		return m.appendTail(sb).toString();
	}

	/** Rewrites the names inside a string: mixin targets, annotation values, descriptors. */
	public String rewrite(String s) {
		return rewrite(s, null, false);
	}

	/**
	 * As {@link #rewrite(String)}, and a bare member name such as {@code render} or {@code name(desc)}
	 * is renamed with {@code mixinClass} as its owner, which follows the class up to its mixin targets.
	 * {@code field} says whether a bare name with no parenthesis is a field.
	 */
	public String rewrite(String s, String mixinClass, boolean field) {
		if (!(s.contains("net/minecraft") || s.contains("net.minecraft") || s.contains("method_") || s.contains("field_")
				|| mixinClass != null && (renames.hasMethod(bareName(s)) || renames.hasField(bareName(s))))) return s;

		if (mixinClass != null) {
			Matcher b = BARE.matcher(s);
			if (b.matches()) {
				String rest = b.group(2) == null ? "" : b.group(2);
				boolean isField = rest.startsWith(":") || rest.isEmpty() && field;
				String name = isField ? field(mixinClass, b.group(1)) : method(mixinClass, b.group(1), rest.isEmpty() ? null : rest);
				s = name + rest;
			}
		}

		Matcher m = MEMBER_REF.matcher(s);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String rest = s.substring(m.end());
			String name = rest.startsWith(":") ? field(m.group(1), m.group(2)) : method(m.group(1), m.group(2), rest);
			m.appendReplacement(sb, Matcher.quoteReplacement("L" + m.group(1) + ";" + name));
		}
		s = m.appendTail(sb).toString();

		m = SLASH_CLASS.matcher(s);
		sb = new StringBuilder();
		while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(cls(m.group())));
		s = m.appendTail(sb).toString();

		m = DOT_CLASS.matcher(s);
		sb = new StringBuilder();
		while (m.find()) {
			String slash = m.group().replace('.', '/');
			m.appendReplacement(sb, Matcher.quoteReplacement(cls(slash).replace('/', '.')));
		}
		s = m.appendTail(sb).toString();

		s = replaceMembers(s);

		m = FIELD_REF.matcher(s);
		sb = new StringBuilder();
		while (m.find()) {
			String owner = movedOwner(m.group(1), m.group(2), m.group(3));
			m.appendReplacement(sb, Matcher.quoteReplacement("L" + owner + ";" + m.group(2) + ":" + m.group(3)));
		}
		s = m.appendTail(sb).toString();
		for (Map.Entry<String, String> at : renames.injectAts().entrySet()) {
			if (s.contains(at.getKey())) s = s.replace(at.getKey(), at.getValue());
		}
		return s;
	}

	private static String bareName(String s) {
		Matcher b = BARE.matcher(s);
		return b.matches() ? b.group(1) : "";
	}

	/**
	 * Rewrites a refmap: every value goes through {@link #rewrite(String, String, boolean)} with its
	 * mixin class as the owner for bare names. Falls back to plain text when it is not the expected JSON.
	 */
	@SuppressWarnings("unchecked")
	public String rewriteRefmap(String text) {
		try {
			Map<String, Object> root = Json.parseObject(text);
			if (root.get("mappings") instanceof Map<?, ?> mappings) rewriteEntries((Map<String, Object>) mappings);
			// "data" maps a namespace to the same shape as "mappings"
			if (root.get("data") instanceof Map<?, ?> data) {
				for (Object ns : data.values()) if (ns instanceof Map<?, ?> nsMap) rewriteEntries((Map<String, Object>) nsMap);
			}
			return Json.write(root);
		} catch (RuntimeException e) {
			report.notes.add("refmap could not be parsed as expected, rewritten as plain text: " + e);
			return rewrite(text);
		}
	}

	@SuppressWarnings("unchecked")
	private void rewriteEntries(Map<String, Object> classes) {
		for (Map.Entry<String, Object> c : classes.entrySet()) {
			if (!(c.getValue() instanceof Map<?, ?> entries)) continue;
			String mixinClass = c.getKey().replace('.', '/');
			for (Map.Entry<String, Object> e : ((Map<String, Object>) entries).entrySet()) {
				if (e.getValue() instanceof String v) e.setValue(rewrite(v, mixinClass, false));
			}
		}
	}
}
