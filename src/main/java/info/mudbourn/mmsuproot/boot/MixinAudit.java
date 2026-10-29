package info.mudbourn.mmsuproot.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import info.mudbourn.mmsuproot.disable.Disabler;
import info.mudbourn.mmsuproot.map.GameClasses;
import info.mudbourn.mmsuproot.remap.Namer;
import info.mudbourn.mmsuproot.util.Json;
import net.fabricmc.api.EnvType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Checks a target mod's mixins against the game before Mixin runs, so the report names the ones
 * that cannot apply: a target class that is gone, an injector method that is gone, a shadowed
 * member that is gone.
 */
final class MixinAudit {
	private MixinAudit() { }

	static void run(ModSource src, EnvType side) {
		for (String cfg : src.mixinConfigs(side)) {
			try {
				Path p = src.mod.findPath(cfg).orElse(null);
				if (p == null) {
					src.report.mixinIssues.add(cfg + ": config file missing");
					continue;
				}
				Map<String, Object> json = Json.parseObject(Files.readString(p));
				String pkg = String.valueOf(json.get("package")).replace('.', '/') + "/";
				for (String key : new String[] {"mixins", "client", "server"}) {
					if (!(json.get(key) instanceof List<?> l)) continue;
					if (!key.equals("mixins") && !key.equals(side == EnvType.CLIENT ? "client" : "server")) continue;
					for (Object o : l) audit(src, pkg + String.valueOf(o).replace('.', '/'));
				}
			} catch (IOException | RuntimeException e) {
				src.report.mixinIssues.add(cfg + ": could not audit: " + e);
			}
		}
	}

	private static void audit(ModSource src, String internal) {
		String label = internal.substring(internal.lastIndexOf('/') + 1);
		String dropped = Disabler.mixinReason(internal);
		if (dropped != null) {
			src.report.mixinIssues.add(label + ": left out of the config on purpose: " + dropped);
			return;
		}
		ClassNode node = new ClassNode();
		try {
			byte[] bytes = src.remapped(internal, false);
			if (bytes == null) {
				src.report.mixinIssues.add(label + ": mixin class missing from the jar");
				return;
			}
			new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
		} catch (IOException | RuntimeException e) {
			src.report.mixinIssues.add(label + ": could not read: " + e);
			return;
		}

		GameClasses game = src.namer.game();
		if (!game.indexed()) return;

		List<String> targets = new ArrayList<>();
		for (AnnotationNode a : node.invisibleAnnotations == null ? List.<AnnotationNode>of() : node.invisibleAnnotations) {
			if (!a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") || a.values == null) continue;
			for (int i = 0; i + 1 < a.values.size(); i += 2) {
				String key = (String) a.values.get(i);
				if (!(a.values.get(i + 1) instanceof List<?> list)) continue;
				for (Object v : list) {
					if (key.equals("value") && v instanceof Type t) targets.add(t.getInternalName());
					else if (key.equals("targets") && v instanceof String s) targets.add(s.replace('.', '/'));
				}
			}
		}

		List<String> present = new ArrayList<>();
		for (String t : targets) {
			if (game.has(t)) present.add(t);
			else if (t.startsWith("net/minecraft/")) src.report.mixinIssues.add(label + ": target class missing in 26.3: " + t);
			else present.add(t); // another mod's class; cannot judge
		}
		if (present.isEmpty()) return;

		for (MethodNode m : node.methods) {
			checkAnnotations(src, label, m.name, m.invisibleAnnotations, present, true, null);
		}
		for (FieldNode f : node.fields) {
			checkAnnotations(src, label, f.name, f.invisibleAnnotations, present, false, f.desc);
		}
	}

	private static void checkAnnotations(ModSource src, String label, String member, List<AnnotationNode> annos,
			List<String> targets, boolean isMethod, String fieldDesc) {
		if (annos == null) return;
		GameClasses game = src.namer.game();
		for (AnnotationNode a : annos) {
			if (a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) {
				String prefix = "";
				if (a.values != null) for (int i = 0; i + 1 < a.values.size(); i += 2) if ("prefix".equals(a.values.get(i))) prefix = (String) a.values.get(i + 1);
				String name = prefix.isEmpty() || !member.startsWith(prefix) ? member : member.substring(prefix.length());
				boolean found = false;
				for (String t : targets) found |= isMethod ? game.findMethod(t, name, null) : game.findField(t, name, fieldDesc);
				if (!found && !isMethod) src.report.mixinIssues.add(label + ": @Shadow field '" + name + "' missing in " + targets);
				if (!found && isMethod) src.report.mixinIssues.add(label + ": @Shadow method '" + name + "' missing in " + targets);
				continue;
			}

			boolean injector = a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") || a.desc.startsWith("Lcom/llamalad7/mixinextras/injector/");
			if (!injector || a.values == null) continue;
			for (int i = 0; i + 1 < a.values.size(); i += 2) {
				String key = (String) a.values.get(i);
				Object v = a.values.get(i + 1);
				if (key.equals("method") && v instanceof List<?> list) {
					for (Object o : list) {
						String name = methodName((String) o);
						if (name == null) continue;
						boolean found = false;
						for (String t : targets) found |= game.findMethod(t, name, null);
						if (!found) src.report.mixinIssues.add(label + "." + member + ": injection method '" + name + "' missing in " + targets);
					}
				} else if (key.equals("at") || key.equals("slice")) {
					checkAt(src, label, member, v);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void checkAt(ModSource src, String label, String member, Object v) {
		if (v instanceof List<?> list) {
			for (Object o : list) checkAt(src, label, member, o);
			return;
		}
		if (!(v instanceof AnnotationNode at) || at.values == null) return;
		for (int i = 0; i + 1 < at.values.size(); i += 2) {
			Object val = at.values.get(i + 1);
			if (val instanceof AnnotationNode || val instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof AnnotationNode) {
				checkAt(src, label, member, val);
			} else if ("target".equals(at.values.get(i)) && val instanceof String s) {
				String problem = checkTarget(src.namer.game(), s);
				if (problem != null) src.report.mixinIssues.add(label + "." + member + ": @At target " + problem + ": " + s);
			}
		}
	}

	/** Checks {@code Lowner;name(desc)ret} or {@code Lowner;name:desc}. Returns the problem or null. */
	private static String checkTarget(GameClasses game, String target) {
		if (!target.startsWith("L")) return null;
		int semi = target.indexOf(';');
		if (semi < 0) return null;
		String owner = target.substring(1, semi);
		if (!owner.startsWith("net/minecraft/")) return null;
		if (!game.has(owner)) return "class missing";
		String rest = target.substring(semi + 1);
		int paren = rest.indexOf('(');
		int colon = rest.indexOf(':');
		if (paren > 0) {
			return game.findMethod(owner, rest.substring(0, paren), rest.substring(paren)) ? null : "method missing or changed";
		} else if (colon > 0) {
			return game.findField(owner, rest.substring(0, colon), rest.substring(colon + 1)) ? null : "field missing or changed";
		}
		return null;
	}

	/** The plain method name in a mixin method spec, or null for wildcards and constructors. */
	private static String methodName(String spec) {
		if (spec.startsWith("/") || spec.contains("*")) return null;
		int semi = spec.indexOf(';');
		if (semi >= 0) spec = spec.substring(semi + 1);
		int paren = spec.indexOf('(');
		if (paren >= 0) spec = spec.substring(0, paren);
		int colon = spec.indexOf(':');
		if (colon >= 0) spec = spec.substring(0, colon);
		if (spec.isEmpty() || spec.equals("<init>") || spec.equals("<clinit>")) return null;
		return spec;
	}
}
