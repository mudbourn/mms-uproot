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

/**
 * Rewrites one class from intermediary names to 26.3 names, in memory. Besides the bytecode it
 * rewrites the strings in annotations, because mixins name their targets there.
 */
public final class ClassRemap {
	/** {@code desc} with the parameter types {@code extra} (descriptor syntax, no parentheses) added after its last parameter. */
	static String withExtraParams(String desc, String extra) {
		int close = desc.indexOf(')');
		return desc.substring(0, close) + extra + desc.substring(close);
	}

	private static final int API = Opcodes.ASM9;

	private ClassRemap() { }

	public static byte[] remap(byte[] bytes, Namer namer) {
		ClassReader reader = new ClassReader(bytes);
		ClassWriter writer = new ClassWriter(0);
		ClassVisitor chain = new MixinShadows(new MixinParams(new CtorAdapters(new CallRules(new FieldMoves(new SuperSwap(writer, namer), namer), namer), namer), namer), namer);
		chain = new ClassRemapper(chain, new NameRemapper(namer, namer.renames().mixinTargetsOf(reader.getClassName())));
		chain = new LambdaNames(chain, namer);
		chain = new BridgeMerge(chain, namer);
		chain = new AnnotationStrings(chain, namer);
		chain = new StringNames(chain, namer);
		reader.accept(chain, 0);
		return writer.toByteArray();
	}

	/**
	 * A {@code super} rule: the mod class extends the stand-in the rule names, which itself extends what the class extended.
	 * The class's calls of its old superclass's constructor and methods (invokespecial) go to the stand-in, which inherits them.
	 */
	private static final class SuperSwap extends ClassVisitor {
		private final Namer namer;
		private String oldSuper;
		private String newSuper;

		SuperSwap(ClassVisitor next, Namer namer) {
			super(API, next);
			this.namer = namer;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			String swap = namer.renames().superSwap(name);
			if (swap != null) {
				oldSuper = superName;
				newSuper = swap;
				superName = swap;
			}
			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
			if (mv == null || newSuper == null) return mv;
			return new MethodVisitor(API, mv) {
				@Override
				public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean itf) {
					super.visitMethodInsn(opcode, opcode == Opcodes.INVOKESPECIAL && owner.equals(oldSuper) ? newSuper : owner, methodName, methodDesc, itf);
				}
			};
		}
	}

	/** Leaves out a compiler bridge that a member clash merged into the method it forwards to (see {@link Clashes}). Runs before the names change. */
	private static final class BridgeMerge extends ClassVisitor {
		private final Namer namer;
		private String className;

		BridgeMerge(ClassVisitor next, Namer namer) {
			super(API, next);
			this.namer = namer;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			className = name;
			super.visit(version, access, name, signature, superName, interfaces);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			if (namer.droppedBridge(className, name, descriptor)) return null;
			return super.visitMethod(access, name, descriptor, signature, exceptions);
		}
	}

	/**
	 * A lambda or method reference names the method of the functional interface it implements ({@code invokedynamic foo()Lmod/I;}), and the
	 * name mapper cannot tell which interface that is. When {@code foo} of that mod interface was renamed by a clash (see {@link Clashes}), the
	 * call site says so here, before the names are mapped, so the lambda still implements the method. Runs before the names change.
	 */
	private static final class LambdaNames extends ClassVisitor {
		private final Namer namer;

		LambdaNames(ClassVisitor next, Namer namer) {
			super(API, next);
			this.namer = namer;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
			if (mv == null) return null;
			return new MethodVisitor(API, mv) {
				@Override
				public void visitInvokeDynamicInsn(String indyName, String indyDesc, Handle bsm, Object... args) {
					if (bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory") && args.length > 0 && args[0] instanceof Type sam && sam.getSort() == Type.METHOD) {
						Type itf = Type.getReturnType(indyDesc);
						if (itf.getSort() == Type.OBJECT) {
							String renamed = namer.clashed(itf.getInternalName(), indyName, sam.getDescriptor());
							if (renamed != null) indyName = renamed;
						}
					}
					super.visitInvokeDynamicInsn(indyName, indyDesc, bsm, args);
				}
			};
		}
	}

	/** Maps names through the {@link Namer}; intermediary names are unique, so owners do not matter. */
	private static final class NameRemapper extends Remapper {
		private final Namer namer;

		/** Classes this mixin's {@code mixintarget} rules replace: its handlers take the new target where they took the old one. */
		private final java.util.Map<String, String> targetSwap;

		NameRemapper(Namer namer, java.util.Map<String, String> targetSwap) {
			super(API);
			this.namer = namer;
			this.targetSwap = targetSwap;
		}

		@Override
		public String map(String internalName) {
			String mapped = namer.cls(internalName);
			return targetSwap.isEmpty() ? mapped : targetSwap.getOrDefault(mapped, mapped);
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
			return namer.field(owner, name, descriptor);
		}

		@Override
		public String mapRecordComponentName(String owner, String name, String descriptor) {
			return namer.field(owner, name);
		}
	}

	/**
	 * Rewrites a string constant that names an intermediary class ({@code net/minecraft/class_123}, a descriptor with one, or the
	 * dotted form), or a member of a mod class in the form {@code Lowner;name(desc)} (which a member clash may have renamed). Code that builds
	 * classes or looks members up by reflection carries its names as strings, which the class remapper does not see. A string that is only a
	 * member name ({@code Class.getMethod("foo")}) cannot be told from any other text and is left alone, as {@link Clashes} says. Constants in
	 * fields (compile-time constants) and {@code ldc} both count.
	 */
	private static final class StringNames extends ClassVisitor {
		private final Namer namer;

		StringNames(ClassVisitor next, Namer namer) {
			super(API, next);
			this.namer = namer;
		}

		private Object names(Object value) {
			if (value instanceof String s && (s.contains("net/minecraft/class_") || s.contains("net.minecraft.class_") || namer.refersToModMember(s))) return namer.rewrite(s);
			return value;
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			return super.visitField(access, name, descriptor, signature, names(value));
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
			if (mv == null) return null;
			return new MethodVisitor(API, mv) {
				@Override
				public void visitLdcInsn(Object value) {
					super.visitLdcInsn(names(value));
				}
			};
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

		/**
		 * {@code memberName} and {@code memberDesc} are the member the annotation is on (a method or a field of this class), which an
		 * {@code @Accessor} or {@code @Invoker} without a name takes its target from.
		 */
		private AnnotationVisitor wrap(AnnotationVisitor av, String desc, String memberName, String memberDesc, boolean onMethod) {
			if (av == null) return null;
			boolean injector = desc != null && (desc.startsWith("Lorg/spongepowered/asm/mixin/injection/") || desc.startsWith("Lcom/llamalad7/mixinextras/"));
			boolean accessor = desc != null && desc.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;");
			boolean invoker = desc != null && desc.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;");
			String targetDesc = null; // the descriptor of the member a bare name stands for
			if (onMethod && memberDesc != null) {
				if (invoker) targetDesc = memberDesc;
				else if (accessor) {
					Type[] args = Type.getArgumentTypes(memberDesc);
					targetDesc = args.length == 1 ? args[0].getDescriptor() : Type.getReturnType(memberDesc).getDescriptor();
				}
			}
			Strings strings = new Strings(av, namer, injector, injector || accessor || invoker ? className : null, accessor, null, targetDesc);
			if ((accessor || invoker) && onMethod && memberName != null) strings.inferName(memberName);
			return strings;
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			return wrap(super.visitAnnotation(descriptor, visible), descriptor, null, null, false);
		}

		@Override
		public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
			FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
			if (fv == null) return null;
			return new FieldVisitor(API, fv) {
				@Override
				public AnnotationVisitor visitAnnotation(String d, boolean visible) {
					return wrap(super.visitAnnotation(d, visible), d, name, descriptor, false);
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
					return wrap(super.visitAnnotation(d, visible), d, name, descriptor, true);
				}

				@Override
				public AnnotationVisitor visitParameterAnnotation(int parameter, String d, boolean visible) {
					return wrap(super.visitParameterAnnotation(parameter, d, visible), d, null, null, false);
				}
			};
		}
	}

	private static final class Strings extends AnnotationVisitor {
		/** {@code get}, {@code is}, {@code set}, {@code call} and {@code invoke}: the prefixes Mixin takes off an accessor or invoker's name to find its target (read from Mixin's AccessorInfo). */
		private static final java.util.regex.Pattern INFLECT = java.util.regex.Pattern.compile("^(get|is|set|call|invoke)(([A-Z])(.*?))(_\\$md.*)?$");

		private final Namer namer;
		private final boolean injector;
		private final String owner; // class that names bare members, or null when bare names are not members
		private final boolean field; // a bare name with no parenthesis is a field
		private final String element; // the array this visitor is inside, or null
		private final String targetDesc; // descriptor of the member a bare name with none stands for (an invoker's method, an accessor's field), or null
		private String inferred; // the target Mixin takes from the name of an accessor or invoker method that names none, or null
		private boolean named; // the annotation named its target

		Strings(AnnotationVisitor next, Namer namer, boolean injector, String owner, boolean field, String element, String targetDesc) {
			super(API, next);
			this.namer = namer;
			this.injector = injector;
			this.owner = owner;
			this.field = field;
			this.element = element;
			this.targetDesc = targetDesc;
		}

		/**
		 * An {@code @Accessor} or {@code @Invoker} that names no target takes it from its method's name ({@code getFoo} reads {@code foo}); when a clash
		 * renamed that member, the annotation has to say so, since the method's name no longer leads to it.
		 */
		void inferName(String methodName) {
			java.util.regex.Matcher m = INFLECT.matcher(methodName);
			if (!m.matches()) return;
			String rest = m.group(2);
			boolean allUpper = rest.equals(rest.toUpperCase(java.util.Locale.ROOT));
			inferred = (allUpper ? m.group(3) : m.group(3).toLowerCase(java.util.Locale.ROOT)) + m.group(4);
		}

		@Override
		public void visitEnd() {
			if (inferred != null && !named && owner != null && targetDesc != null) {
				String renamed = namer.clashedMember(inferred, owner, field, targetDesc);
				if (renamed != null) super.visit("value", renamed);
			}
			super.visitEnd();
		}

		@Override
		public void visit(String name, Object value) {
			if (injector && "require".equals(name)) return;
			String key = name != null ? name : element;
			boolean member = owner != null && ("method".equals(key) || "target".equals(key) || "value".equals(key) || "field".equals(key));
			if (member && element == null && "value".equals(key)) named = true;
			super.visit(name, value instanceof String s ? namer.rewrite(s, member ? owner : null, field, targetDesc) : value);
		}

		@Override
		public void visitEnum(String name, String descriptor, String value) {
			// a local capture that no longer fits skips the injector (a dead hook, reported) and does not stop the class or the game
			boolean capture = injector && "locals".equals(name) && "Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;".equals(descriptor);
			super.visitEnum(name, descriptor, capture && (value.equals("CAPTURE_FAILHARD") || value.equals("CAPTURE_FAILEXCEPTION")) ? "CAPTURE_FAILSOFT" : value);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			AnnotationVisitor av = super.visitAnnotation(name, descriptor);
			return av == null ? null : new Strings(av, namer, false, owner, false, null, null);
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			AnnotationVisitor av = super.visitArray(name);
			return av == null ? null : new Strings(av, namer, false, owner, field, name, targetDesc);
		}
	}

	/** Static constants such as {@code EntityType.ZOMBIE} that 26.3 keeps in another class. */
	private static final class FieldMoves extends ClassVisitor {
		private final Namer namer;

		private String className = "?";

		FieldMoves(ClassVisitor next, Namer namer) {
			super(API, next);
			this.namer = namer;
		}

		@Override
		public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
			className = name;
			super.visit(version, access, name, signature, superName, interfaces);
		}

		/**
		 * A {@code fieldget} or {@code fieldset} rule for the access, found on the owner or on anything it extends (a mixin
		 * class extends its targets), is emitted as a call: of the owner's method, or of the rule's static helper, which
		 * takes the object the rule names (and, for a write, the value) in the order the stack already holds them.
		 * Returns false when no rule applies.
		 */
		private boolean fieldRule(String owner, String fieldName, boolean write, String fieldDesc, MethodVisitor mv) {
			Renames renames = namer.renames();
			if (!renames.hasFieldRule(fieldName)) return false;
			String ruleOwner = null;
			String[] rule = null;
			String getter = null;
			for (String candidate : candidates(owner)) {
				if (write) rule = renames.fieldSet(candidate, fieldName);
				else getter = renames.fieldGet(candidate, fieldName);
				if (rule != null || getter != null) {
					ruleOwner = candidate;
					break;
				}
			}
			if (rule == null && getter == null) return false;
			String helperOwner = null;
			String name;
			if (write) {
				helperOwner = rule[0];
				name = rule[1];
			} else {
				int dot = getter.lastIndexOf('.');
				helperOwner = dot < 0 ? null : getter.substring(0, dot);
				name = dot < 0 ? getter : getter.substring(dot + 1);
			}
			String desc = write ? "(" + fieldDesc + ")V" : "()" + fieldDesc;
			if (helperOwner != null) {
				String helperDesc = write ? "(L" + ruleOwner + ";" + fieldDesc + ")V" : "(L" + ruleOwner + ";)" + fieldDesc;
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, helperOwner, name, helperDesc, false);
			} else {
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, desc, false);
			}
			return true;
		}

		/** The owner, then everything it extends or implements (a mixin class counts its targets). */
		private java.util.Collection<String> candidates(String owner) {
			java.util.List<String> out = new java.util.ArrayList<>();
			out.add(owner);
			out.addAll(namer.ancestorsOf(owner));
			return out;
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
					if (opcode == Opcodes.GETFIELD && fieldRule(owner, fieldName, false, fieldDesc, this)) return;
					if (opcode == Opcodes.PUTFIELD && fieldRule(owner, fieldName, true, fieldDesc, this)) return;
					if (opcode == Opcodes.GETSTATIC) {
						String[] move = namer.renames().fieldMove(owner, fieldName);
						if (move != null) {
							owner = move[0];
							fieldName = move[1];
						} else {
							owner = namer.movedOwner(owner, fieldName, fieldDesc);
						}
					}
					if (opcode == Opcodes.GETFIELD && namer.game().has(owner) && namer.game().isPrivateField(owner, fieldName, fieldDesc)
							&& namer.game().findMethod(owner, fieldName, "()" + fieldDesc)) {
						// a field that 26.3 made private (a record component, say) is read through its accessor
						namer.audit(className, Opcodes.INVOKEVIRTUAL, owner, fieldName, "()" + fieldDesc, false);
						super.visitMethodInsn(namer.game().isInterface(owner) ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, owner, fieldName, "()" + fieldDesc, namer.game().isInterface(owner));
						return;
					}
					namer.audit(className, opcode, owner, fieldName, fieldDesc, true);
					super.visitFieldInsn(opcode, owner, fieldName, fieldDesc);
				}

				@Override
				public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean itf) {
					namer.audit(className, opcode, owner, methodName, methodDesc, false);
					super.visitMethodInsn(opcode, owner, methodName, methodDesc, itf);
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
	 * descriptors are 26.3 names. The writer does not compute frames: the inserted code has no branches
	 * and uses no local, so existing frames stay valid and only the stack limit grows. (Mixin runs the field
	 * initialisers of a mixin inside the target's, and cannot take a local there.)
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
				return move == null ? arg : new Handle(Opcodes.H_INVOKESTATIC, move[0], move[1], move.length > 2 ? move[2] : s.getDesc(), namer.game().isInterface(move[0]));
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
					adapt(mv, owner, a);
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
		 * With the old arguments on the stack: calls the helper, which leaves its {@code Object[]}, then lays the new
		 * arguments out under it (cast, or unboxed for primitives) one by one and drops the array. Only the stack is
		 * used. Nothing sits between the receiver and the arguments but the arguments, so this serves
		 * {@code new X(...)} and {@code super(...)} alike (the helper is static).
		 */
		private static void adapt(MethodVisitor mv, String owner, Renames.CtorAdapt a) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, a.helperOwner(), a.helperName(),
					Type.getMethodDescriptor(Type.getType(Object[].class), Type.getArgumentTypes(a.oldDesc())), false);
			Type[] now = Type.getArgumentTypes(a.newDesc());
			for (int i = 0; i < now.length; i++) {
				Type t = now[i];
				mv.visitInsn(Opcodes.DUP);
				pushInt(mv, i);
				mv.visitInsn(Opcodes.AALOAD);
				unpack(mv, t);
				if (t.getSize() == 2) {
					mv.visitInsn(Opcodes.DUP2_X1);
					mv.visitInsn(Opcodes.POP2);
				} else {
					mv.visitInsn(Opcodes.SWAP);
				}
			}
			mv.visitInsn(Opcodes.POP);
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

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
			return next == null ? null : rewriter(next);
		}

		private MethodVisitor rewriter(MethodVisitor next) {
			return new MethodVisitor(API, next) {
				private boolean adapted;
				private int extraStack;

				@Override
				public void visitInvokeDynamicInsn(String indyName, String indyDesc, Handle bsm, Object... bsmArgs) {
					Object[] mapped = bsmArgs.clone();
					for (int i = 0; i < mapped.length; i++) mapped[i] = bridged(mapped[i]);
					super.visitInvokeDynamicInsn(indyName, indyDesc, bsm, mapped);
				}

				@Override
				public void visitMethodInsn(int opcode, String owner, String methodName, String methodDesc, boolean itf) {
					String[] invoker = opcode == Opcodes.INVOKEINTERFACE || opcode == Opcodes.INVOKEVIRTUAL ? namer.renames().invokerParam(owner, methodName) : null;
					if (invoker != null && invoker[0].equals(methodDesc)) {
						// a call of an invoker whose target gained trailing parameters: the new ones get their zero value
						int words = 0;
						for (Type t : Type.getArgumentTypes("(" + invoker[1] + ")V")) {
							switch (t.getSort()) {
								case Type.LONG -> super.visitInsn(Opcodes.LCONST_0);
								case Type.DOUBLE -> super.visitInsn(Opcodes.DCONST_0);
								case Type.FLOAT -> super.visitInsn(Opcodes.FCONST_0);
								case Type.OBJECT, Type.ARRAY -> super.visitInsn(Opcodes.ACONST_NULL);
								default -> super.visitInsn(Opcodes.ICONST_0);
							}
							words += t.getSize();
						}
						extraStack = Math.max(extraStack, words);
						super.visitMethodInsn(opcode, owner, methodName, ClassRemap.withExtraParams(methodDesc, invoker[1]), itf);
						return;
					}
					if (opcode == Opcodes.INVOKESPECIAL && methodName.equals("<init>")) {
						Renames.CtorAdapt a = namer.renames().ctorAdapt(owner, methodDesc);
						if (a != null) {
							adapt(mv, owner, a);
							extraStack = Math.max(extraStack, stackFor(a));
							return;
						}
					}
					String[] move = opcode == Opcodes.INVOKESTATIC ? callMove(owner, methodName, methodDesc) : null;
					if (move != null) {
						super.visitMethodInsn(opcode, move[0], move[1], move.length > 2 ? move[2] : methodDesc, namer.game().isInterface(move[0]));
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
					super.visitMaxs(maxStack + extraStack + (adapted ? 1 : 0), maxLocals);
					adapted = false;
					extraStack = 0;
				}
			};
		}
	}
}
