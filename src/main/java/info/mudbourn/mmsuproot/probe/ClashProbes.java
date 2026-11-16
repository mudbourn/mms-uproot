package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Method;

import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Member clashes (remap.Clashes), on the one class of the pack that has one a mod needs to run: Guard Villagers' guard declares its own
 * {@code setChargingCrossbow(boolean)} next to the one it implements from the crossbow interface, which share a name once named.
 */
final class ClashProbes {
	private ClashProbes() { }

	static List<Probe> all() {
		return List.of(Probe.steps("clash.guard-two-charging-methods", 100, ClashProbes::guard),
			Probe.once("clash.override-family-and-references", ClashProbes::family));
	}

	private static final String P = "uproot/probe/clash/";
	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";

	/**
	 * Classes written in memory that stand in for mod classes (the pack has one clash only, so the cases it lacks are made): an interface {@code I}
	 * and a class {@code B} that implements it and has the clash (its own {@code probeToggle(Z)V} next to {@code method_900001(Z)V}, which the name table
	 * maps to the same name; likewise a field), a subclass {@code C}, an unrelated implementer {@code D}, a class {@code E} that gets {@code probeToggle}
	 * from a superclass {@code G} and {@code I} both, a lambda of {@code I}, and mixins on {@code B} with an invoker, an accessor and a shadow. They are
	 * remapped by the same code the mods go through, and the result is read back: the whole override family and every reference must carry the new name.
	 */
	private static String family(Probe.Ctx c) throws Exception {
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("uproot-clash-probe");
		java.util.Map<String, byte[]> in = new java.util.LinkedHashMap<>();
		in.put("I", clazz(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, "I", "java/lang/Object", null, cw -> abstractMethod(cw, "probeToggle", "(Z)V")));
		in.put("B", clazz(Opcodes.ACC_PUBLIC, "B", "java/lang/Object", new String[] {P + "I"}, cw -> {
			method(cw, "probeToggle", "(Z)V");
			method(cw, "method_900001", "(Z)V");
			cw.visitField(Opcodes.ACC_PUBLIC, "probeFlag", "Z", null, null).visitEnd();
			cw.visitField(Opcodes.ACC_PUBLIC, "field_900002", "Z", null, null).visitEnd();
		}));
		in.put("C", clazz(Opcodes.ACC_PUBLIC, "C", P + "B", null, cw -> method(cw, "probeToggle", "(Z)V")));
		in.put("D", clazz(Opcodes.ACC_PUBLIC, "D", "java/lang/Object", new String[] {P + "I"}, cw -> method(cw, "probeToggle", "(Z)V")));
		in.put("G", clazz(Opcodes.ACC_PUBLIC, "G", "java/lang/Object", null, cw -> method(cw, "probeToggle", "(Z)V")));
		in.put("E", clazz(Opcodes.ACC_PUBLIC, "E", P + "G", new String[] {P + "I"}, cw -> { }));
		in.put("User", clazz(Opcodes.ACC_PUBLIC, "User", "java/lang/Object", null, cw -> {
			org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "use", "(L" + P + "B;L" + P + "I;)V", null, null);
			mv.visitCode();
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, P + "B", "probeToggle", "(Z)V", false);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, P + "B", "method_900001", "(Z)V", false);
			mv.visitVarInsn(Opcodes.ALOAD, 2);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, P + "I", "probeToggle", "(Z)V", true);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitFieldInsn(Opcodes.GETFIELD, P + "B", "probeFlag", "Z");
			mv.visitInsn(Opcodes.POP);
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitFieldInsn(Opcodes.GETFIELD, P + "B", "field_900002", "Z");
			mv.visitInsn(Opcodes.POP);
			mv.visitInvokeDynamicInsn("probeToggle", "()L" + P + "I;", new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false),
				Type.getMethodType("(Z)V"), new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, P + "User", "lambda$use$0", "(Z)V", false), Type.getMethodType("(Z)V"));
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitMaxs(2, 3);
			mv.visitEnd();
			org.objectweb.asm.MethodVisitor lambda = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "lambda$use$0", "(Z)V", null, null);
			lambda.visitCode();
			lambda.visitInsn(Opcodes.RETURN);
			lambda.visitMaxs(0, 1);
			lambda.visitEnd();
		}));
		in.put("Access", mixin("Access", true, cw -> {
			org.objectweb.asm.MethodVisitor named = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "callToggle", "(Z)V", null, null);
			AnnotationVisitor a = named.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Invoker;", true);
			a.visit("value", "probeToggle");
			a.visitEnd();
			named.visitEnd();
			org.objectweb.asm.MethodVisitor inferred = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "invokeProbeToggle", "(Z)V", null, null);
			inferred.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Invoker;", true).visitEnd();
			inferred.visitEnd();
			org.objectweb.asm.MethodVisitor getter = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "flagOf", "()Z", null, null);
			AnnotationVisitor g = getter.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true);
			g.visit("value", "probeFlag");
			g.visitEnd();
			getter.visitEnd();
			org.objectweb.asm.MethodVisitor getter2 = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getProbeFlag", "()Z", null, null);
			getter2.visitAnnotation("Lorg/spongepowered/asm/mixin/gen/Accessor;", true).visitEnd();
			getter2.visitEnd();
		}));
		in.put("Shadows", mixin("Shadows", false, cw -> {
			org.objectweb.asm.MethodVisitor shadow = cw.visitMethod(Opcodes.ACC_PRIVATE, "probeToggle", "(Z)V", null, null);
			shadow.visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", true).visitEnd();
			shadow.visitCode();
			shadow.visitInsn(Opcodes.RETURN);
			shadow.visitMaxs(0, 2);
			shadow.visitEnd();
			cw.visitField(Opcodes.ACC_PRIVATE, "probeFlag", "Z", null, null).visitAnnotation("Lorg/spongepowered/asm/mixin/Shadow;", true).visitEnd();
			org.objectweb.asm.MethodVisitor inject = cw.visitMethod(Opcodes.ACC_PRIVATE, "handler", "()V", null, null);
			AnnotationVisitor at = inject.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", true);
			AnnotationVisitor targets = at.visitArray("method");
			targets.visit(null, "probeToggle(Z)V");
			targets.visit(null, "probeToggle");
			targets.visit(null, "L" + P + "B;probeToggle(Z)V");
			targets.visitEnd();
			at.visitEnd();
			inject.visitCode();
			inject.visitVarInsn(Opcodes.ALOAD, 0);
			inject.visitInsn(Opcodes.ICONST_1);
			inject.visitMethodInsn(Opcodes.INVOKESPECIAL, P + "Shadows", "probeToggle", "(Z)V", false);
			inject.visitVarInsn(Opcodes.ALOAD, 0);
			inject.visitFieldInsn(Opcodes.GETFIELD, P + "Shadows", "probeFlag", "Z");
			inject.visitInsn(Opcodes.POP);
			inject.visitInsn(Opcodes.RETURN);
			inject.visitMaxs(2, 1);
			inject.visitEnd();
		}));

		info.mudbourn.mmsuproot.map.NameTable table = new info.mudbourn.mmsuproot.map.NameTable();
		table.methods.put("method_900001", "probeToggle");
		table.fields.put("field_900002", "probeFlag");
		info.mudbourn.mmsuproot.remap.ModClasses mods = new info.mudbourn.mmsuproot.remap.ModClasses();
		for (java.util.Map.Entry<String, byte[]> e : in.entrySet()) {
			java.nio.file.Path file = dir.resolve(e.getKey() + ".class");
			java.nio.file.Files.write(file, e.getValue());
			mods.put(P + e.getKey(), file);
		}
		java.nio.file.Path reportFile = dir.resolve("report.json");
		info.mudbourn.mmsuproot.remap.Namer namer = new info.mudbourn.mmsuproot.remap.Namer(table,
			info.mudbourn.mmsuproot.map.Renames.parse(new java.io.BufferedReader(new java.io.StringReader(""))),
			new info.mudbourn.mmsuproot.map.GameClasses(ClashProbes.class.getClassLoader(), List.of()), mods,
			new info.mudbourn.mmsuproot.boot.Report(reportFile).mod("probe"));
		java.util.Map<String, org.objectweb.asm.tree.ClassNode> out = new java.util.LinkedHashMap<>();
		for (java.util.Map.Entry<String, byte[]> e : in.entrySet()) {
			org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
			new org.objectweb.asm.ClassReader(info.mudbourn.mmsuproot.remap.ClassRemap.remap(e.getValue(), namer)).accept(node, 0);
			out.put(e.getKey(), node);
		}

		String renamed = "probeToggle$clash";
		for (String name : List.of("I", "B", "C", "D", "G")) {
			if (!has(out.get(name), renamed, "(Z)V")) throw new Probe.Fail(name + " of the override family was not renamed to " + renamed + ": " + methods(out.get(name)));
		}
		if (!has(out.get("B"), "probeToggle", "(Z)V")) throw new Probe.Fail("B lost the method the game name keeps: " + methods(out.get("B")));
		if (has(out.get("C"), "probeToggle", "(Z)V") || has(out.get("D"), "probeToggle", "(Z)V") || has(out.get("G"), "probeToggle", "(Z)V")) throw new Probe.Fail("a class of the family kept the old name");
		if (!has(out.get("B"), "probeToggle$clash", "(Z)V") || out.get("B").fields.stream().noneMatch(f -> f.name.equals("probeFlag$clash"))) throw new Probe.Fail("B's own field was not renamed: " + out.get("B").fields.stream().map(f -> f.name).toList());
		String calls = calls(out.get("User"));
		if (!calls.contains("B.probeToggle$clash(Z)V") || !calls.contains("B.probeToggle(Z)V") || !calls.contains("I.probeToggle$clash(Z)V")) throw new Probe.Fail("calls are " + calls);
		if (!calls.contains("indy probeToggle$clash")) throw new Probe.Fail("the lambda of the interface keeps the old name: " + calls);
		if (!calls.contains("probeFlag$clash") || !calls.contains(".probeFlag ")) throw new Probe.Fail("field reads are " + calls);
		String access = annotations(out.get("Access"));
		for (String want : List.of("callToggle=probeToggle$clash", "invokeProbeToggle=probeToggle$clash", "flagOf=probeFlag$clash", "getProbeFlag=probeFlag$clash")) {
			if (!access.contains(want)) throw new Probe.Fail("invoker/accessor " + want + " missing in " + access);
		}
		org.objectweb.asm.tree.ClassNode shadows = out.get("Shadows");
		if (!has(shadows, renamed, "(Z)V") || shadows.fields.stream().noneMatch(f -> f.name.equals("probeFlag$clash"))) throw new Probe.Fail("the mixin's shadow members were not renamed: " + methods(shadows));
		String strings = annotations(shadows);
		if (!strings.contains("probeToggle$clash(Z)V") || !strings.contains(",probeToggle$clash,") || !strings.contains(P + "B;probeToggle$clash(Z)V")) throw new Probe.Fail("method strings are " + strings);
		long unresolved = namer.clashes().stream().filter(x -> x.unresolved()).count();
		if (unresolved != 0) throw new Probe.Fail(unresolved + " clashes left unresolved");
		return "the family I, B, C, D, G (and E, through G and I) got probeToggle$clash with its calls, a lambda, an invoker, an accessor, shadows and method strings; B's field clash too";
	}

	private static byte[] clazz(int access, String name, String parent, String[] interfaces, java.util.function.Consumer<org.objectweb.asm.ClassWriter> body) {
		org.objectweb.asm.ClassWriter cw = new org.objectweb.asm.ClassWriter(0);
		cw.visit(Opcodes.V21, access, P + name, null, parent, interfaces);
		body.accept(cw);
		cw.visitEnd();
		return cw.toByteArray();
	}

	private static byte[] mixin(String name, boolean itf, java.util.function.Consumer<org.objectweb.asm.ClassWriter> body) {
		return clazz(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | (itf ? Opcodes.ACC_INTERFACE : 0), name, "java/lang/Object", null, cw -> {
			AnnotationVisitor a = cw.visitAnnotation(MIXIN, false);
			AnnotationVisitor v = a.visitArray("value");
			v.visit(null, Type.getObjectType(P + "B"));
			v.visitEnd();
			a.visitEnd();
			body.accept(cw);
		});
	}

	private static void method(org.objectweb.asm.ClassWriter cw, String name, String desc) {
		org.objectweb.asm.MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, desc, null, null);
		mv.visitCode();
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 2);
		mv.visitEnd();
	}

	private static void abstractMethod(org.objectweb.asm.ClassWriter cw, String name, String desc) {
		cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, name, desc, null, null).visitEnd();
	}

	private static boolean has(org.objectweb.asm.tree.ClassNode node, String name, String desc) {
		return node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc));
	}

	private static List<String> methods(org.objectweb.asm.tree.ClassNode node) {
		return node.methods.stream().map(m -> m.name + m.desc).toList();
	}

	/** Every call, field read and lambda of the class, as {@code Owner.name desc} (the owner without its package). */
	private static String calls(org.objectweb.asm.tree.ClassNode node) {
		StringBuilder sb = new StringBuilder();
		for (org.objectweb.asm.tree.MethodNode m : node.methods) {
			for (var insn : m.instructions) {
				if (insn instanceof org.objectweb.asm.tree.MethodInsnNode mi) sb.append(mi.owner.substring(mi.owner.lastIndexOf('/') + 1)).append('.').append(mi.name).append(mi.desc).append('\n');
				else if (insn instanceof org.objectweb.asm.tree.FieldInsnNode fi) sb.append(fi.owner.substring(fi.owner.lastIndexOf('/') + 1)).append('.').append(fi.name).append(' ').append(fi.desc).append('\n');
				else if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode di) sb.append("indy ").append(di.name).append('\n');
			}
		}
		return sb.toString();
	}

	/** The annotation values of the methods and fields of the class: {@code member=value} for a plain value, {@code member=[a,b,]} for an array. */
	private static String annotations(org.objectweb.asm.tree.ClassNode node) {
		StringBuilder sb = new StringBuilder();
		for (org.objectweb.asm.tree.MethodNode m : node.methods) {
			for (var list : java.util.Arrays.asList(m.visibleAnnotations, m.invisibleAnnotations)) {
				if (list == null) continue;
				for (org.objectweb.asm.tree.AnnotationNode a : list) {
					if (a.values == null) continue;
					for (int i = 0; i + 1 < a.values.size(); i += 2) {
						Object v = a.values.get(i + 1);
						sb.append(m.name).append('=');
						if (v instanceof List<?> l) {
							sb.append('[');
							for (Object o : l) sb.append(o).append(',');
							sb.append(']');
						} else {
							sb.append(v);
						}
						sb.append(' ');
					}
				}
			}
		}
		return sb.toString();
	}

	/**
	 * The guard class loads and a guard is made; the interface's method (empty in the mod) leaves the charging flag alone, as it did in 1.21.11, and the
	 * mod's own method, renamed by the remapper, still sets it.
	 */
	private static String guard(Probe.Ctx c, int tick) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("guardvillagers")) throw new Probe.Skip("Guard Villagers is not installed");
		return c.onServer(() -> {
			try {
				ServerLevel level = c.serverPlayer().level();
				LivingEntity guard = (LivingEntity) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("guardvillagers:guard")).create(level, EntitySpawnReason.COMMAND);
				if (guard == null) throw new Probe.Fail("no guard could be made");
				Class<?> type = guard.getClass();
				Method flag = type.getMethod("isCharging");
				Method own = type.getDeclaredMethod("setChargingCrossbow$clash", boolean.class);
				((CrossbowAttackMob) guard).setChargingCrossbow(true);
				if ((boolean) flag.invoke(guard)) throw new Probe.Fail("the interface's setChargingCrossbow set the flag; in the mod it does nothing");
				own.invoke(guard, true);
				if (!(boolean) flag.invoke(guard)) throw new Probe.Fail("the mod's own setChargingCrossbow (renamed) did not set the flag");
				return "the guard has the interface's empty setChargingCrossbow and the mod's own setChargingCrossbow$clash, which sets the flag";
			} catch (ReflectiveOperationException e) {
				throw new Probe.Fail(e.toString());
			}
		});
	}
}
