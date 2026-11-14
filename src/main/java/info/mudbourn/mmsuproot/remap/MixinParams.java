package info.mudbourn.mmsuproot.remap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import info.mudbourn.mmsuproot.map.Renames;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Gives a mixin handler the parameters its target gained. An {@code @Inject} handler lists the target's
 * arguments before the callback; when a {@code bridge} rule says the target method (on the mixin's target
 * class or something it extends) now has more parameters after the old ones, and the game has that method,
 * the handler is replaced by one with the 26.3 parameters that calls the old handler, now unannotated.
 * Runs after the name mapping, so descriptors are 26.3 names. Only handlers whose target is named by a
 * plain method name are touched; a handler cannot use a parameter it never had.
 */
final class MixinParams extends ClassVisitor {
	private static final int API = Opcodes.ASM9;
	private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
	private static final String OPERATION = "com/llamalad7/mixinextras/injector/wrapoperation/Operation";
	private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
	private static final String CALLBACK_RETURNABLE = "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";

	private final Namer namer;
	private final List<String> targets = new ArrayList<>();
	private String className;
	private final List<MethodNode> adapters = new ArrayList<>();

	MixinParams(ClassVisitor next, Namer namer) {
		super(API, next);
		this.namer = namer;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		className = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
		if (!descriptor.equals(MIXIN)) return av;
		return new AnnotationVisitor(API, av) {
			@Override
			public AnnotationVisitor visitArray(String key) {
				AnnotationVisitor inner = super.visitArray(key);
				return new AnnotationVisitor(API, inner) {
					@Override
					public void visit(String k, Object value) {
						if (value instanceof Type t && t.getSort() == Type.OBJECT) targets.add(t.getInternalName());
						else if (value instanceof String s && "targets".equals(key)) targets.add(s.replace('.', '/'));
						super.visit(k, value);
					}
				};
			}
		};
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		if (targets.isEmpty() || name.startsWith("<")) return super.visitMethod(access, name, descriptor, signature, exceptions);
		return new MethodNode(API, access, name, descriptor, signature, exceptions) {
			@Override
			public void visitEnd() {
				super.visitEnd();
				MethodNode wrapper = adapt(this);
				accept(MixinParams.super.visitMethod(this.access, this.name, this.desc, this.signature, this.exceptions.toArray(new String[0])));
				if (wrapper != null) adapters.add(wrapper);
			}
		};
	}

	@Override
	public void visitEnd() {
		inheritedOverrides();
		for (MethodNode m : adapters) m.accept(super.visitMethod(m.access, m.name, m.desc, null, m.exceptions.toArray(new String[0])));
		super.visitEnd();
	}

	/**
	 * An {@code inherited} rule: the mixin's target no longer declares a method that its injectors name, a superclass
	 * does. Mixin cannot inject into a method the target only inherits, so the mixin gets an override that calls the
	 * superclass's method; Mixin merges it into the target and the injectors find it. Behaviour is unchanged.
	 */
	private void inheritedOverrides() {
		for (Renames.Inherited rule : namer.renames().inherited()) {
			if (!targets.contains(rule.target())) continue;
			var own = namer.game().info(rule.target());
			if (own.isEmpty() || own.get().hasMethod(rule.name(), rule.desc()) || own.get().hasMethod(rule.name(), rule.superDesc())) continue;
			String declaring = null;
			for (String c = own.get().superName(); c != null && declaring == null; ) {
				var info = namer.game().info(c);
				if (info.isEmpty()) break;
				if (info.get().hasMethod(rule.name(), rule.superDesc())) declaring = c;
				c = info.get().superName();
			}
			if (declaring == null) continue;
			// the method the injectors name: calls the superclass's
			emitOverride(rule.desc(), Opcodes.ACC_PUBLIC, rule.name(), rule.desc(), declaring, rule.superDesc(), Opcodes.INVOKESPECIAL, declaring);
			// the method the game calls, when the superclass erased the types: what javac's bridge would do
			if (!rule.desc().equals(rule.superDesc())) {
				emitOverride(rule.superDesc(), Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC, rule.name(), rule.superDesc(), className, rule.desc(), Opcodes.INVOKEVIRTUAL, className);
			}
			namer.note("mixin " + className.substring(className.lastIndexOf('/') + 1) + " gets an override of " + rule.name() + " so its injectors can reach it (declared in " + declaring.substring(declaring.lastIndexOf('/') + 1) + ")");
		}
	}

	/** A method {@code name desc} that calls {@code callOwner.name callDesc} on this, casting arguments that differ in type. */
	private void emitOverride(String desc, int access, String name, String declDesc, String callOwner, String callDesc, int op, String ignored) {
		Type[] have = Type.getArgumentTypes(desc);
		Type[] want = Type.getArgumentTypes(callDesc);
		MethodVisitor w = super.visitMethod(access, name, desc, null, null);
		w.visitCode();
		w.visitVarInsn(Opcodes.ALOAD, 0);
		int slot = 1;
		for (int i = 0; i < have.length; i++) {
			w.visitVarInsn(have[i].getOpcode(Opcodes.ILOAD), slot);
			if (have[i].getSort() == Type.OBJECT && !have[i].equals(want[i]) && !namer.ancestorsOf(have[i].getInternalName()).contains(want[i].getInternalName())) {
				w.visitTypeInsn(Opcodes.CHECKCAST, want[i].getInternalName());
			}
			slot += have[i].getSize();
		}
		w.visitMethodInsn(op, callOwner, name, callDesc, false);
		w.visitInsn(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));
		w.visitMaxs(slot + Type.getReturnType(desc).getSize(), slot);
		w.visitEnd();
	}

	/**
	 * If {@code m} is an injector handler whose target gained parameters: strips the annotations from {@code m},
	 * renames it, and returns the replacement that carries them; otherwise returns null and leaves {@code m}.
	 */
	private MethodNode adapt(MethodNode m) {
		Renames.Handler handlerRule = namer.renames().handler(className, m.name);
		if (handlerRule != null) {
			AnnotationNode any = findInjector(m.invisibleAnnotations);
			if (any == null) any = findInjector(m.visibleAnnotations);
			return any == null ? null : adaptHandler(m, any, handlerRule);
		}
		AnnotationNode inject = find(m.invisibleAnnotations);
		if (inject == null) inject = find(m.visibleAnnotations);
		if (inject == null) return null;
		List<String> names = new ArrayList<>();
		for (int i = 0; inject.values != null && i + 1 < inject.values.size(); i += 2) {
			if (inject.values.get(i).equals("method") && inject.values.get(i + 1) instanceof List<?> l) {
				for (Object o : l) {
					String s = String.valueOf(o);
					// wildcards and regexes are left alone; a name with the descriptor 1.21.11 had is read and rewritten below
					if (s.matches("[\\w$]+\\(.*\\).+")) s = s.substring(0, s.indexOf('('));
					else if (s.isEmpty() || !s.matches("[\\w$]+")) return null;
					names.add(s);
				}
			}
		}
		if (names.isEmpty()) return null;
		Type[] args = Type.getArgumentTypes(m.desc);
		int callback = -1;
		for (int i = 0; i < args.length && callback < 0; i++) {
			if (args[i].getSort() == Type.OBJECT && (args[i].getInternalName().equals(CALLBACK) || args[i].getInternalName().equals(CALLBACK_RETURNABLE))) callback = i;
		}
		if (callback < 0) return null;

		Renames.Bridge rule = null;
		for (String target : targets) {
			Set<String> up = namer.ancestorsOf(target);
			for (String name : names) {
				for (Renames.Bridge b : namer.renames().bridges()) {
					if (!b.name().equals(name) || !up.contains(b.owner()) || !namer.game().findMethod(target, name, b.newDesc())) continue;
					Type[] old = Type.getArgumentTypes(b.oldDesc());
					if (old.length != callback) continue;
					boolean same = true;
					for (int i = 0; i < old.length; i++) same &= old[i].equals(args[i]) || narrower(args[i], old[i]);
					if (!same) continue;
					if (rule != null && !rule.newDesc().equals(b.newDesc())) return null; // targets disagree: not ours to guess
					rule = b;
				}
			}
		}
		if (rule == null) return null;

		Type[] now = Type.getArgumentTypes(rule.newDesc());
		for (int i = 0; i < callback; i++) now[i] = args[i]; // a handler may name a narrower type than the rule does
		int extra = now.length - callback;
		Type[] widened = new Type[args.length + extra];
		System.arraycopy(now, 0, widened, 0, now.length);
		System.arraycopy(args, callback, widened, now.length, args.length - callback);

		// every old parameter is passed on from the same place; the added ones are dropped
		Renames.Arg[] sources = new Renames.Arg[args.length];
		for (int i = 0; i < args.length; i++) sources[i] = Renames.Arg.param(i < callback ? i : i + extra);

		StringBuilder added = new StringBuilder();
		for (int i = callback; i < now.length; i++) added.append(added.length() > 0 ? ", " : "").append(now[i].getClassName());
		namer.note("mixin handler " + className.substring(className.lastIndexOf('/') + 1) + "." + m.name + " takes the parameters its target gained (" + added + ")");
		return rewrap(m, inject, widened, sources, now, "mms_uproot$" + m.name + "$" + callback, callback, extra, null, null, 0);
	}

	/**
	 * A {@code handler} rule: the handler of this mixin takes the parameters of the rule's descriptor, the target's in 26.3,
	 * and passes its old ones on as the argument map says. Returns the replacement, or null when the rule does not fit the handler.
	 */
	private MethodNode adaptHandler(MethodNode m, AnnotationNode inject, Renames.Handler rule) {
		Type[] args = Type.getArgumentTypes(m.desc);
		Type[] widened = Type.getArgumentTypes(rule.newDesc());
		int callback = callbackIndex(args);
		int newCallback = callbackIndex(widened);
		String where = className.substring(className.lastIndexOf('/') + 1) + "." + m.name;
		// a wrap operation handler has its captured values after the Operation; they keep their order and sit after the new one
		boolean wrap = callback >= 0 && args[callback].getInternalName().equals(OPERATION);
		int suffix = args.length - 1 - callback;
		if (callback < 0 || newCallback < 0 || rule.args().size() != callback) {
			namer.note("handler rule for " + where + " does not fit the handler: it takes " + args.length + " parameters, the callback at " + callback + ", and the rule maps " + rule.args().size());
			return null;
		}
		Renames.Arg[] sources = new Renames.Arg[args.length];
		for (int i = 0; i < callback; i++) {
			sources[i] = rule.args().get(i);
			if (sources[i].index() >= newCallback) {
				namer.note("handler rule for " + where + " maps parameter " + i + " to " + sources[i].index() + ", past the target's parameters");
				return null;
			}
			if (sources[i].index() >= 0 && !compatible(widened[sources[i].index()], args[i])) {
				namer.note("handler rule for " + where + " maps parameter " + i + " (" + args[i].getClassName() + ") to a " + widened[sources[i].index()].getClassName());
				return null;
			}
		}
		sources[callback] = Renames.Arg.param(newCallback);
		// the values a wrap handler takes after the Operation are the enclosing method's parameters: each old one is found again, by type, among the new ones
		boolean[] used = new boolean[widened.length];
		for (int i = callback + 1; i < args.length; i++) {
			int found = -1;
			for (int k = widened.length - 1; k > newCallback && found < 0; k--) if (!used[k] && widened[k].equals(args[i])) found = k;
			if (found < 0) {
				namer.note("handler rule for " + where + " finds no " + args[i].getClassName() + " among the target's parameters after the Operation");
				return null;
			}
			used[found] = true;
			sources[i] = Renames.Arg.param(found);
		}
		Type[] now = wrap ? null : new Type[newCallback];
		if (now != null) System.arraycopy(widened, 0, now, 0, newCallback);
		namer.note("mixin handler " + where + " takes the target's 26.3 parameters (" + rule.newDesc().substring(0, rule.newDesc().indexOf(')') + 1) + ")");
		int[] dropped = null;
		if (wrap) {
			// the old handler calls the original with the arguments it knew: the Operation it gets maps them to the ones the target takes now
			// (by default the same ones, leaving out those the target no longer takes; an op= field of the rule says otherwise)
			dropped = rule.opSpec() != null ? rule.opSpec() : java.util.stream.IntStream.range(0, callback).filter(i -> sources[i].index() >= 0).toArray();
			boolean identity = dropped.length == callback;
			for (int i = 0; identity && i < dropped.length; i++) identity = dropped[i] == i;
			if (identity) dropped = null;
		}
		return rewrap(m, inject, widened, sources, now, "mms_uproot$" + m.name + "$handler", -1, 0, dropped, rule.guard(), newCallback);
	}

	private static int callbackIndex(Type[] args) {
		for (int i = 0; i < args.length; i++) {
			if (args[i].getSort() == Type.OBJECT && (args[i].getInternalName().equals(CALLBACK) || args[i].getInternalName().equals(CALLBACK_RETURNABLE)
					|| args[i].getInternalName().equals(OPERATION))) return i;
		}
		return -1;
	}

	/** Whether a value of type {@code have} can be passed where {@code want} is taken: the same type, or objects where one extends the other. */
	private boolean compatible(Type have, Type want) {
		if (have.equals(want)) return true;
		if (have.getSort() != Type.OBJECT || want.getSort() != Type.OBJECT) return false;
		return namer.ancestorsOf(have.getInternalName()).contains(want.getInternalName()) || namer.ancestorsOf(want.getInternalName()).contains(have.getInternalName());
	}

	/**
	 * Strips the annotations from {@code m}, renames it, and returns a method with the parameters {@code newArgs} that carries
	 * them and calls it. {@code sources} says, per parameter of {@code m}, where its value comes from. {@code now} are the target's
	 * parameters, which a method named with its old descriptor in the annotation is rewritten to. {@code callback} and {@code extra}
	 * place the parameter annotations of a handler whose parameters grew (callback is -1 when they do not).
	 */
	private MethodNode rewrap(MethodNode m, AnnotationNode inject, Type[] newArgs, Renames.Arg[] sources, Type[] now, String renamed, int callback, int extra, int[] dropped, String[] guard, int guardArgs) {
		boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
		MethodNode w = new MethodNode(API, m.access, m.name, Type.getMethodDescriptor(Type.getReturnType(m.desc), newArgs), null, m.exceptions.toArray(new String[0]));
		w.visibleAnnotations = m.visibleAnnotations;
		w.invisibleAnnotations = m.invisibleAnnotations;
		if (callback >= 0) {
			w.visibleParameterAnnotations = widen(m.visibleParameterAnnotations, callback, extra);
			w.invisibleParameterAnnotations = widen(m.invisibleParameterAnnotations, callback, extra);
			w.visibleAnnotableParameterCount = m.visibleAnnotableParameterCount == 0 ? 0 : m.visibleAnnotableParameterCount + extra;
			w.invisibleAnnotableParameterCount = m.invisibleAnnotableParameterCount == 0 ? 0 : m.invisibleAnnotableParameterCount + extra;
			m.visibleParameterAnnotations = null;
			m.invisibleParameterAnnotations = null;
			m.visibleAnnotableParameterCount = 0;
			m.invisibleAnnotableParameterCount = 0;
		}
		if (callback < 0) {
			// a handler rule: each parameter annotation (a local capture, say) follows its parameter to where the rule puts it
			w.visibleParameterAnnotations = moveParameterAnnotations(m.visibleParameterAnnotations, sources, newArgs.length);
			w.invisibleParameterAnnotations = moveParameterAnnotations(m.invisibleParameterAnnotations, sources, newArgs.length);
			m.visibleParameterAnnotations = null;
			m.invisibleParameterAnnotations = null;
			m.visibleAnnotableParameterCount = 0;
			m.invisibleAnnotableParameterCount = 0;
		}
		m.visibleAnnotations = null;
		m.invisibleAnnotations = null;
		m.name = renamed;
		if ((m.access & Opcodes.ACC_PUBLIC) != 0 || (m.access & Opcodes.ACC_PROTECTED) != 0) m.access = (m.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE;

		Type[] old = Type.getArgumentTypes(m.desc);
		int[] slots = new int[newArgs.length];
		int slot = isStatic ? 0 : 1;
		for (int i = 0; i < newArgs.length; i++) {
			slots[i] = slot;
			slot += newArgs[i].getSize();
		}
		w.visitCode();
		org.objectweb.asm.Label run = null;
		if (guard != null) {
			// the handler only runs when the helper, given the target's parameters, says so (the handler returns nothing)
			run = new org.objectweb.asm.Label();
			StringBuilder helperDesc = new StringBuilder("(");
			for (int i = 0; i < guardArgs; i++) {
				w.visitVarInsn(newArgs[i].getOpcode(Opcodes.ILOAD), slots[i]);
				helperDesc.append(newArgs[i].getDescriptor());
			}
			w.visitMethodInsn(Opcodes.INVOKESTATIC, guard[0], guard[1], helperDesc.append(")Z").toString(), false);
			w.visitJumpInsn(Opcodes.IFNE, run);
			w.visitInsn(Opcodes.RETURN);
			w.visitLabel(run);
			w.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
		}
		if (!isStatic) w.visitVarInsn(Opcodes.ALOAD, 0);
		int pushed = isStatic ? 0 : 1;
		for (int i = 0; i < old.length; i++) {
			Renames.Arg from = sources[i];
			if (from.index() >= 0) {
				w.visitVarInsn(newArgs[from.index()].getOpcode(Opcodes.ILOAD), slots[from.index()]);
				if (dropped != null && old[i].getSort() == Type.OBJECT && old[i].getInternalName().equals(OPERATION)) {
					// the Operation, mapped: which old call argument (or captured parameter after the Operation, as -n-1) each new one takes
					w.visitIntInsn(Opcodes.BIPUSH, dropped.length);
					w.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
					for (int k = 0; k < dropped.length; k++) {
						w.visitInsn(Opcodes.DUP);
						w.visitIntInsn(Opcodes.BIPUSH, k);
						w.visitIntInsn(Opcodes.BIPUSH, dropped[k]);
						w.visitInsn(Opcodes.IASTORE);
					}
					int capturedFrom = guardArgs + 1;
					w.visitIntInsn(Opcodes.BIPUSH, newArgs.length - capturedFrom);
					w.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
					for (int k = capturedFrom; k < newArgs.length; k++) {
						w.visitInsn(Opcodes.DUP);
						w.visitIntInsn(Opcodes.BIPUSH, k - capturedFrom);
						w.visitVarInsn(Opcodes.ALOAD, slots[k]);
						w.visitInsn(Opcodes.AASTORE);
					}
					w.visitMethodInsn(Opcodes.INVOKESTATIC, "info/mudbourn/mmsuproot/shim/compat/LegacyOperations", "remapping", "(L" + OPERATION + ";[I[Ljava/lang/Object;)L" + OPERATION + ";", false);
					pushed += 10;
				}
				if (!newArgs[from.index()].equals(old[i]) && old[i].getSort() == Type.OBJECT && !namer.ancestorsOf(newArgs[from.index()].getInternalName()).contains(old[i].getInternalName())) {
					w.visitTypeInsn(Opcodes.CHECKCAST, old[i].getInternalName());
				}
			} else if (from.helperOwner() != null) {
				w.visitMethodInsn(Opcodes.INVOKESTATIC, from.helperOwner(), from.helperName(), "()" + old[i].getDescriptor(), false);
			} else {
				switch (old[i].getSort()) {
				case Type.LONG -> w.visitInsn(Opcodes.LCONST_0);
				case Type.FLOAT -> w.visitInsn(Opcodes.FCONST_0);
				case Type.DOUBLE -> w.visitInsn(Opcodes.DCONST_0);
				case Type.OBJECT, Type.ARRAY -> w.visitInsn(Opcodes.ACONST_NULL);
				default -> w.visitInsn(Opcodes.ICONST_0);
				}
			}
			pushed += old[i].getSize();
		}
		int op = isStatic ? Opcodes.INVOKESTATIC : (m.access & Opcodes.ACC_PRIVATE) != 0 ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL;
		w.visitMethodInsn(op, className, m.name, m.desc, false);
		w.visitInsn(Type.getReturnType(m.desc).getOpcode(Opcodes.IRETURN));
		w.visitMaxs(Math.max(pushed, Type.getReturnType(m.desc).getSize()), slot);
		w.visitEnd();

		// a method named with its 1.21.11 descriptor now names the 26.3 one
		String nowArgs = now == null ? null : Type.getMethodDescriptor(Type.VOID_TYPE, now);
		if (nowArgs != null) nowArgs = nowArgs.substring(0, nowArgs.indexOf(')') + 1);
		for (int i = 0; nowArgs != null && inject.values != null && i + 1 < inject.values.size(); i += 2) {
			if (inject.values.get(i).equals("method") && inject.values.get(i + 1) instanceof List<?> l) {
				@SuppressWarnings("unchecked") List<Object> mutable = (List<Object>) l;
				for (int k = 0; k < mutable.size(); k++) {
					String s = String.valueOf(mutable.get(k));
					int paren = s.indexOf('(');
					if (paren > 0 && s.matches("[\\w$<>]+\\(.*\\).+")) mutable.set(k, s.substring(0, paren) + nowArgs + s.substring(s.indexOf(')') + 1));
				}
			}
		}
		return w;
	}

	@SuppressWarnings("unchecked")
	private static List<AnnotationNode>[] moveParameterAnnotations(List<AnnotationNode>[] old, Renames.Arg[] sources, int newCount) {
		if (old == null) return null;
		List<AnnotationNode>[] out = (List<AnnotationNode>[]) new List<?>[newCount];
		for (int i = 0; i < old.length && i < sources.length; i++) {
			if (old[i] != null && sources[i].index() >= 0) out[sources[i].index()] = old[i];
		}
		return out;
	}

	/** Whether {@code narrow} is a class that is, or extends, the class {@code wide}. */
	private boolean narrower(Type narrow, Type wide) {
		return narrow.getSort() == Type.OBJECT && wide.getSort() == Type.OBJECT && namer.ancestorsOf(narrow.getInternalName()).contains(wide.getInternalName());
	}

	/** The injector annotation of a method, whichever kind: Mixin's own or MixinExtras'. */
	private static AnnotationNode findInjector(List<AnnotationNode> list) {
		if (list == null) return null;
		for (AnnotationNode a : list) {
			if (a.desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") || a.desc.startsWith("Lcom/llamalad7/mixinextras/injector/")) return a;
		}
		return null;
	}

	private static AnnotationNode find(List<AnnotationNode> list) {
		if (list == null) return null;
		for (AnnotationNode a : list) if (a.desc.equals(INJECT)) return a;
		return null;
	}

	@SuppressWarnings("unchecked")
	private static List<AnnotationNode>[] widen(List<AnnotationNode>[] from, int at, int extra) {
		if (from == null) return null;
		List<AnnotationNode>[] out = (List<AnnotationNode>[]) new List[from.length + extra];
		for (int i = 0; i < from.length; i++) out[i < at ? i : i + extra] = from[i];
		return out;
	}
}
