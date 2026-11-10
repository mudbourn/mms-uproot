package info.mudbourn.mmsuproot.remap;

import java.util.ArrayList;
import java.util.List;

import info.mudbourn.mmsuproot.map.GameClasses;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Makes a mixin fit the class hierarchy of 26.3. Mixin looks up an {@code @Shadow} member in the target class only
 * and wants the mixin's declared superclass to lie in the target's hierarchy; a game that moved members up or
 * between classes breaks both without changing what the mixin means. For a mixin with a single game target this
 * <ul>
 * <li>replaces a declared superclass the target no longer extends by the nearest class of the target's chain that the old
 * one also extends, and sends the super calls there;</li>
 * <li>drops a shadow member the target inherits and points its uses at the target, which resolves them up the chain;</li>
 * <li>for a shadow method that is static in 26.3 and was an instance method, replaces the declaration by a static adapter
 * that takes the old receiver as its first argument;</li>
 * <li>drops a shadow method the game lacks, and turns a shadow field the game lacks into a field of the mixin itself,
 * so the rest of the mixin still applies; calls and accesses of what is gone are listed by the audit.</li>
 * </ul>
 * Runs after the name mapping, so every name is a 26.3 name.
 */
final class MixinShadows extends ClassNode {
	private static final int API = Opcodes.ASM9;
	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
	private static final String UNIQUE = "Lorg/spongepowered/asm/mixin/Unique;";

	private final ClassVisitor next;
	private final Namer namer;
	private final List<MethodNode> adapters = new ArrayList<>();

	MixinShadows(ClassVisitor next, Namer namer) {
		super(API);
		this.next = next;
		this.namer = namer;
	}

	@Override
	public void visitEnd() {
		super.visitEnd();
		retargetMixin();
		String target = singleTarget();
		if (target != null && namer.game().has(target)) {
			fixSuper(target);
			fixMethods(target);
			fixFields(target);
			methods.addAll(adapters);
		}
		for (MethodNode m : methods) {
			String[] invoker = namer.renames().invokerParam(name, m.name);
			if (invoker != null && invoker[0].equals(m.desc)) {
				// an @Invoker whose target gained trailing parameters: the interface method takes them too (callers are adapted in ClassRemap)
				m.desc = ClassRemap.withExtraParams(m.desc, invoker[1]);
				m.signature = null;
				m.visibleParameterAnnotations = null;
				m.invisibleParameterAnnotations = null;
				m.visibleAnnotableParameterCount = 0;
				m.invisibleAnnotableParameterCount = 0;
			}
			if (!namer.renames().optional(name, m.name)) continue;
			for (List<AnnotationNode> list : java.util.Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations)) {
				if (list == null) continue;
				for (AnnotationNode a : list) {
					if (a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") || a.desc.startsWith("Lcom/llamalad7/mixinextras/")) {
						if (a.values == null) a.values = new ArrayList<>();
						a.values.add("require");
						a.values.add(0);
					}
				}
			}
		}
		accept(next);
	}

	/** A {@code mixintarget} rule: the mixin's {@code @Mixin} names the class that now holds what it injects into. */
	@SuppressWarnings("unchecked")
	private void retargetMixin() {
		for (List<AnnotationNode> list : java.util.Arrays.asList(invisibleAnnotations, visibleAnnotations)) {
			if (list == null) continue;
			for (AnnotationNode a : list) {
				if (!MIXIN.equals(a.desc) || a.values == null) continue;
				for (int i = 0; i + 1 < a.values.size(); i += 2) {
					if (!(a.values.get(i + 1) instanceof List<?> l) || !"value".equals(a.values.get(i))) continue;
					List<Object> targets = (List<Object>) l;
					for (int k = 0; k < targets.size(); k++) {
						if (!(targets.get(k) instanceof Type t) || t.getSort() != Type.OBJECT) continue;
						String now = namer.renames().mixinTarget(name, t.getInternalName());
						if (now == null) continue;
						targets.set(k, Type.getObjectType(now));
						namer.note("mixin " + simple() + " targets " + now.substring(now.lastIndexOf('/') + 1) + " now, " + t.getInternalName().substring(t.getInternalName().lastIndexOf('/') + 1) + " no longer holds what it injects into");
					}
				}
			}
		}
	}

	/** The one class the mixin targets by class literal, or null. */
	private String singleTarget() {
		List<String> targets = new ArrayList<>();
		for (List<AnnotationNode> list : List.of(invisibleAnnotations == null ? List.<AnnotationNode>of() : invisibleAnnotations,
				visibleAnnotations == null ? List.<AnnotationNode>of() : visibleAnnotations)) {
			for (AnnotationNode a : list) {
				if (!MIXIN.equals(a.desc) || a.values == null) continue;
				for (int i = 0; i + 1 < a.values.size(); i += 2) {
					if (!(a.values.get(i + 1) instanceof List<?> l)) continue;
					for (Object o : l) {
						if (o instanceof Type t && t.getSort() == Type.OBJECT) targets.add(t.getInternalName());
						else if (o instanceof String s && "targets".equals(a.values.get(i))) targets.add(s.replace('.', '/'));
					}
				}
			}
		}
		return targets.size() == 1 ? targets.get(0) : null;
	}

	/** The declared superclass of the mixin, when the target no longer extends it, becomes the closest class the target does extend. */
	private void fixSuper(String target) {
		GameClasses game = namer.game();
		if (superName == null || superName.equals("java/lang/Object") || !game.has(superName)) return;
		if (namer.ancestorsOf(target).contains(superName)) return;
		var above = namer.ancestorsOf(superName);
		String found = null;
		for (String c = game.info(target).map(GameClasses.Info::superName).orElse(null); c != null && found == null; ) {
			if (above.contains(c)) found = c;
			c = game.info(c).map(GameClasses.Info::superName).orElse(null);
		}
		if (found == null || found.equals("java/lang/Object")) return;
		String old = superName;
		superName = found;
		signature = null;
		for (MethodNode m : methods) {
			for (var insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKESPECIAL && mi.owner.equals(old)) mi.owner = found;
			}
		}
		namer.note("mixin " + simple() + " extends " + found.substring(found.lastIndexOf('/') + 1) + " now, " + old.substring(old.lastIndexOf('/') + 1) + " is not in the hierarchy of " + target.substring(target.lastIndexOf('/') + 1));
	}

	private void fixMethods(String target) {
		GameClasses game = namer.game();
		for (MethodNode m : new ArrayList<>(methods)) {
			if (!shadow(m.visibleAnnotations) && !shadow(m.invisibleAnnotations)) continue;
			if (m.name.startsWith("shadow$")) continue;
			String declaring = game.declaringMethod(target, m.name, m.desc);
			boolean wasStatic = (m.access & Opcodes.ACC_STATIC) != 0;
			if (declaring == null) {
				methods.remove(m);
				namer.note("mixin " + simple() + " drops the shadow method " + m.name + m.desc + ": " + target.substring(target.lastIndexOf('/') + 1) + " has no such method in 26.3");
				continue;
			}
			int flags = game.info(declaring).map(i -> i.methodFlags(m.name, m.desc)).orElse(-1);
			if ((flags & Opcodes.ACC_PRIVATE) != 0 && !declaring.equals(target)) { // not reachable from the target: dropped like a missing one
				methods.remove(m);
				namer.note("mixin " + simple() + " drops the shadow method " + m.name + m.desc + ": it is private to " + declaring.substring(declaring.lastIndexOf('/') + 1) + " in 26.3");
				continue;
			}
			if ((flags & Opcodes.ACC_PRIVATE) != 0) continue;
			if (declaring.equals(target) && ((flags & Opcodes.ACC_STATIC) != 0) == wasStatic) continue; // the target declares it as the mixin expects
			if ((flags & Opcodes.ACC_STATIC) != 0 && !wasStatic) {
				methods.remove(m);
				MethodNode adapter = staticAdapter(m, declaring);
				adapters.add(adapter);
				redirectCalls(m, adapter);
				namer.note("mixin " + simple() + " calls " + m.name + " through a static adapter: it is static in " + declaring.substring(declaring.lastIndexOf('/') + 1) + " now");
				continue;
			}
			methods.remove(m);
			retarget(m.name, m.desc, target);
			namer.note("mixin " + simple() + " no longer shadows " + m.name + ": it is inherited from " + declaring.substring(declaring.lastIndexOf('/') + 1));
		}
	}

	private void fixFields(String target) {
		GameClasses game = namer.game();
		for (FieldNode f : new ArrayList<>(fields)) {
			if (!shadow(f.visibleAnnotations) && !shadow(f.invisibleAnnotations)) continue;
			if (f.name.startsWith("shadow$") || game.info(target).map(i -> i.hasField(f.name, f.desc)).orElse(true)) continue;
			String declaring = game.declaringField(target, f.name, f.desc);
			if (declaring != null) {
				int flags = game.info(declaring).map(i -> i.fieldFlags(f.name, f.desc)).orElse(-1);
				if ((flags & Opcodes.ACC_PRIVATE) == 0) {
					fields.remove(f);
					retargetField(f.name, f.desc, target);
					namer.note("mixin " + simple() + " no longer shadows " + f.name + ": it is inherited from " + declaring.substring(declaring.lastIndexOf('/') + 1));
					continue;
				}
				// private to a superclass of the target: the mixin cannot reach it; a fieldget or fieldset rule may answer its uses
			}
			// the game lacks the field: the mixin keeps its own, so the rest of it still applies
			if (f.visibleAnnotations != null) f.visibleAnnotations.removeIf(a -> SHADOW.equals(a.desc));
			if (f.invisibleAnnotations != null) f.invisibleAnnotations.removeIf(a -> SHADOW.equals(a.desc));
			if (f.invisibleAnnotations == null) f.invisibleAnnotations = new ArrayList<>();
			f.invisibleAnnotations.add(new AnnotationNode(UNIQUE));
			f.access = (f.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE;
			namer.note("mixin " + simple() + " keeps " + f.name + " as a field of its own: " + target.substring(target.lastIndexOf('/') + 1) + (declaring == null ? " has none in 26.3" : " cannot reach it, it is private to " + declaring.substring(declaring.lastIndexOf('/') + 1)));
		}
	}

	/** A private static method {@code (Lthis;args)ret} that makes the static call the shadow method now stands for. */
	private MethodNode staticAdapter(MethodNode shadow, String declaring) {
		Type[] args = Type.getArgumentTypes(shadow.desc);
		Type ret = Type.getReturnType(shadow.desc);
		Type[] withThis = new Type[args.length + 1];
		withThis[0] = Type.getObjectType(name);
		System.arraycopy(args, 0, withThis, 1, args.length);
		MethodNode a = new MethodNode(API, Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "uproot$static$" + shadow.name,
				Type.getMethodDescriptor(ret, withThis), null, null);
		a.visitCode();
		int slot = 1;
		for (Type t : args) {
			a.visitVarInsn(t.getOpcode(Opcodes.ILOAD), slot);
			slot += t.getSize();
		}
		a.visitMethodInsn(Opcodes.INVOKESTATIC, declaring, shadow.name, shadow.desc, false);
		a.visitInsn(ret.getOpcode(Opcodes.IRETURN));
		a.visitMaxs(Math.max(slot, ret.getSize()), slot);
		a.visitEnd();
		return a;
	}

	private void redirectCalls(MethodNode shadow, MethodNode adapter) {
		for (MethodNode m : methods) {
			for (var insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn instanceof MethodInsnNode mi && mi.getOpcode() != Opcodes.INVOKESTATIC && mi.name.equals(shadow.name) && mi.desc.equals(shadow.desc)) {
					mi.setOpcode(Opcodes.INVOKESTATIC);
					mi.owner = name;
					mi.name = adapter.name;
					mi.desc = adapter.desc;
					mi.itf = false;
				}
			}
		}
	}

	/** Calls of a member the mixin no longer declares name the target instead, which finds it up its chain. */
	private void retarget(String member, String desc, String target) {
		for (MethodNode m : methods) {
			for (var insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn instanceof MethodInsnNode mi && mi.owner.equals(name) && mi.name.equals(member) && mi.desc.equals(desc)) mi.owner = target;
			}
		}
	}

	private void retargetField(String member, String desc, String target) {
		for (MethodNode m : methods) {
			for (var insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
				if (insn instanceof FieldInsnNode fi && fi.owner.equals(name) && fi.name.equals(member) && fi.desc.equals(desc)) fi.owner = target;
			}
		}
	}

	private static boolean shadow(List<AnnotationNode> annotations) {
		if (annotations == null) return false;
		for (AnnotationNode a : annotations) if (SHADOW.equals(a.desc)) return true;
		return false;
	}

	private String simple() {
		return name.substring(name.lastIndexOf('/') + 1);
	}
}
