package info.mudbourn.mmsuproot.remap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Member clashes: a mod class that would have two methods (or two fields) with the same name and descriptor once its names are 26.3's.
 *
 * <p>Intermediary names and the mod's own names live in one namespace in the class file, and the two do not meet there: a mod's own
 * {@code setChargingCrossbow(boolean)} and the interface method {@code method_7110(boolean)} it implements are two members. Mojang names are
 * what the mod's own names are often written in, and {@code method_7110} becomes {@code setChargingCrossbow}, so the class gets two of the
 * same name and the JVM refuses it ({@code ClassFormatError: duplicate method}). The same happens when two descriptors that differ only in
 * classes that 26.3 merged, or two fields, map to one.
 *
 * <p>Every clash is resolved the same way, from what the class file itself says about the two members:
 * <ul>
 * <li><b>Merge</b> when the later of them is a compiler bridge (synthetic, bridge flag) of the other: it can only forward to the other,
 * so it is dropped and its callers reach the survivor.</li>
 * <li><b>Rename</b> otherwise. The members had different names in the mod as built, so its author wrote two methods; the one whose name
 * Uproot did not change (the mod's own) keeps being a separate method, and the one the remap named after a game member keeps the
 * name, since the game's calls and overrides go to that name. The mod's member gets the suffix {@value #SUFFIX}, in its declaration, in every
 * call, field access or method reference to it in the mod, and in every mod subclass that overrides it, so nothing it was called from
 * changes meaning: a call that reached the mod's method still does, and a call of the game's method still reaches that.
 * When both are the mod's own (descriptors that merged), the first keeps the name; when both come from game names, the first does.</li>
 * </ul>
 * Merging two members that both have bodies is never done: that would be choosing which code runs.
 *
 * <p><b>Override families.</b> A method is only renamed together with every method that overrides or implements it, or that it overrides or
 * implements, in the mod classes: if a mod interface {@code I} or a mod superclass {@code A} declares {@code foo(Z)V}, mod class {@code B}
 * implements it and {@code B.foo} is the clashing member, then {@code I.foo}, {@code A.foo} and the {@code foo(Z)V} of every other mod class
 * tied to them (through a common subclass too) get the same new name, or dispatch would stop at the renamed one. The family is found in the
 * classes of every scanned mod ({@link ModClasses} is pack-wide), so it does not depend on which class is remapped first. A family is not
 * renamed, and the clash is reported as unresolved (a real gap, which leaves the class unloadable), when it cannot be done consistently:
 * a class of the family cannot be read, or the members of the family are named differently by the remap (a rule that applies to one
 * class only), so no single new name would fit. Overrides that pass through a game class are not followed (nothing in a game class can be
 * renamed).
 *
 * <p><b>Strings.</b> A rename is followed wherever the mod names the member in a form the remap reads: a mixin's {@code @Shadow} declaration and its
 * uses (they are members of the mixin class, which is tied to its target), {@code @Invoker} and {@code @Accessor} (with the name taken from the
 * method name when the annotation gives none), {@code method=} and {@code target=} strings with or without a descriptor, refmaps, and the
 * method of a lambda or method reference. A bare {@code method="foo"} string follows when every {@code foo} of the target is renamed to the
 * same name, and is left as it is, with a note, when the target has both a renamed and a plain {@code foo}. Reflection that looks a member up by
 * name ({@code Class.getMethod("foo")}) cannot be followed: the name is a string the remap cannot tie to a member, and it keeps reaching the
 * member that kept the name.
 *
 * <p>The analysis of a class reads only that class's header (members, no code). A lookup walks the mod's own classes above the owner, since a
 * call or an override of a renamed member names a subclass or overrides it down the hierarchy.
 */
public final class Clashes {
	/** What a renamed member gets after its name. */
	static final String SUFFIX = "$clash";

	/** One clash, for the scan and the report. {@code unresolved} says the clash could not be resolved, so the class stays broken. */
	public record Clash(String modClass, String kind, String member, String resolution, boolean unresolved) { }

	/** A method that has to be renamed in its class: the name the remap gave it, and why it is the one that is renamed. */
	private record Seed(String base, String reason) { }

	/** The result for one class: method keys (original name, a tab, original descriptor) that clash and field keys to their new names, and the bridges to drop. */
	private record ClassClashes(Map<String, Seed> seeds, Map<String, String> fields, Set<String> dropped) {
		static final ClassClashes NONE = new ClassClashes(Map.of(), Map.of(), Set.of());
	}

	private record Member(int access, String name, String desc) { }

	private final Namer namer;
	private final ModClasses mods;
	private final Map<String, ClassClashes> classes = new ConcurrentHashMap<>();
	private final Map<String, String> lookups = new ConcurrentHashMap<>();
	private final Map<String, String> families = new ConcurrentHashMap<>();
	private final Set<String> assigned = new HashSet<>(); // class, tab, new name, tab and descriptor of every name a family took
	private final Map<String, Clash> found = new ConcurrentSkipListMap<>();

	Clashes(Namer namer) {
		this.namer = namer;
		this.mods = namer.mods();
	}

	/** Every clash found in the classes asked about so far, by class and member. */
	Map<String, Clash> found() {
		return found;
	}

	/** The name of the method {@code owner.name desc} (original names) after clashes, given the name {@code mapped} the remap gave it. */
	String method(String owner, String name, String desc, String mapped) {
		if (owner == null || desc == null || !mods.has(owner)) return mapped;
		String d = methodDesc(desc);
		if (d == null) return mapped;
		String key = owner + '\t' + name + '\t' + d;
		String cached = lookups.get(key);
		if (cached == null) {
			String declarer = declarer(owner, name + '\t' + d);
			String renamed = declarer == null ? "" : family(declarer, name, d);
			cached = renamed;
			lookups.put(key, cached);
		}
		return cached.isEmpty() ? mapped : cached;
	}

	/**
	 * As {@link #method} for a reference that names no descriptor ({@code method="foo"} of a mixin). When every method of that name in the class
	 * and above it has the same answer, that is it; when some are renamed and some are not, the reference would have meant both, and it keeps
	 * the name with a note.
	 */
	String methodByName(String owner, String name, String mapped) {
		if (owner == null || !mods.has(owner)) return mapped;
		Set<String> answers = new HashSet<>();
		Set<String> seen = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>();
		queue.add(owner);
		while (!queue.isEmpty()) {
			String c = queue.poll();
			if (!mods.has(c) || !seen.add(c)) continue;
			Map<String, Integer> declared = mods.methods(c);
			if (declared != null) {
				for (String key : declared.keySet()) {
					if (!key.startsWith(name + '\t')) continue;
					answers.add(method(c, name, key.substring(name.length() + 1), mapped));
				}
			}
			queue.addAll(mods.parents(c));
		}
		if (answers.size() <= 1) return answers.isEmpty() ? mapped : answers.iterator().next();
		namer.note("a reference to " + name + " by name alone in " + owner.replace('/', '.') + " could mean a renamed member (see member clashes) or a plain one of the same name, and keeps the name");
		return mapped;
	}

	/** As {@link #method} for a field. */
	String field(String owner, String name, String desc, String mapped) {
		if (owner == null || desc == null || !mods.has(owner)) return mapped;
		String d = typeDesc(desc);
		if (d == null) return mapped;
		String key = "f\t" + owner + '\t' + name + '\t' + d;
		String cached = lookups.get(key);
		if (cached == null) {
			String renamed = walk(owner, name + '\t' + d);
			cached = renamed == null ? "" : renamed;
			lookups.put(key, cached);
		}
		return cached.isEmpty() ? mapped : cached;
	}

	/** Whether the compiler bridge {@code name desc} of the mod class was merged into the method it forwards to, and so is not written. */
	boolean dropped(String modClass, String name, String desc) {
		return mods.has(modClass) && of(modClass).dropped.contains(name + '\t' + desc);
	}

	/** The method descriptor at the start of {@code s} (a reference may carry more after it), or null. */
	static String methodDesc(String s) {
		if (s.isEmpty() || s.charAt(0) != '(') return null;
		int close = s.indexOf(')');
		if (close < 0) return null;
		String ret = typeDesc(s.substring(close + 1));
		return ret == null ? null : s.substring(0, close + 1) + ret;
	}

	/** The field or return type descriptor at the start of {@code s}, or null. */
	static String typeDesc(String s) {
		int i = 0;
		while (i < s.length() && s.charAt(i) == '[') i++;
		if (i >= s.length()) return null;
		char c = s.charAt(i);
		if (c == 'L') {
			int end = s.indexOf(';', i);
			return end < 0 ? null : s.substring(0, end + 1);
		}
		return "BCDFIJSZV".indexOf(c) >= 0 ? s.substring(0, i + 1) : null;
	}

	/** The first class on the way up from {@code owner} (mod classes only) that declares the method {@code key}, or null. */
	private String declarer(String owner, String key) {
		Set<String> seen = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>();
		queue.add(owner);
		while (!queue.isEmpty()) {
			String c = queue.poll();
			if (!mods.has(c) || !seen.add(c)) continue;
			Map<String, Integer> declared = mods.methods(c);
			if (declared != null && declared.containsKey(key)) return c;
			queue.addAll(mods.parents(c));
		}
		return null;
	}

	/** Whether a method with these access flags takes part in overriding. */
	private static boolean overridable(String key, int access) {
		return (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0 && !key.startsWith("<");
	}

	/**
	 * Whether the declarations of the method {@code key} in a class and in one of its parents or children are tied: an override or implementation
	 * (neither is private or static), or a mixin and its target (a mixin's member is merged into the target whatever its access, so a
	 * {@code @Shadow} can name a private method). {@code null} access stands for a class that does not declare it.
	 */
	private boolean tied(String key, String a, Integer accessA, String b, Integer accessB) {
		if (mods.isMixin(a) || mods.isMixin(b)) return true;
		return (accessA == null || overridable(key, accessA)) && (accessB == null || overridable(key, accessB));
	}

	/** The classes that declare the method {@code key} and are the nearest to {@code from} on the way up, past the classes that do not. */
	private Set<String> nearestAbove(String from, Integer fromAccess, String key, boolean[] ok) {
		Set<String> out = new LinkedHashSet<>();
		Set<String> seen = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>(mods.parents(from));
		while (!queue.isEmpty()) {
			String c = queue.poll();
			if (!mods.has(c) || !seen.add(c)) continue;
			Map<String, Integer> declared = mods.methods(c);
			if (declared == null) {
				ok[0] = false;
				continue;
			}
			Integer access = declared.get(key);
			if (access != null && tied(key, from, fromAccess, c, access)) out.add(c);
			else queue.addAll(mods.parents(c));
		}
		return out;
	}

	/**
	 * The new name of the method {@code name desc} declared by {@code start}, or "" when its override family has no clash. The family is every mod
	 * class that declares the method and is tied to {@code start} by overriding or by being a mixin and its target: above it, below it, and the
	 * classes a common subclass or a class that inherits one of them into an interface ties together. Every class of the family gets the
	 * name, so the answer is the same whichever of them asks.
	 */
	private synchronized String family(String start, String name, String desc) {
		String key = name + '\t' + desc;
		String cached = families.get(start + '\t' + key);
		if (cached != null) return cached;
		boolean[] ok = {true};
		Set<String> family = new LinkedHashSet<>();
		Deque<String> work = new ArrayDeque<>();
		family.add(start);
		work.add(start);
		while (!work.isEmpty() && ok[0]) {
			String c = work.poll();
			Map<String, Integer> mine = mods.methods(c);
			if (mine == null) {
				ok[0] = false;
				break;
			}
			Integer myAccess = mine.get(key);
			for (String up : nearestAbove(c, myAccess, key, ok)) if (family.add(up)) work.add(up);
			Set<String> seen = new HashSet<>();
			Deque<String> down = new ArrayDeque<>(mods.children(c));
			while (!down.isEmpty()) {
				String ch = down.poll();
				if (!seen.add(ch)) continue;
				Map<String, Integer> declared = mods.methods(ch);
				if (declared == null) {
					ok[0] = false;
					break;
				}
				Integer access = declared.get(key);
				if (access != null) {
					if (tied(key, c, myAccess, ch, access) && family.add(ch)) work.add(ch);
					continue;
				}
				// a class that only inherits the method ties together the declarations it inherits from different parents
				for (String up : nearestAbove(ch, null, key, ok)) if (family.add(up)) work.add(up);
				down.addAll(mods.children(ch));
			}
		}
		String result = "";
		if (ok[0]) {
			result = decide(family, name, desc);
		} else {
			// the family cannot be known whole, so a clash in the part that is known is left as it is
			List<String> clashing = family.stream().filter(c -> of(c).seeds.containsKey(key)).toList();
			if (!clashing.isEmpty()) unresolved(clashing, name, desc, "a class of its override family cannot be read");
		}
		for (String c : family) families.put(c + '\t' + key, result);
		families.put(start + '\t' + key, result);
		return result;
	}

	/** The name the family takes, "" when none of its members clashes or no consistent name exists (reported). */
	private String decide(Set<String> family, String name, String desc) {
		String key = name + '\t' + desc;
		Map<String, Seed> seeds = new LinkedHashMap<>();
		for (String c : family) {
			Seed seed = of(c).seeds.get(key);
			if (seed != null) seeds.put(c, seed);
		}
		if (seeds.isEmpty()) return "";
		Set<String> mapped = new HashSet<>();
		for (String c : family) mapped.add(namer.baseMethod(c, name, desc));
		if (mapped.size() > 1) {
			unresolved(seeds.keySet(), name, desc, "the override family of " + family.size() + " classes has the method under different names after the remap (" + String.join(", ", mapped.stream().sorted().toList()) + "), so no one new name fits");
			return "";
		}
		String base = mapped.iterator().next();
		String newDesc = namer.desc(desc);
		String renamed = base + SUFFIX;
		int n = 2;
		while (taken(family, renamed + '\t' + newDesc)) renamed = base + SUFFIX + n++;
		for (String c : family) assigned.add(c + '\t' + renamed + '\t' + newDesc);
		for (Map.Entry<String, Seed> e : seeds.entrySet()) {
			record(e.getKey(), "method", e.getValue().base + newDesc, false, "renamed " + name + " to " + renamed + e.getValue().reason
				+ (family.size() > 1 ? "; the rename follows its override family of " + family.size() + " classes: " + describe(family) : ""));
		}
		return renamed;
	}

	private static String describe(Set<String> family) {
		List<String> names = family.stream().map(c -> c.substring(c.lastIndexOf('/') + 1)).sorted().limit(6).toList();
		return String.join(", ", names) + (family.size() > names.size() ? ", ..." : "");
	}

	/** Whether {@code name tab desc} (in 26.3 names) is already the name of some method in a class of the family, or taken by another family's rename. */
	private boolean taken(Set<String> family, String nameAndDesc) {
		for (String c : family) {
			if (assigned.contains(c + '\t' + nameAndDesc)) return true;
			Map<String, Integer> declared = mods.methods(c);
			if (declared == null) return true;
			for (String key : declared.keySet()) {
				int tab = key.indexOf('\t');
				String desc = key.substring(tab + 1);
				if ((namer.baseMethod(c, key.substring(0, tab), desc) + '\t' + namer.desc(desc)).equals(nameAndDesc)) return true;
			}
		}
		return false;
	}

	private void unresolved(java.util.Collection<String> classes, String name, String desc, String why) {
		for (String c : classes) {
			String member = namer.baseMethod(c, name, desc) + namer.desc(desc);
			record(c, "method", member, true, "UNRESOLVED, left as it is: " + why);
			namer.unresolved(c.replace('/', '.') + "." + name + desc + "  <- member clash: " + why);
		}
	}

	/** The new name from the first class on the way up from {@code owner} (mod classes only) that renamed the field, or null. */
	private String walk(String owner, String key) {
		Set<String> seen = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>();
		queue.add(owner);
		while (!queue.isEmpty()) {
			String c = queue.poll();
			if (!mods.has(c) || !seen.add(c)) continue;
			String renamed = of(c).fields.get(key);
			if (renamed != null) return renamed;
			queue.addAll(mods.parents(c));
		}
		return null;
	}

	private ClassClashes of(String modClass) {
		ClassClashes cached = classes.get(modClass);
		if (cached != null) return cached;
		ClassClashes computed = analyse(modClass);
		ClassClashes raced = classes.putIfAbsent(modClass, computed);
		return raced != null ? raced : computed;
	}

	private ClassClashes analyse(String modClass) {
		Path path = mods.path(modClass);
		if (path == null) return ClassClashes.NONE;
		List<Member> methods = new ArrayList<>();
		List<Member> fields = new ArrayList<>();
		try {
			new ClassReader(Files.readAllBytes(path)).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
					methods.add(new Member(access, name, descriptor));
					return null;
				}

				@Override
				public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
					fields.add(new Member(access, name, descriptor));
					return null;
				}
			}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		} catch (IOException | RuntimeException e) {
			namer.note("could not read the members of " + modClass + " for clash detection: " + e);
			return ClassClashes.NONE;
		}
		Map<String, Seed> seeds = new LinkedHashMap<>();
		Map<String, String> fieldRenames = new LinkedHashMap<>();
		Set<String> dropped = new HashSet<>();
		resolve(modClass, "method", methods, seeds, null, dropped);
		resolve(modClass, "field", fields, null, fieldRenames, dropped);
		if (seeds.isEmpty() && fieldRenames.isEmpty() && dropped.isEmpty()) return ClassClashes.NONE;
		return new ClassClashes(seeds, fieldRenames, dropped);
	}

	/**
	 * Finds the members of the class that share a name and descriptor once named. A method that has to be renamed becomes a seed (the name it
	 * gets is decided for its whole override family, see {@link #family}); a field gets its new name here.
	 */
	private void resolve(String modClass, String kind, List<Member> members, Map<String, Seed> seeds, Map<String, String> fieldRenames, Set<String> dropped) {
		boolean method = kind.equals("method");
		Map<String, List<Member>> groups = new LinkedHashMap<>();
		Map<Member, String> mappedName = new HashMap<>();
		for (Member m : members) {
			String name = method ? namer.baseMethod(modClass, m.name, m.desc) : namer.baseField(modClass, m.name);
			mappedName.put(m, name);
			groups.computeIfAbsent(name + '\t' + namer.desc(m.desc), k -> new ArrayList<>()).add(m);
		}
		Set<String> taken = new HashSet<>(groups.keySet());
		for (Map.Entry<String, List<Member>> g : groups.entrySet()) {
			List<Member> same = g.getValue();
			if (same.size() < 2) continue;
			String newDesc = g.getKey().substring(g.getKey().indexOf('\t') + 1);
			// a bridge is merged into the method it forwards to
			List<Member> real = new ArrayList<>();
			boolean anyReal = method && same.stream().anyMatch(m -> !bridge(m));
			for (Member m : same) {
				if (anyReal && bridge(m)) {
					dropped.add(m.name + '\t' + m.desc);
					record(modClass, kind, mappedName.get(m) + newDesc, false, "merged: the compiler bridge " + m.name + m.desc + " forwards to the other member and is not written");
				} else {
					real.add(m);
				}
			}
			if (real.size() < 2) continue;
			Member keeper = real.stream().filter(m -> !own(m, mappedName.get(m))).findFirst().orElse(real.get(0));
			for (Member m : real) {
				if (m == keeper) continue;
				String base = mappedName.get(m);
				String reason = own(m, base)
					? ": the mod's own member, which was a separate " + kind + " from " + (own(keeper, mappedName.get(keeper)) ? "the other" : keeper.name) + " in the mod as built"
					: ": both names map to this one, the first keeps it";
				if (method) {
					seeds.put(m.name + '\t' + m.desc, new Seed(base, reason));
					continue;
				}
				String renamed = base + SUFFIX;
				int n = 2;
				while (taken.contains(renamed + '\t' + newDesc)) renamed = base + SUFFIX + n++;
				taken.add(renamed + '\t' + newDesc);
				fieldRenames.put(m.name + '\t' + m.desc, renamed);
				record(modClass, kind, base + newDesc, false, "renamed " + m.name + " to " + renamed + reason);
			}
		}
	}

	private static boolean bridge(Member m) {
		return (m.access & Opcodes.ACC_BRIDGE) != 0 && (m.access & Opcodes.ACC_SYNTHETIC) != 0;
	}

	/** Whether the member's name is the mod's own: the remap left it as it was and it is not an intermediary name. */
	private static boolean own(Member m, String mapped) {
		return m.name.equals(mapped) && !m.name.startsWith("method_") && !m.name.startsWith("field_") && !m.name.startsWith("comp_");
	}

	private void record(String modClass, String kind, String member, boolean unresolved, String resolution) {
		found.put(modClass + '\t' + kind + '\t' + member, new Clash(modClass.replace('/', '.'), kind, member, resolution, unresolved));
		namer.note("member clash in " + modClass.replace('/', '.') + ", " + kind + " " + member + " (" + resolution + ")");
	}
}
