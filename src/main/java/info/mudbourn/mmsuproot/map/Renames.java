package info.mudbourn.mmsuproot.map;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.Type;

/** The hand-made 1.21.11 to 26.3 Mojang rename table, read from a names-only resource. */
public final class Renames {
	/**
	 * One member rename. It applies to {@code owner} and to everything that extends or implements it.
	 * {@code args} (-1 for any) and {@code desc} (null for any) keep unrelated overloads out.
	 */
	private record Rule(String owner, String newName, int args, String desc) { }

	/** A static field read, or an int constant when {@code owner} is null (then {@code name} is the number), pushed as an extra constructor argument at index {@code position}. */
	public record Inserted(int position, String owner, String name, String desc) { }

	/** A constructor whose descriptor changed; calls pass the {@code inserted} fields as the new arguments. */
	public record Ctor(String oldDesc, String newDesc, List<Inserted> inserted) { }

	/** A constructor whose arguments a static helper converts: the helper takes the old arguments and returns the new ones as an {@code Object[]}. */
	public record CtorAdapt(String oldDesc, String newDesc, String helperOwner, String helperName) { }

	/** An override whose 26.3 descriptor differs: {@code owner} is the 26.3 type that declares {@code newDesc}. */
	public record Bridge(String owner, String name, String oldDesc, String newDesc) { }

	/** A mixin target (26.3 name) that inherits {@code name desc} where 1.21.11 declared it. */
	public record Inherited(String target, String name, String desc) { }

	private final Map<String, List<CtorAdapt>> ctorAdapts = new HashMap<>(); // owner (26.3 name) -> adapters
	private final Map<String, String> callDescs = new HashMap<>(); // "owner.name olddesc" -> new desc
	private final Map<String, String[]> callInsts = new HashMap<>(); // "owner.name desc" -> {helper owner, helper name}
	private final List<Bridge> bridges = new ArrayList<>();
	private final List<Inherited> inherited = new ArrayList<>();
	private final Map<String, List<Ctor>> ctors = new HashMap<>(); // owner (26.3 name) -> adapters
	private final Map<String, String> classes = new HashMap<>();
	private final List<String[]> packages = new ArrayList<>();
	private final Map<String, List<Rule>> methods = new HashMap<>(); // old name -> rules
	private final Map<String, List<Rule>> fields = new HashMap<>();
	private final Map<String, String> fieldOwners = new HashMap<>();
	private final Map<String, String[]> casts = new HashMap<>(); // class (26.3 name) -> {helper owner, helper name}
	private final Map<String, String[]> callMoves = new HashMap<>(); // "owner.name desc" (26.3 names) -> {new owner, new name}
	private final Map<String, String> fieldGets = new HashMap<>(); // "owner.name" (26.3 names) -> getter name
	private final Map<String, String> injectAts = new HashMap<>(); // mixin target string of a call -> the string it becomes
	private final Map<String, String[]> fieldMoves = new HashMap<>(); // "owner.name" (26.3 names) -> {new owner, new name}

	public static Renames load() {
		try (InputStream in = Renames.class.getResourceAsStream("/uproot/renames.txt")) {
			if (in == null) return new Renames();
			return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
		} catch (IOException e) {
			throw new RuntimeException("Could not read the rename table", e);
		}
	}

	/** Reads a table from text in the {@code renames.txt} format. */
	public static Renames parse(BufferedReader reader) throws IOException {
		Renames r = new Renames();
		String line;
		while ((line = reader.readLine()) != null) {
			line = line.strip();
			if (line.isEmpty() || line.charAt(0) == '#') continue;
			String[] p = line.split("\\s+");
			switch (p[0]) {
			case "class" -> r.classes.put(p[1], p[2]);
			case "package" -> r.packages.add(new String[] {p[1] + "/", p[2] + "/"});
			case "method" -> r.methods.computeIfAbsent(p[2], k -> new ArrayList<>()).add(rule(p));
			case "field" -> r.fields.computeIfAbsent(p[2], k -> new ArrayList<>()).add(rule(p));
			case "ctor" -> r.ctors.computeIfAbsent(p[1], k -> new ArrayList<>()).add(ctor(p, line));
			case "fieldowner" -> r.fieldOwners.put(p[1], p[2]);
			case "castadapt" -> {
				int dot = p[2].lastIndexOf('.');
				r.casts.put(p[1], new String[] {p[2].substring(0, dot), p[2].substring(dot + 1)});
			}
			case "callmove" -> r.callMoves.put(p[1] + "." + p[2] + " " + p[3], new String[] {p[4], p[5]});
			case "fieldmove" -> r.fieldMoves.put(p[1] + "." + p[2], new String[] {p[3], p[4]});
			case "fieldget" -> r.fieldGets.put(p[1] + "." + p[2], p[3]);
			case "injectat" -> {
				if (p.length != 7) throw new IllegalStateException("Bad injectat rule: " + line);
				r.injectAts.put("L" + p[1] + ";" + p[2] + p[3], "L" + p[4] + ";" + p[5] + p[6]);
			}
			case "calldesc" -> r.callDescs.put(p[1] + "." + p[2] + " " + p[3], p[4]);
			case "callinst" -> r.callInsts.put(p[1] + "." + p[2] + " " + p[3], helper(p[4], line));
			case "bridge" -> r.bridges.add(bridge(p, line));
			case "inherited" -> {
				if (p.length != 4) throw new IllegalStateException("Bad inherited rule: " + line);
				r.inherited.add(new Inherited(p[1], p[2], p[3]));
			}
			case "ctoradapt" -> {
				String[] h = helper(p[4], line);
				r.ctorAdapts.computeIfAbsent(p[1], k -> new ArrayList<>()).add(new CtorAdapt(p[2], p[3], h[0], h[1]));
			}
			default -> { }
			}
		}
		return r;
	}

	/** Applies class and package renames to a Mojang internal name. Returns the input when none applies. */
	public String cls(String name) {
		String exact = classes.get(name);
		if (exact != null) return exact;
		int dollar = name.indexOf('$');
		String outer = dollar < 0 ? name : name.substring(0, dollar);
		String suffix = dollar < 0 ? "" : name.substring(dollar);
		String renamed = classes.get(outer);
		if (renamed != null) return renamed + suffix;
		for (String[] p : packages) {
			if (outer.startsWith(p[0])) return p[1] + outer.substring(p[0].length()) + suffix;
		}
		return name;
	}

	/**
	 * Reads {@code ctor OWNER OLDDESC NEWDESC POS=FOWNER.FIELD:FDESC...}. Only the shapes the remapper can
	 * emit are accepted: one inserted field of one slot, placed last or just before a one-slot last argument.
	 */
	private static Ctor ctor(String[] p, String line) {
		if (p.length != 5 || !p[2].startsWith("(") || !p[3].startsWith("(")) throw new IllegalStateException("Bad ctor rule: " + line);
		Matcher k = Pattern.compile("(\\d+)=#(-?\\d+)").matcher(p[4]);
		if (k.matches()) {
			int at = Integer.parseInt(k.group(1));
			if (at != Type.getArgumentTypes(p[2]).length || Type.getArgumentTypes(p[3]).length != at + 1) throw new IllegalStateException("An int constant goes last: " + line);
			return new Ctor(p[2], p[3], List.of(new Inserted(at, null, k.group(2), "I")));
		}
		Matcher m = Pattern.compile("(\\d+)=([\\w/$]+)\\.(\\w+):(L[\\w/$]+;)").matcher(p[4]);
		if (!m.matches()) throw new IllegalStateException("Unsupported ctor insertion (one object-typed static field only): " + line);
		int pos = Integer.parseInt(m.group(1));
		Type[] old = Type.getArgumentTypes(p[2]);
		Type[] now = Type.getArgumentTypes(p[3]);
		boolean last = pos == old.length;
		boolean beforeLast = pos == old.length - 1 && old[pos].getSize() == 1;
		if (now.length != old.length + 1 || !(last || beforeLast)) throw new IllegalStateException("Unsupported ctor shape: " + line);
		return new Ctor(p[2], p[3], List.of(new Inserted(pos, m.group(2), m.group(3), m.group(4))));
	}

	/** The adapter for {@code invokespecial owner.<init>desc}, both in 26.3 names, or null. */
	public Ctor ctor(String owner, String desc) {
		List<Ctor> list = ctors.get(owner);
		if (list == null) return null;
		for (Ctor c : list) if (c.oldDesc.equals(desc)) return c;
		return null;
	}

	/** Splits {@code pkg/Class.method} into {class, method}. */
	private static String[] helper(String ref, String line) {
		int dot = ref.lastIndexOf('.');
		if (dot <= 0 || dot == ref.length() - 1) throw new IllegalStateException("Bad helper reference (want pkg/Class.method): " + line);
		return new String[] {ref.substring(0, dot), ref.substring(dot + 1)};
	}

	/** Reads {@code bridge OWNER NAME OLDDESC NEWDESC}: the new descriptor keeps the old parameters first and may add more after them. */
	private static Bridge bridge(String[] p, String line) {
		if (p.length != 5 || !p[3].startsWith("(") || !p[4].startsWith("(")) throw new IllegalStateException("Bad bridge rule: " + line);
		Type[] old = Type.getArgumentTypes(p[3]);
		Type[] now = Type.getArgumentTypes(p[4]);
		if (now.length < old.length || !Type.getReturnType(p[3]).equals(Type.getReturnType(p[4]))) throw new IllegalStateException("A bridge keeps the return type and only adds parameters at the end: " + line);
		for (int i = 0; i < old.length; i++) {
			if (old[i].getSort() != now[i].getSort() || (old[i].getSort() < Type.ARRAY && !old[i].equals(now[i]))) throw new IllegalStateException("A bridge may only narrow object parameters: " + line);
		}
		return new Bridge(p[1], p[2], p[3], p[4]);
	}

	/** The adapter for {@code invokespecial owner.<init>desc}, both in 26.3 names, or null. */
	public CtorAdapt ctorAdapt(String owner, String desc) {
		List<CtorAdapt> list = ctorAdapts.get(owner);
		if (list == null) return null;
		for (CtorAdapt c : list) if (c.oldDesc.equals(desc)) return c;
		return null;
	}

	/** The call descriptor to use for a virtual call of {@code owner.name desc} (26.3 names), or null. */
	public String callDesc(String owner, String name, String desc) {
		return callDescs.get(owner + "." + name + " " + desc);
	}

	/** The static helper {owner, name} an instance call of {@code owner.name desc} goes to, or null. */
	public String[] callInst(String owner, String name, String desc) {
		return callInsts.get(owner + "." + name + " " + desc);
	}

	/** Whether a call descriptor or instance redirect is named {@code name}, so callers can skip the hierarchy walk. */
	public boolean hasCallRule(String name) {
		for (String key : callDescs.keySet()) if (key.substring(key.indexOf('.') + 1).startsWith(name + " ")) return true;
		for (String key : callInsts.keySet()) if (key.substring(key.indexOf('.') + 1).startsWith(name + " ")) return true;
		return false;
	}

	public List<Inherited> inherited() {
		return inherited;
	}

	public List<Bridge> bridges() {
		return bridges;
	}

	private static Rule rule(String[] p) {
		int args = -1;
		String desc = null;
		if (p.length > 4) {
			if (p[4].startsWith("(")) desc = p[4];
			else args = Integer.parseInt(p[4]);
		}
		return new Rule(p[1], p[3], args, desc);
	}

	public boolean hasMethod(String name) {
		return methods.containsKey(name);
	}

	public boolean hasField(String name) {
		return fields.containsKey(name);
	}

	/**
	 * The new name of a method. {@code ancestors} are the 26.3 names of the owner and everything it
	 * extends or implements, or null when the owner is unknown (then only {@code *} rules apply).
	 * {@code args} is the argument count (-1 unknown) and {@code mojangDesc} the descriptor in 1.21.11 names (or null).
	 */
	public String method(Collection<String> ancestors, String name, int args, String mojangDesc) {
		return pick(methods.get(name), ancestors, name, args, mojangDesc);
	}

	public String field(Collection<String> ancestors, String name) {
		return pick(fields.get(name), ancestors, name, -1, null);
	}

	private String pick(List<Rule> rules, Collection<String> ancestors, String name, int args, String desc) {
		if (rules == null) return name;
		String wildcard = null;
		for (Rule r : rules) {
			if (r.args >= 0 && args >= 0 && r.args != args) continue;
			if (r.desc != null && desc != null && !r.desc.equals(desc)) continue;
			if (r.owner.equals("*")) wildcard = r.newName;
			else if (ancestors != null && ancestors.contains(cls(r.owner))) return r.newName;
		}
		return wildcard != null ? wildcard : name;
	}

	/** The class that now holds the static constants of {@code owner}, or null. */
	public String fieldOwner(String owner) {
		return fieldOwners.get(owner);
	}

	/** Where a static field read now goes, as {new owner, new name}, or null. Owner and name are 26.3 names. */
	public String[] fieldMove(String owner, String name) {
		return fieldMoves.get(owner + "." + name);
	}

	/** Where a static call now goes, as {new owner, new name} with the same descriptor, or null. All 26.3 names. */
	/** {helper owner, helper name} for a checkcast to the class, or null. */
	public String[] castAdapt(String cls) {
		return casts.get(cls);
	}

	public String[] callMove(String owner, String name, String desc) {
		return callMoves.get(owner + "." + name + " " + desc);
	}

	/** The no-argument method that a read of the field {@code owner.name} (26.3 names) goes to now, or null. */
	public String fieldGet(String owner, String name) {
		return fieldGets.get(owner + "." + name);
	}

	/** The mixin {@code @At} target strings that are retargeted, from the 26.3 form of the 1.21.11 target to the new one. */
	public Map<String, String> injectAts() {
		return injectAts;
	}

	/** Whether any call redirect is named {@code name}, so callers can skip the hierarchy walk. */
	public boolean hasCallMove(String name) {
		for (String key : callMoves.keySet()) if (key.substring(key.indexOf('.') + 1).startsWith(name + " ")) return true;
		return false;
	}
}
