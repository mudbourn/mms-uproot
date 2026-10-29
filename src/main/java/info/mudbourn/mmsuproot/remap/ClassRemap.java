package info.mudbourn.mmsuproot.remap;

import info.mudbourn.mmsuproot.map.Renames;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.MethodNode;

/**
 * Rewrites one class from intermediary names to 26.3 names, in memory. Besides the bytecode it
 * rewrites the strings in annotations, because mixins name their targets there.
 */
public final class ClassRemap {
	private static final int API = Opcodes.ASM9;

	private ClassRemap() { }

	public static byte[] remap(byte[] bytes, Namer namer) {
		ClassReader reader = new ClassReader(bytes);
		ClassWriter writer = new ClassWriter(0);
		ClassVisitor chain = new CtorAdapters(new CallRules(new FieldMoves(writer, namer), namer), namer);
		chain = new ClassRemapper(chain, new NameRemapper(namer));
		chain = new AnnotationStrings(chain, namer);
		reader.accept(chain, 0);
		return writer.toByteArray();
	}

	/** Maps names through the {@link Namer}; intermediary names are unique, so owners do not matter. */
	private static final class NameRemapper extends Remapper {
		private final Namer namer;

		NameRemapper(Namer namer) {
			super(API);
			this.namer = namer;
		}

		@Override
		public String map(String internalName) {
			return namer.cls(internalName);
		}

		@Override
		public String mapMethodName(String owner, String name, String descriptor) {
			return namer.method(owner, name, descriptor);
		}

		@Override
		public String mapInvokeDynamicMethodName(String name, String descriptor) {
			return namer.method(null, name, descriptor);
		}

		@Override
		public String mapFieldName(String owner, String name, String descriptor) {
			return namer.field(owner, name);
		}

		@Override
		public String mapRecordComponentName(String owner, String name, String descriptor) {
			return namer.field(owner, name);
		}
	}

	/**
	 * Rewrites strings in annotations and drops mixin {@code require}, so a missing target disables one
	 * injector. Bare member names in injector and accessor annotations take this class as their owner.
	 */
	private static final class AnnotationStrings extends ClassVisitor {
		private final Namer namer;
		private String className;

		AnnotationStrings(ClassVisitor next, Namer namer) {
			super(API, next);
			this.namer = namer;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			className = name;
			super.visit(version, access, name, signature, superName, interfaces);
		}

		private AnnotationVisitor wrap(AnnotationVisitor av, String desc) {
			if (av == null) return null;
			boolean injector = desc != null && (desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") || desc.startsWith("Lcom/llamalad7/mixinextras/"));
			boolean accessor = desc != null && desc.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;");
			boolean invoker = desc != null && desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;");
			return new Strings(av, namer, injector, injector || accessor || invoker ? className : null, accessor, null);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			return wrap(super.visitAnnotation(descriptor, visible), descriptor);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
			if (fv == null) return null;
			return new FieldVisitor(API, fv) {
				@Override
				public AnnotationVisitor visitAnnotation(String d, boolean visible) {
					return wrap(super.visitAnnotation(d, visible), d);
				}
			};
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
			if (mv == null) return null;
			return new MethodVisitor(API, mv) {
				@Override
				public AnnotationVisitor visitAnnotation(String d, boolean visible) {
					return wrap(super.visitAnnotation(d, visible), d);
				}

				@Override
				public AnnotationVisitor visitParameterAnnotation(int parameter, String d, boolean visible) {
					return wrap(super.visitParameterAnnotation(parameter, d, visible), d);
				}
			};
		}
	}

	private static final class Strings extends AnnotationVisitor {
		private final Namer namer;
		private final boolean injector;
		private final String owner; // class that names bare members, or null when bare names are not members
		private final boolean field; // a bare name with no parenthesis is a field
		private final String element; // the array this visitor is inside, or null

		Strings(AnnotationVisitor next, Namer namer, boolean injector, String owner, boolean field, String element) {
			super(API, next);
			this.namer = namer;
			this.injector = injector;
			this.owner = owner;
			this.field = field;
			this.element = element;
		}

		@Override
		public void visit(String name, Object value) {
			if (injector && "require".equals(name)) return;
			String key = name != null ? name : element;
			boolean member = owner != null && ("method".equals(key) || "target".equals(key) || "value".equals(key) || "field".equals(key));
			super.visit(name, value instanceof String s ? namer.rewrite(s, member ? owner : null, field) : value);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			AnnotationVisitor av = super.visitAnnotation(name, descriptor);
			return av == null ? null : new Strings(av, namer, false, owner, false, null);
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			AnnotationVisitor av = super.visitArray(name);
			return av == null ? null : new Strings(av, namer, false, owner, field, name);
		}
	}

	/** Static constants such as {@code EntityType.ZOMBIE} that 26.3 keeps in another class. */
	private static final class FieldMoves extends ClassVisitor {
		private final Namer namer;

		FieldMoves(ClassVisitor next, Namer namer) {
			super(API, next);
			this.namer = namer;
		}

		/** A static field handle, as {@code ldc} and bootstrap arguments carry them. */
		private Object moved(Object o) {
			if (!(o instanceof Handle h) || h.getTag() != Opcodes.H_GETSTATIC) return o;
			String[] move = namer.renames().fieldMove(h.getOwner(), h.getName());
			if (move != null) return new Handle(h.getTag(), move[0], move[1], h.getDesc(), h.isInterface());
			String owner = namer.movedOwner(h.getOwner(), h.getName(), h.getDesc());
			return owner.equals(h.getOwner()) ? h : new Handle(h.getTag(), owner, h.getName(), h.getDesc(), h.isInterface());
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
			if (mv == null) return null;
			return new MethodVisitor(API, mv) {
				@Override
				public void visitFieldInsn(int opcode, String owner, String fieldName, String fieldDesc) {
					if (opcode == Opcodes.GETSTATIC) {
						String[] move = namer.renames().fieldMove(owner, fieldName);
						if (move != null) {
							owner = move[0];
							fieldName = move[1];
						} else {
							owner = namer.movedOwner(owner, fieldName, fieldDesc);
						}
					}
					super.visitFieldInsn(opcode, owner, fieldName, fieldDesc);
				}

				@Override
				public void visitLdcInsn(Object value) {
					super.visitLdcInsn(moved(value));
				}

				@Override
				public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... args) {
					Object[] mapped = args.clone();
					for (int i = 0; i < mapped.length; i++) mapped[i] = moved(mapped[i]);
					super.visitInvokeDynamicInsn(name, desc, bsm, mapped);
				}
			};
		}
	}

	/**
	 * Static calls the rename table redirects go to Uproot's helper. Calls to constructors that gained a
	 * parameter in 26.3 get the extra argument from a static field,
	 * as the rename table says; calls to constructors with a {@code ctoradapt} rule send their arguments
	 * through a static helper that returns the new ones. Runs after the name mapping, so owner and
	 * descriptors are 26.3 names. The writer does not compute frames: the inserted code has no branches,
	 * so existing frames stay valid, the stack limit grows and a method that adapts uses one spare local
	 * past its own (never read after the adapted call, so no frame needs to mention it).
	 */
	private static final class CtorAdapters extends ClassVisitor {
		private final Namer namer;

		private String className;
		private boolean isInterface;
		private int version;
		private final java.util.List<Object[]> bridges = new java.util.ArrayList<>(); // {name, Ctor or CtorAdapt, owner}

		CtorAdapters(ClassVisitor next, Namer namer) {
			super(API, next);
			this.namer = namer;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			this.className = name;
			this.version = version;
			this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
			super.visit(version, access, name, signature, superName, interfaces);
		}

		/**
		 * A constructor reference ({@code Foo::new}) to a constructor that gained a parameter cannot take the
		 * extra argument at the call, so the reference points at a small static method of this class that does.
		 * A static method reference to a call a {@code callmove} rule redirects points at the new target.
		 */
		private Object bridged(Object arg) {
			if (arg instanceof Handle s && s.getTag() == Opcodes.H_INVOKESTATIC) { // Foo::staticCall with a callmove rule
				String[] move = callMove(s.getOwner(), s.getName(), s.getDesc());
				return move == null ? arg : new Handle(Opcodes.H_INVOKESTATIC, move[0], move[1], s.getDesc(), false);
			}
			if (!(arg instanceof Handle h) || h.getTag() != Opcodes.H_NEWINVOKESPECIAL || !h.getName().equals("<init>")) return arg;
			Renames.Ctor c = namer.renames().ctor(h.getOwner(), h.getDesc());
			Renames.CtorAdapt a = c == null ? namer.renames().ctorAdapt(h.getOwner(), h.getDesc()) : null;
			if (c == null && a == null) return arg;
			String name = "uproot$new$" + bridges.size();
			bridges.add(new Object[] {name, c != null ? c : a, h.getOwner()});
			return new Handle(Opcodes.H_INVOKESTATIC, className, name, Type.getMethodDescriptor(Type.getObjectType(h.getOwner()), Type.getArgumentTypes(c != null ? c.oldDesc() : a.oldDesc())), isInterface);
		}

		/** The redirect for a static call, found on the owner or on anything the owner extends (static methods are inherited). */
		private String[] callMove(String owner, String name, String desc) {
			String[] exact = namer.renames().callMove(owner, name, desc);
			if (exact != null || !namer.renames().hasCallMove(name)) return exact;
			for (String ancestor : namer.ancestorsOf(owner)) {
				String[] m = namer.renames().callMove(ancestor, name, desc);
				if (m != null) return m;
			}
			return null;
		}

		@Override
		public void visitEnd() {
			for (Object[] b : bridges) {
				String owner = (String) b[2];
				if (b[1] instanceof Renames.CtorAdapt a) {
					Type[] oldArgs = Type.getArgumentTypes(a.oldDesc());
					MethodVisitor mv = super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, (String) b[0],
							Type.getMethodDescriptor(Type.getObjectType(owner), oldArgs), null, null);
					mv.visitCode();
					mv.visitTypeInsn(Opcodes.NEW, owner);
					mv.visitInsn(Opcodes.DUP);
					int used = 0;
					for (Type t : oldArgs) {
						mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), used);
						used += t.getSize();
					}
					adapt(mv, owner, a, used);
					mv.visitInsn(Opcodes.ARETURN);
					mv.visitMaxs(2 + stackFor(a), used + 1);
					mv.visitEnd();
					continue;
				}
				Renames.Ctor c = (Renames.Ctor) b[1];
				Type[] args = Type.getArgumentTypes(c.oldDesc());
				MethodVisitor mv = super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, (String) b[0],
						Type.getMethodDescriptor(Type.getObjectType(owner), args), null, null);
				mv.visitCode();
				mv.visitTypeInsn(Opcodes.NEW, owner);
				mv.visitInsn(Opcodes.DUP);
				Renames.Inserted in = c.inserted().get(0);
				int slot = 0;
				for (int i = 0; i <= args.length; i++) {
					if (i == in.position()) {
						if (in.owner() == null) mv.visitLdcInsn(Integer.valueOf(in.name()));
						else mv.visitFieldInsn(Opcodes.GETSTATIC, in.owner(), in.name(), in.desc());
					}
					if (i < args.length) {
						mv.visitVarInsn(args[i].getOpcode(Opcodes.ILOAD), slot);
						slot += args[i].getSize();
					}
				}
				mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", c.newDesc(), false);
				mv.visitInsn(Opcodes.ARETURN);
				mv.visitMaxs(slot + 4, slot);
				mv.visitEnd();
			}
			super.visitEnd();
		}

		/** The stack the argument unpacking of one adapted call needs on top of what the method already had. */
		private static int stackFor(Renames.CtorAdapt a) {
			int slots = 0;
			for (Type t : Type.getArgumentTypes(a.newDesc())) slots += t.getSize();
			return slots + 3;
		}

		/**
		 * With the old arguments on the stack: calls the helper, keeps its {@code Object[]} in local
		 * {@code spare}, pushes the new arguments from it (cast, or unboxed for primitives) and calls the
		 * new constructor. Nothing sits between the receiver and the arguments but the arguments, so this
		 * serves {@code new X(...)} and {@code super(...)} alike (the helper is static).
		 */
		private static void adapt(MethodVisitor mv, String owner, Renames.CtorAdapt a, int spare) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, a.helperOwner(), a.helperName(),
					Type.getMethodDescriptor(Type.getType(Object[].class), Type.getArgumentTypes(a.oldDesc())), false);
			mv.visitVarInsn(Opcodes.ASTORE, spare);
			Type[] now = Type.getArgumentTypes(a.newDesc());
			for (int i = 0; i < now.length; i++) {
				mv.visitVarInsn(Opcodes.ALOAD, spare);
				pushInt(mv, i);
				mv.visitInsn(Opcodes.AALOAD);
				unpack(mv, now[i]);
			}
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", a.newDesc(), false);
		}

		private static void pushInt(MethodVisitor mv, int n) {
			if (n >= 0 && n <= 5) mv.visitInsn(Opcodes.ICONST_0 + n);
			else if (n < 128) mv.visitIntInsn(Opcodes.BIPUSH, n);
			else mv.visitIntInsn(Opcodes.SIPUSH, n);
		}

		/** Turns the {@code Object} on top of the stack into a value of type {@code t}. */
		private static void unpack(MethodVisitor mv, Type t) {
			String box;
			String method;
			switch (t.getSort()) {
			case Type.OBJECT -> {
				if (!t.getInternalName().equals("java/lang/Object")) mv.visitTypeInsn(Opcodes.CHECKCAST, t.getInternalName());
				return;
			}
			case Type.ARRAY -> {
				mv.visitTypeInsn(Opcodes.CHECKCAST, t.getDescriptor());
				return;
			}
			case Type.BOOLEAN -> { box = "java/lang/Boolean"; method = "booleanValue"; }
			case Type.CHAR -> { box = "java/lang/Character"; method = "charValue"; }
			case Type.BYTE -> { box = "java/lang/Byte"; method = "byteValue"; }
			case Type.SHORT -> { box = "java/lang/Short"; method = "shortValue"; }
			case Type.INT -> { box = "java/lang/Integer"; method = "intValue"; }
			case Type.LONG -> { box = "java/lang/Long"; method = "longValue"; }
			case Type.FLOAT -> { box = "java/lang/Float"; method = "floatValue"; }
			default -> { box = "java/lang/Double"; method = "doubleValue"; }
			}
			mv.visitTypeInsn(Opcodes.CHECKCAST, box);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, box, method, "()" + t.getDescriptor(), false);
		}

		/**
		 * The method is held back until its end so the adapter knows {@code maxLocals}, whose slot is
		 * the first one nothing uses.
		 */
		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
			if (next == null) return null;
			return new MethodNode(API, access, name, descriptor, signature, exceptions) {
				@Override
				public void visitEnd() {
					super.visitEnd();
					accept(rewriter(next, maxLocals));
				}
			};
		}

		private MethodVisitor rewriter(MethodVisitor next, int spare) {
			return new MethodVisitor(API, next) {
				private boolean adapted;
				private int extraStack;
				private boolean spareUsed;

				@Override
				public void visitInvokeDynamicInsn(String indyName, String indyDesc, Handle bsm, Object... bsmArgs) {
					Object[] mapped = bsmArgs.clone();
					for (int i = 0; i < mapped.length; i++) mapped[i] = bridged(mapped[i]);
					super.visitInvokeDynamicInsn(indyName, indyDesc, bsm, mapped);
				}

				@Override
				public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean itf) {
					if (opcode == Opcodes.INVOKESPECIAL && methodName.equals("<init>")) {
						Renames.CtorAdapt a = namer.renames().ctorAdapt(owner, methodDesc);
						if (a != null) {
							adapt(mv, owner, a, spare);
							extraStack = Math.max(extraStack, stackFor(a));
							spareUsed = true;
							return;
						}
					}
					String[] move = opcode == Opcodes.INVOKESTATIC ? callMove(owner, methodName, methodDesc) : null;
					if (move != null) {
						super.visitMethodInsn(opcode, move[0], move[1], methodDesc, false);
						return;
					}
					Renames.Ctor c = opcode == Opcodes.INVOKESPECIAL && methodName.equals("<init>") ? namer.renames().ctor(owner, methodDesc) : null;
					if (c == null) {
						super.visitMethodInsn(opcode, owner, methodName, methodDesc, itf);
						return;
					}
					Renames.Inserted in = c.inserted().get(0);
					if (in.owner() == null) super.visitLdcInsn(Integer.valueOf(in.name()));
					else super.visitFieldInsn(Opcodes.GETSTATIC, in.owner(), in.name(), in.desc());
					if (in.position() < Type.getArgumentTypes(c.oldDesc()).length) super.visitInsn(Opcodes.SWAP);
					adapted = true;
					super.visitMethodInsn(opcode, owner, methodName, c.newDesc(), itf);
				}

				@Override
				public void visitMaxs(int maxStack, int maxLocals) {
					super.visitMaxs(maxStack + extraStack + (adapted ? 1 : 0), spareUsed ? Math.max(maxLocals, spare + 1) : maxLocals);
					adapted = false;
					extraStack = 0;
					spareUsed = false;
				}
			};
		}
	}
}
