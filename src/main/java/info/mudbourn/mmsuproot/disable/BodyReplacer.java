package info.mudbourn.mmsuproot.disable;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

/**
 * Swallows a method's original code and writes a default body in its place. Annotations are
 * forwarded (or dropped) first, because a mixin handler has to stay a handler.
 *
 * <p>A constructor must still call a super constructor to verify, so it repeats the super call the
 * original made. Each argument takes the constructor's own parameter of the same type when there is
 * one (a renderer keeps its context), else a default ({@code null}, 0, false).
 */
final class BodyReplacer extends MethodVisitor {
	private static final String EMPTY_BUILDER = "net/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder";

	private final MethodVisitor target;
	private final String name;
	private final String desc;
	private final String[] superCall;
	private final boolean keepAnnotations;

	/** {@code superCall} is {owner, desc} of the constructor call to repeat, or null. */
	BodyReplacer(MethodVisitor target, String name, String desc, String[] superCall, boolean keepAnnotations) {
		super(Opcodes.ASM9);
		this.target = target;
		this.name = name;
		this.desc = desc;
		this.superCall = superCall;
		this.keepAnnotations = keepAnnotations;
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		return keepAnnotations ? target.visitAnnotation(descriptor, visible) : null;
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		return keepAnnotations ? target.visitTypeAnnotation(typeRef, typePath, descriptor, visible) : null;
	}

	@Override
	public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
		return keepAnnotations ? target.visitParameterAnnotation(parameter, descriptor, visible) : null;
	}

	@Override
	public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
		if (keepAnnotations) target.visitAnnotableParameterCount(parameterCount, visible);
	}

	@Override
	public void visitEnd() {
		target.visitCode();
		if (name.equals("<init>")) {
			target.visitVarInsn(Opcodes.ALOAD, 0);
			String owner = superCall == null ? null : superCall[0];
			String callDesc = superCall == null ? "()V" : superCall[1];
			Type[] own = Type.getArgumentTypes(desc);
			int[] slots = new int[own.length];
			int slot = 1;
			for (int i = 0; i < own.length; i++) {
				slots[i] = slot;
				slot += own[i].getSize();
			}
			boolean[] used = new boolean[own.length];
			for (Type t : Type.getArgumentTypes(callDesc)) {
				int found = -1;
				if (t.getSort() == Type.OBJECT) {
					for (int i = 0; i < own.length && found < 0; i++) {
						if (!used[i] && own[i].equals(t)) found = i;
					}
				}
				if (found < 0) {
					pushDefault(target, t);
				} else {
					used[found] = true;
					target.visitVarInsn(Opcodes.ALOAD, slots[found]);
				}
			}
			target.visitMethodInsn(Opcodes.INVOKESPECIAL, owner == null ? "java/lang/Object" : owner, "<init>", callDesc, false);
			target.visitInsn(Opcodes.RETURN);
		} else if (name.equals("<clinit>")) {
			target.visitInsn(Opcodes.RETURN);
		} else {
			Type ret = Type.getReturnType(desc);
			if (ret.getSort() == Type.OBJECT && ret.getInternalName().equals(EMPTY_BUILDER)) {
				// a mob's attribute builder is built and registered even when the mob is off; an empty one registers harmlessly
				target.visitTypeInsn(Opcodes.NEW, EMPTY_BUILDER);
				target.visitInsn(Opcodes.DUP);
				target.visitMethodInsn(Opcodes.INVOKESPECIAL, EMPTY_BUILDER, "<init>", "()V", false);
			} else if (ret.getSort() != Type.VOID) {
				pushDefault(target, ret);
			}
			target.visitInsn(ret.getOpcode(Opcodes.IRETURN));
		}
		target.visitMaxs(0, 0);
		target.visitEnd();
	}

	static void pushDefault(MethodVisitor mv, Type t) {
		switch (t.getSort()) {
			case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> mv.visitInsn(Opcodes.ICONST_0);
			case Type.LONG -> mv.visitInsn(Opcodes.LCONST_0);
			case Type.FLOAT -> mv.visitInsn(Opcodes.FCONST_0);
			case Type.DOUBLE -> mv.visitInsn(Opcodes.DCONST_0);
			default -> mv.visitInsn(Opcodes.ACONST_NULL);
		}
	}
}
