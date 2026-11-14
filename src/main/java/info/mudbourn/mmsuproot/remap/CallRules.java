package info.mudbourn.mmsuproot.remap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import info.mudbourn.mmsuproot.map.GameClasses;
import info.mudbourn.mmsuproot.map.Renames;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * The call-shaped rules of the rename table that need no stack tricks: {@code calldesc} (a virtual
 * call whose descriptor changed), {@code callinst} (an instance call that a static helper answers)
 * {@code castadapt} (a cast that a static helper answers) and {@code bridge} (an override whose 26.3 descriptor gained parameters). It also switches
 * invokevirtual and invokeinterface (and the interface flag of static calls) for Minecraft classes
 * that became interfaces or classes. Runs after the name
 * mapping, so owners and descriptors are 26.3 names. The inserted code has no branches, so the
 * frames and the stack limit of existing methods stay valid; the generated methods are straight-line.
 */
final class CallRules extends ClassVisitor {
	private static final int API = Opcodes.ASM9;

	private final Namer namer;
	private final Renames renames;
	private String className;
	private boolean isInterface;
	private final Set<String> declared = new HashSet<>(); // name+desc of the methods the class declares
	private final List<Object[]> superStubs = new ArrayList<>(); // {stub name, super owner, interface flag, Bridge}
	private List<Renames.Bridge> applicable;
	private final List<Object[]> lambdaStubs = new ArrayList<>(); // {stub name, stub descriptor, impl Handle}

	CallRules(ClassVisitor next, Namer namer) {
		super(API, next);
		this.namer = namer;
		this.renames = namer.renames();
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		className = name;
		isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	/** The bridge rules whose owner this class extends or implements. */
	private List<Renames.Bridge> applicable() {
		if (applicable == null) {
			applicable = new ArrayList<>();
			if (!renames.bridges().isEmpty()) {
				Set<String> up = namer.ancestorsOf(className);
				for (Renames.Bridge b : renames.bridges()) if (up.contains(b.owner())) applicable.add(b);
			}
		}
		return applicable;
	}

	/** {helper owner, helper name, rule owner} for an instance call, found on the owner or on anything it extends. */
	private String[] callInst(String owner, String name, String desc) {
		if (!renames.hasCallRule(name)) return null;
		String[] exact = renames.callInst(owner, name, desc);
		if (exact != null) return new String[] {exact[0], exact[1], owner};
		for (String ancestor : namer.ancestorsOf(owner)) {
			String[] m = renames.callInst(ancestor, name, desc);
			if (m != null) return new String[] {m[0], m[1], ancestor};
		}
		return null;
	}

	private String callDesc(String owner, String name, String desc) {
		if (!renames.hasCallRule(name)) return null;
		String exact = renames.callDesc(owner, name, desc);
		if (exact != null) return exact;
		for (String ancestor : namer.ancestorsOf(owner)) {
			String m = renames.callDesc(ancestor, name, desc);
			if (m != null) return m;
		}
		return null;
	}

	/** The helper's descriptor: the receiver (as the rule's owner) in front of the old arguments. */
	private static String helperDesc(String ruleOwner, String desc) {
		return "(L" + ruleOwner + ";" + desc.substring(1);
	}

	private Object rewritten(Object arg) {
		if (arg instanceof Handle g && g.getTag() >= Opcodes.H_INVOKEVIRTUAL && g.getTag() <= Opcodes.H_INVOKEINTERFACE) {
			Handle fixed = shapeHandle(g);
			if (fixed != g) arg = fixed;
		}
		if (!(arg instanceof Handle h) || (h.getTag() != Opcodes.H_INVOKEVIRTUAL && h.getTag() != Opcodes.H_INVOKEINTERFACE)) return arg;
		String[] inst = callInst(h.getOwner(), h.getName(), h.getDesc());
		if (inst != null) return new Handle(Opcodes.H_INVOKESTATIC, inst[0], inst[1], helperDesc(inst[2], h.getDesc()), false);
		String now = callDesc(h.getOwner(), h.getName(), h.getDesc());
		if (now != null && Type.getReturnType(now).equals(Type.getReturnType(h.getDesc()))) return new Handle(h.getTag(), h.getOwner(), h.getName(), now, h.isInterface());
		return arg;
	}

	/**
	 * A lambda or method reference for a functional interface a {@code bridge} rule covers: the interface method has added parameters, so
	 * the lambda is made for the new shape, and its body is a static method of this class that drops the added arguments and calls the
	 * old implementation. Returns the new bootstrap arguments, or null when no rule applies.
	 */
	private Object[] bridgedLambda(String indyName, String indyDesc, Handle bsm, Object[] args) {
		if (!bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") || args.length < 3 || renames.bridges().isEmpty()) return null;
		if (!(args[0] instanceof Type sam) || !(args[1] instanceof Handle impl) || !(args[2] instanceof Type inst)) return null;
		Type itf = Type.getReturnType(indyDesc);
		if (itf.getSort() != Type.OBJECT) return null;
		Set<String> up = new HashSet<>(namer.ancestorsOf(itf.getInternalName()));
		up.add(itf.getInternalName());
		Renames.Bridge rule = null;
		for (Renames.Bridge b : renames.bridges()) {
			if (b.name().equals(indyName) && b.oldDesc().equals(sam.getDescriptor()) && up.contains(b.owner())) rule = b;
		}
		if (rule == null) return null;

		Type[] oldArgs = Type.getArgumentTypes(rule.oldDesc());
		Type[] newArgs = Type.getArgumentTypes(rule.newDesc());
		List<Type> implArgs = new ArrayList<>();
		if (impl.getTag() != Opcodes.H_INVOKESTATIC && impl.getTag() != Opcodes.H_NEWINVOKESPECIAL) implArgs.add(Type.getObjectType(impl.getOwner()));
		implArgs.addAll(List.of(Type.getArgumentTypes(impl.getDesc())));
		List<Type> stubArgs = new ArrayList<>(implArgs);
		List<Type> extra = new ArrayList<>(List.of(newArgs).subList(oldArgs.length, newArgs.length));
		stubArgs.addAll(extra);
		Type ret = impl.getTag() == Opcodes.H_NEWINVOKESPECIAL ? Type.getObjectType(impl.getOwner()) : Type.getReturnType(impl.getDesc());
		String stubDesc = Type.getMethodDescriptor(ret, stubArgs.toArray(new Type[0]));
		String stub = "uproot$lambda$" + lambdaStubs.size();
		lambdaStubs.add(new Object[] {stub, stubDesc, impl, implArgs.size()});

		List<Type> instArgs = new ArrayList<>(List.of(Type.getArgumentTypes(inst.getDescriptor())));
		instArgs.addAll(extra);
		Object[] out = args.clone();
		out[0] = Type.getMethodType(rule.newDesc());
		out[1] = new Handle(Opcodes.H_INVOKESTATIC, className, stub, stubDesc, isInterface);
		out[2] = Type.getMethodType(Type.getReturnType(inst.getDescriptor()), instArgs.toArray(new Type[0]));
		return out;
	}

	/** The static method a bridged lambda runs: calls the old implementation with the arguments it knew and ignores the rest. */
	private void emitLambdaStub(String stub, String desc, Handle impl, int implArgCount) {
		Type[] args = Type.getArgumentTypes(desc);
		Type ret = Type.getReturnType(desc);
		MethodVisitor mv = super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, stub, desc, null, null);
		mv.visitCode();
		int stack = 0;
		if (impl.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
			mv.visitTypeInsn(Opcodes.NEW, impl.getOwner());
			mv.visitInsn(Opcodes.DUP);
			stack += 2;
		}
		int slot = 0;
		for (int i = 0; i < implArgCount; i++) {
			mv.visitVarInsn(args[i].getOpcode(Opcodes.ILOAD), slot);
			slot += args[i].getSize();
			stack += args[i].getSize();
		}
		int opcode = switch (impl.getTag()) {
			case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
			case Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL;
			case Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE;
			default -> Opcodes.INVOKESPECIAL;
		};
		mv.visitMethodInsn(opcode, impl.getOwner(), impl.getName(), impl.getDesc(), impl.isInterface());
		mv.visitInsn(ret.getOpcode(Opcodes.IRETURN));
		for (int i = implArgCount; i < args.length; i++) slot += args[i].getSize();
		mv.visitMaxs(Math.max(stack, ret.getSize()), slot);
		mv.visitEnd();
	}

	/**
	 * A Minecraft class that became an interface (or stopped being one) is called with the other
	 * instruction now. Returns the opcode to use for a call whose owner is a game class.
	 */
	private int shapeOpcode(int opcode, String owner) {
		GameClasses game = namer.game();
		if (opcode == Opcodes.INVOKEVIRTUAL && game.isInterface(owner)) return Opcodes.INVOKEINTERFACE;
		if (opcode == Opcodes.INVOKEINTERFACE && game.isClass(owner)) return Opcodes.INVOKEVIRTUAL;
		return opcode;
	}

	/** The interface flag a call needs: the owner's own shape when it is a game class, else the flag as written. */
	private boolean shapeFlag(String owner, boolean itf) {
		GameClasses game = namer.game();
		if (game.isInterface(owner)) return true;
		if (game.isClass(owner)) return false;
		return itf;
	}

	private Handle shapeHandle(Handle h) {
		int tag = h.getTag();
		if (tag == Opcodes.H_INVOKEVIRTUAL && namer.game().isInterface(h.getOwner())) tag = Opcodes.H_INVOKEINTERFACE;
		else if (tag == Opcodes.H_INVOKEINTERFACE && namer.game().isClass(h.getOwner())) tag = Opcodes.H_INVOKEVIRTUAL;
		boolean itf = shapeFlag(h.getOwner(), h.isInterface());
		if (tag == h.getTag() && itf == h.isInterface()) return h;
		return new Handle(tag, h.getOwner(), h.getName(), h.getDesc(), itf);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		declared.add(name + descriptor);
		MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
		if (mv == null) return null;
		Renames.Bridge own = null;
		if (!name.equals("<init>")) {
			for (Renames.Bridge b : applicable()) if (b.name().equals(name) && b.oldDesc().equals(descriptor)) own = b;
		}
		Renames.Bridge bridge = own;
		return new MethodVisitor(API, mv) {
			@Override
			public void visitInvokeDynamicInsn(String indyName, String indyDesc, Handle bsm, Object... bsmArgs) {
				Object[] mapped = bsmArgs.clone();
				for (int i = 0; i < mapped.length; i++) mapped[i] = rewritten(mapped[i]);
				Object[] adapted = bridgedLambda(indyName, indyDesc, bsm, mapped);
				super.visitInvokeDynamicInsn(indyName, indyDesc, bsm, adapted != null ? adapted : mapped);
			}

			@Override
			public void visitTypeInsn(int opcode, String type) {
				String[] cast = opcode == Opcodes.CHECKCAST ? renames.castAdapt(type) : null;
				if (cast != null) {
					super.visitMethodInsn(Opcodes.INVOKESTATIC, cast[0], cast[1], "(Ljava/lang/Object;)L" + type + ";", false);
					return;
				}
				String[] test = opcode == Opcodes.INSTANCEOF ? renames.instanceAdapt(type) : null;
				if (test != null) {
					super.visitMethodInsn(Opcodes.INVOKESTATIC, test[0], test[1], "(Ljava/lang/Object;)Z", false);
					return;
				}
				super.visitTypeInsn(opcode, type);
			}

			@Override
			public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean itf) {
				if (opcode != Opcodes.INVOKESTATIC && !methodName.equals("<init>") && renames.hasCallDrop(methodName) && dropped(owner, methodName, methodDesc)) {
					dropCall(this, methodDesc);
					return;
				}
				Renames.Bridge superBridge = bridge;
				if (opcode == Opcodes.INVOKESPECIAL && superBridge == null && !methodName.equals("<init>") && namer.game().has(owner)) {
					// a super call of the old shape from a method that is not itself an override (a mixin handler, say)
					for (Renames.Bridge b : applicable()) {
						if (b.name().equals(methodName) && b.oldDesc().equals(methodDesc) && !namer.game().findMethod(owner, methodName, methodDesc)
								&& namer.game().findMethod(owner, methodName, b.newDesc())) superBridge = b;
					}
				}
				if (opcode == Opcodes.INVOKESPECIAL && superBridge != null && methodName.equals(superBridge.name()) && methodDesc.equals(superBridge.oldDesc())) {
					// super.m(old) in the override of m(old): the super class only has m(new) now, which a stub of this class reaches
					String stub = "uproot$super$" + superStubs.size();
					superStubs.add(new Object[] {stub, owner, itf, superBridge});
					super.visitMethodInsn(Opcodes.INVOKESTATIC, className, stub, "(L" + className + ";" + methodDesc.substring(1), isInterface);
					return;
				}
				if (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE) {
					String[] inst = callInst(owner, methodName, methodDesc);
					if (inst != null) {
						super.visitMethodInsn(Opcodes.INVOKESTATIC, inst[0], inst[1], helperDesc(inst[2], methodDesc), false);
						return;
					}
					String now = callDesc(owner, methodName, methodDesc);
					if (now != null) {
						super.visitMethodInsn(shapeOpcode(opcode, owner), owner, methodName, now, shapeFlag(owner, itf));
						Type was = Type.getReturnType(methodDesc);
						if ((was.getSort() == Type.OBJECT || was.getSort() == Type.ARRAY) && !was.equals(Type.getReturnType(now))) {
							super.visitTypeInsn(Opcodes.CHECKCAST, was.getInternalName());
						}
						return;
					}
				}
				super.visitMethodInsn(shapeOpcode(opcode, owner), owner, methodName, methodDesc, shapeFlag(owner, itf));
			}
		};
	}

	@Override
	public void visitEnd() {
		for (Renames.Bridge b : applicable()) {
			if (declared.contains(b.name() + b.oldDesc()) && !declared.contains(b.name() + b.newDesc())) emitBridge(b);
		}
		if (!renames.retBridges().isEmpty()) {
			Set<String> up = namer.ancestorsOf(className);
			for (Renames.RetBridge b : renames.retBridges()) {
				String wide = Type.getMethodDescriptor(Type.getObjectType(b.ret().substring(1, b.ret().length() - 1)), Type.getArgumentTypes(b.desc()));
				if (up.contains(b.owner()) && declared.contains(b.name() + b.desc()) && !declared.contains(b.name() + wide)) emitRetBridge(b, wide);
			}
		}
		for (Object[] l : lambdaStubs) emitLambdaStub((String) l[0], (String) l[1], (Handle) l[2], (int) l[3]);
		for (Object[] s : superStubs) emitSuperStub((String) s[0], (String) s[1], (boolean) s[2], (Renames.Bridge) s[3]);
		super.visitEnd();
	}

	/** Whether a {@code calldrop} rule covers the call, found on the owner or on anything it extends. */
	private boolean dropped(String owner, String name, String desc) {
		if (renames.callDrop(owner, name, desc)) return true;
		for (String ancestor : namer.ancestorsOf(owner)) if (renames.callDrop(ancestor, name, desc)) return true;
		return false;
	}

	/** Replaces an instance call: its arguments and receiver are popped and a null, zero or false stands in for the result. */
	private static void dropCall(MethodVisitor mv, String desc) {
		Type[] args = Type.getArgumentTypes(desc);
		for (int i = args.length - 1; i >= 0; i--) mv.visitInsn(args[i].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP);
		mv.visitInsn(Opcodes.POP);
		Type ret = Type.getReturnType(desc);
		if (ret.getSort() != Type.VOID) pushDefault(mv, ret);
	}

	/** What javac makes for a covariant return: the method with the wide return type, calling the one that is declared. */
	private void emitRetBridge(Renames.RetBridge b, String wide) {
		Type[] args = Type.getArgumentTypes(b.desc());
		MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC, b.name(), wide, null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		int slot = 1;
		for (Type a : args) {
			mv.visitVarInsn(a.getOpcode(Opcodes.ILOAD), slot);
			slot += a.getSize();
		}
		mv.visitMethodInsn(isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, className, b.name(), b.desc(), isInterface);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(slot + 1, slot);
		mv.visitEnd();
		declared.add(b.name() + wide);
	}

	/** {@code m(new)} for a class that only has {@code m(old)}: drops the added trailing parameters and calls {@code m(old)}. */
	private void emitBridge(Renames.Bridge b) {
		Type[] old = Type.getArgumentTypes(b.oldDesc());
		Type[] now = Type.getArgumentTypes(b.newDesc());
		MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, b.name(), b.newDesc(), null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		int slot = 1;
		for (int i = 0; i < now.length; i++) {
			if (i < old.length) {
				mv.visitVarInsn(now[i].getOpcode(Opcodes.ILOAD), slot);
				if (!old[i].equals(now[i]) && old[i].getSort() >= Type.ARRAY) mv.visitTypeInsn(Opcodes.CHECKCAST, old[i].getInternalName());
			}
			slot += now[i].getSize();
		}
		mv.visitMethodInsn(isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, className, b.name(), b.oldDesc(), isInterface);
		mv.visitInsn(Type.getReturnType(b.newDesc()).getOpcode(Opcodes.IRETURN));
		mv.visitMaxs(slot + 1, slot);
		mv.visitEnd();
		declared.add(b.name() + b.newDesc());
	}

	/** A static method that makes the {@code invokespecial} of the new descriptor on behalf of an override of the old one. */
	private void emitSuperStub(String stub, String owner, boolean itf, Renames.Bridge b) {
		Type[] old = Type.getArgumentTypes(b.oldDesc());
		Type[] now = Type.getArgumentTypes(b.newDesc());
		Type ret = Type.getReturnType(b.oldDesc());
		MethodVisitor mv = super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, stub, "(L" + className + ";" + b.oldDesc().substring(1), null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		int slot = 1;
		int stack = 1;
		for (int i = 0; i < now.length; i++) {
			if (i < old.length) {
				mv.visitVarInsn(old[i].getOpcode(Opcodes.ILOAD), slot);
				if (!old[i].equals(now[i]) && now[i].getSort() >= Type.ARRAY) mv.visitTypeInsn(Opcodes.CHECKCAST, now[i].getInternalName());
				slot += old[i].getSize();
			} else {
				pushDefault(mv, now[i]);
			}
			stack += now[i].getSize();
		}
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, b.name(), b.newDesc(), itf);
		mv.visitInsn(ret.getOpcode(Opcodes.IRETURN));
		mv.visitMaxs(Math.max(stack, ret.getSize()), slot);
		mv.visitEnd();
	}

	/** null, zero or false for a parameter the old code never had. */
	private static void pushDefault(MethodVisitor mv, Type t) {
		switch (t.getSort()) {
		case Type.LONG -> mv.visitInsn(Opcodes.LCONST_0);
		case Type.FLOAT -> mv.visitInsn(Opcodes.FCONST_0);
		case Type.DOUBLE -> mv.visitInsn(Opcodes.DCONST_0);
		case Type.ARRAY, Type.OBJECT -> mv.visitInsn(Opcodes.ACONST_NULL);
		default -> mv.visitInsn(Opcodes.ICONST_0);
		}
	}
}
