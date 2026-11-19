package info.mudbourn.mmsuproot.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import info.mudbourn.mmsuproot.map.GameClasses;
import info.mudbourn.mmsuproot.remap.Namer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * The code side of {@link IntersectTool}: what a mod's mixins and access wideners touch, named in 26.3 terms (the classes it is
 * given are already remapped for a mod Uproot bridges).
 *
 * <p>Node keys: {@code method:CLASS#NAME+DESC} (a handler's target method; {@code NAME} alone when the name is overloaded and the
 * annotation gave no descriptor), {@code site:CLASS#METHOD@VALUE:TARGET} (a call site, field or constant inside it), {@code class:CLASS}
 * (a mixin into the class), {@code access:CLASS[#member]} (an access widener or class tweaker line).
 */
final class IntersectCode {
	private IntersectCode() { }

	/** What a mixin config says about its classes. */
	record MixinCfg(int priority, Integer require) { }

	private static final Map<String, Double> WEIGHTS = Map.ofEntries(
			Map.entry("Overwrite", 8.0), Map.entry("Redirect", 6.0),
			Map.entry("ModifyConstant", 4.0), Map.entry("ModifyArg", 4.0), Map.entry("ModifyArgs", 4.0), Map.entry("ModifyVariable", 4.0),
			Map.entry("WrapOperation", 3.0), Map.entry("WrapWithCondition", 3.0), Map.entry("ModifyReturnValue", 3.0),
			Map.entry("ModifyExpressionValue", 3.0), Map.entry("WrapMethod", 3.0), Map.entry("ModifyReceiver", 3.0),
			Map.entry("Inject", 1.0), Map.entry("Accessor", 0.5), Map.entry("Invoker", 0.5), Map.entry("Shadow", 0.25));

	/** Reads every mixin handler of one class into {@code out}, as the {@code mod} that ships it. */
	static void analyze(String mod, boolean legacy, byte[] bytes, MixinCfg cfg, GameClasses game, Touches out, IntersectRules rules) {
		ClassNode cn = new ClassNode();
		new ClassReader(bytes).accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		Map<String, Object> mixin = null;
		for (AnnotationNode a : annotations(cn.invisibleAnnotations, cn.visibleAnnotations)) {
			if (a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) mixin = values(a);
		}
		if (mixin == null) return;
		List<String> targets = new ArrayList<>();
		for (Object o : list(mixin.get("value"))) if (o instanceof Type t && t.getSort() == Type.OBJECT) targets.add(t.getInternalName());
		for (Object o : list(mixin.get("targets"))) if (o instanceof String s) targets.add(s.replace('.', '/'));
		int priority = mixin.get("priority") instanceof Integer i ? i : cfg != null ? cfg.priority() : 1000;
		int ifaces = cn.interfaces == null ? 0 : cn.interfaces.size();
		for (String target : targets) {
			out.add("class:" + target, new Touch(mod, "Mixin", cap("priority=" + priority + (ifaces > 0 ? " interfaces=" + ifaces : "") + " via " + simple(cn.name)),
					1 + ifaces, legacy, rules.hitsClass(target)));
		}
		for (MethodNode m : cn.methods) {
			for (AnnotationNode a : annotations(m.invisibleAnnotations, m.visibleAnnotations)) handler(mod, legacy, cn, m, a, targets, priority, cfg, game, out, rules);
		}
		for (FieldNode f : cn.fields) {
			for (AnnotationNode a : annotations(f.invisibleAnnotations, f.visibleAnnotations)) {
				if (!simpleName(a.desc).equals("Shadow") || !a.desc.contains("mixin")) continue;
				Map<String, Object> v = values(a);
				String prefix = v.get("prefix") instanceof String p ? p : "shadow$";
				String name = f.name.startsWith(prefix) ? f.name.substring(prefix.length()) : f.name;
				for (String t : targets) out.add("method:" + t + "#field:" + name, new Touch(mod, "Shadow", cap("field " + name + ":" + f.desc), 0.25, legacy, rules.hitsMember(t, name)));
			}
		}
	}

	private static void handler(String mod, boolean legacy, ClassNode cn, MethodNode m, AnnotationNode a, List<String> targets, int priority, MixinCfg cfg,
			GameClasses game, Touches out, IntersectRules rules) {
		String type = simpleName(a.desc);
		Double base = WEIGHTS.get(type);
		if (base == null || !(a.desc.contains("mixin") || a.desc.contains("llamalad7"))) return;
		Map<String, Object> v = values(a);
		double weight = base;
		StringBuilder detail = new StringBuilder("@").append(type);
		if (type.equals("Inject") && Boolean.TRUE.equals(v.get("cancellable"))) {
			weight = 3;
			detail.append(" cancellable");
		}
		if (priority != 1000) detail.append(" priority=").append(priority);
		Object req = v.get("require");
		if (req instanceof Integer r) detail.append(" require=").append(r);
		else if (cfg != null && cfg.require() != null) detail.append(" require=").append(cfg.require()).append(" (config)");
		List<String[]> methods = new ArrayList<>(); // {name, desc or null}
		switch (type) {
		case "Overwrite" -> methods.add(new String[] {m.name, m.desc});
		case "Shadow" -> {
			String prefix = v.get("prefix") instanceof String p ? p : "shadow$";
			methods.add(new String[] {m.name.startsWith(prefix) ? m.name.substring(prefix.length()) : m.name, m.desc});
		}
		case "Accessor" -> {
			String field = v.get("value") instanceof String s && !s.isEmpty() ? s : derive(m.name, new String[] {"get", "is", "set"});
			for (String t : targets) out.add("method:" + t + "#field:" + field, new Touch(mod, "Accessor", cap(detail + " " + m.name), weight, legacy, rules.hitsMember(t, field)));
			return;
		}
		case "Invoker" -> methods.add(new String[] {v.get("value") instanceof String s && !s.isEmpty() ? s : derive(m.name, new String[] {"call", "invoke"}), null});
		default -> {
			for (Object o : list(v.get("method"))) if (o instanceof String s) methods.add(parseMethod(s));
		}
		}
		List<Object> ats = new ArrayList<>(list(v.get("at")));
		List<String> sites = new ArrayList<>();
		for (Object o : ats) {
			if (!(o instanceof AnnotationNode at)) continue;
			Map<String, Object> av = values(at);
			String value = String.valueOf(av.getOrDefault("value", ""));
			String target = av.get("target") instanceof String s ? normalizeTarget(s) : "";
			if (!target.isEmpty()) sites.add(value + ":" + target);
			detail.append(" at=").append(value).append(target.isEmpty() ? "" : "(" + target + ")");
		}
		if (v.get("constant") instanceof AnnotationNode || v.get("constant") instanceof List<?>) {
			for (Object o : list(v.get("constant"))) {
				if (!(o instanceof AnnotationNode c)) continue;
				for (Map.Entry<String, Object> e : values(c).entrySet()) {
					if (e.getKey().endsWith("Value")) sites.add("CONSTANT:" + e.getKey().replace("Value", "") + "=" + e.getValue());
				}
			}
		}
		for (String t : targets) {
			for (String[] spec : methods) {
				String mkey = resolve(t, spec[0], spec[1], game);
				boolean hit = rules.hitsMember(t, spec[0]);
				out.add("method:" + t + "#" + mkey, new Touch(mod, type, cap(detail.toString()), weight, legacy, hit));
				for (String site : sites) out.add("site:" + t + "#" + mkey + "@" + site, new Touch(mod, type, cap(detail.toString()), weight, legacy, hit));
			}
		}
	}

	/** {@code name} and, when the annotation gave one, the descriptor, from a mixin method selector (which may carry an owner prefix). */
	static String[] parseMethod(String s) {
		if (s.startsWith("L") && s.indexOf(';') > 0) s = s.substring(s.indexOf(';') + 1);
		int p = s.indexOf('(');
		return p < 0 ? new String[] {s, null} : new String[] {s.substring(0, p), s.substring(p)};
	}

	/** A bare method name becomes {@code name+desc} when the 26.3 class declares exactly one method of that name. */
	private static String resolve(String cls, String name, String desc, GameClasses game) {
		if (desc != null) return name + desc;
		Optional<GameClasses.Info> info = game.info(cls);
		if (info.isEmpty()) return name;
		String only = null;
		for (String sig : info.get().methods()) {
			if (!sig.startsWith(name + "(")) continue;
			if (only != null) return name;
			only = sig;
		}
		return only == null ? name : only;
	}

	/** {@code Lowner;name(desc)V}, {@code owner.name(desc)V} and {@code a.b.Owner.name(desc)V} all become {@code Lowner;name(desc)V}. */
	static String normalizeTarget(String t) {
		t = t.strip();
		if (t.startsWith("L") && t.indexOf(';') > 0) return t;
		int paren = t.indexOf('(');
		int colon = t.indexOf(':');
		int cut = paren >= 0 ? paren : colon;
		if (cut <= 0) return t;
		int dot = t.lastIndexOf('.', cut);
		if (dot <= 0) return t;
		return "L" + t.substring(0, dot).replace('.', '/') + ";" + t.substring(dot + 1);
	}

	private static String derive(String methodName, String[] prefixes) {
		for (String p : prefixes) {
			if (methodName.startsWith(p) && methodName.length() > p.length() && Character.isUpperCase(methodName.charAt(p.length()))) {
				String rest = methodName.substring(p.length());
				return Character.toLowerCase(rest.charAt(0)) + rest.substring(1);
			}
		}
		return methodName;
	}

	/** Reads an access widener or class tweaker, naming its entries through {@code namer} when the mod is remapped (null: names are already the game's). */
	static void widener(String mod, boolean legacy, String text, Namer namer, Touches out, IntersectRules rules) {
		boolean header = true;
		for (String line : text.split("\\R")) {
			String t = line.strip();
			int hash = t.indexOf('#');
			if (hash >= 0) t = t.substring(0, hash).strip();
			if (t.isEmpty()) continue;
			String[] p = t.split("\\s+");
			if (header) {
				header = false;
				continue;
			}
			String action = p[0].startsWith("transitive-") ? p[0].substring("transitive-".length()) : p[0];
			if (action.equals("inject-interface") && p.length >= 3) {
				String owner = namer == null ? p[1] : namer.cls(p[1]);
				out.add("access:" + owner + "#interface:" + (namer == null ? p[2] : namer.cls(p[2])), new Touch(mod, "ClassTweaker", cap(t), 0.5, legacy, rules.hitsClass(owner)));
				continue;
			}
			if (!(action.equals("accessible") || action.equals("extendable") || action.equals("mutable")) || p.length < 3) continue;
			String owner = namer == null ? p[2] : namer.cls(p[2]);
			String key = "access:" + owner;
			if (p[1].equals("method") && p.length >= 5) {
				key += "#" + (namer == null ? p[3] + p[4] : namer.method(p[2], p[3], p[4]) + namer.desc(p[4]));
			} else if (p[1].equals("field") && p.length >= 5) {
				key += "#field:" + (namer == null ? p[3] : namer.field(p[2], p[3], p[4]));
			}
			out.add(key, new Touch(mod, "AccessWidener", cap(action + " " + p[1]), 0.5, legacy, p[1].equals("class") ? rules.hitsClass(owner) : rules.hitsMember(owner, p.length >= 4 ? p[3] : "")));
		}
	}

	// ---- annotation tree helpers

	@SafeVarargs
	private static List<AnnotationNode> annotations(List<AnnotationNode>... lists) {
		List<AnnotationNode> all = new ArrayList<>();
		for (List<AnnotationNode> l : lists) if (l != null) all.addAll(l);
		return all;
	}

	private static Map<String, Object> values(AnnotationNode a) {
		Map<String, Object> out = new HashMap<>();
		if (a.values == null) return out;
		for (int i = 0; i + 1 < a.values.size(); i += 2) {
			Object val = a.values.get(i + 1);
			if (val instanceof String[] e && e.length == 2) val = e[1]; // an enum constant
			out.put((String) a.values.get(i), val);
		}
		return out;
	}

	private static List<Object> list(Object o) {
		if (o == null) return List.of();
		if (o instanceof List<?> l) return new ArrayList<>(l);
		return List.of(o);
	}

	private static String simpleName(String desc) {
		String s = desc.substring(1, desc.length() - 1);
		return s.substring(s.lastIndexOf('/') + 1);
	}

	private static String simple(String internal) {
		return internal.substring(internal.lastIndexOf('/') + 1);
	}

	static String cap(String s) {
		return s.length() > 160 ? s.substring(0, 157) + "..." : s;
	}
}
