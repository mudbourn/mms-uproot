package info.mudbourn.mmsuproot.boot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;

/**
 * Finds the mixin injectors that matched nothing. Uproot lets a mod's injectors fail quietly (a missing target would otherwise stop the
 * whole mixin from applying), so a hook whose target is gone does nothing and says nothing. Mixin counts what each injector
 * did ({@code InjectionInfo.getInjectedCallbackCount}), but only tells when {@code require} is set. This makes every kind of
 * injection info Mixin knows (its own and MixinExtras', whenever they are registered) a subclass that reports itself after
 * {@code postInject}, and keeps the ones with no injection. {@link #install} may be called any number of times.
 *
 * <p>The reporting subclasses live in the loader of the class they extend and reach this class through a
 * {@link Consumer} held in the system properties, as the two loaders do not see each other.
 */
public final class DeadHooks {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");
	private static final String SINK = "uproot.deadhooks.sink";
	private static final String SINK_MADE = "uproot.deadhooks.made";
	private static final List<Dead> DEAD = Collections.synchronizedList(new ArrayList<>());
	private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
	private static volatile Map<String, String> allowed;

	/** One injector that injected nothing. */
	public record Dead(String owner, String config, String mixin, String handler, String target, String intended, int targetsScanned) {
		public String key() {
			return mixin + " " + handler;
		}
	}

	private DeadHooks() { }

	/** The id of the mod Uproot rewrites that owns the mixin config, or null for a config of any other mod. */
	public static String ownerOf(String config) {
		return UprootBoot.ownerOf(config);
	}

	/** The injectors seen so far with no injection in them. */
	public static List<Dead> dead() {
		synchronized (DEAD) {
			return List.copyOf(DEAD);
		}
	}

	/** The reason an injector is accepted as dead, from {@code uproot/dead-hooks.txt}, or null. */
	public static String allowedReason(Dead dead) {
		Map<String, String> map = allowed;
		if (map == null) {
			map = loadAllowed();
			allowed = map;
		}
		String reason = map.get(dead.mixin() + " " + dead.handler());
		return reason != null ? reason : map.get(dead.mixin() + " *");
	}

	private static Map<String, String> loadAllowed() {
		Map<String, String> out = new ConcurrentHashMap<>();
		try (InputStream in = DeadHooks.class.getResourceAsStream("/uproot/dead-hooks.txt")) {
			if (in == null) return out;
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = r.readLine()) != null) {
				int bar = line.indexOf('|');
				String rule = (bar < 0 ? line : line.substring(0, bar)).strip();
				if (rule.isEmpty() || rule.startsWith("#")) continue;
				String reason = bar < 0 ? "" : line.substring(bar + 1).strip();
				String[] p = rule.split("\\s+");
				if (p.length != 2 || reason.isEmpty()) throw new IllegalStateException("Bad dead-hooks rule (MIXINCLASS HANDLER | reason): " + line);
				out.put(p[0].replace('.', '/') + " " + p[1], reason);
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return out;
	}

	/** Replaces the constructor each injection info kind is made with by one of a reporting subclass. */
	@SuppressWarnings("unchecked")
	public static synchronized void install() {
		System.getProperties().put(SINK, (Consumer<Object>) DeadHooks::record);
		System.getProperties().put(SINK_MADE, (Consumer<Object>) o -> {
			if (o instanceof InjectionInfo info && info.getTargetCount() == 0) record(o);
		});
		try {
			Class.forName(InjectionInfo.class.getName(), true, InjectionInfo.class.getClassLoader());
			Field registryField = InjectionInfo.class.getDeclaredField("registry");
			registryField.setAccessible(true);
			Map<String, Object> registry = (Map<String, Object>) registryField.get(null);
			for (Object entry : List.copyOf(registry.values())) {
				Field ctorField = entry.getClass().getDeclaredField("ctor");
				ctorField.setAccessible(true);
				Constructor<?> ctor = (Constructor<?>) ctorField.get(entry);
				Class<?> type = ctor.getDeclaringClass();
				if (type.getName().endsWith("$UprootCounted") || !SEEN.add(type.getName())) continue;
				Class<?> counted = counted(type);
				if (counted == null) {
					LOGGER.warn("Dead hook check cannot watch {} injectors", type.getName());
					continue;
				}
				ctorField.set(entry, counted.getDeclaredConstructor(ctor.getParameterTypes()));
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Dead hook check is not active", e);
		}
	}

	/** {@code type} with {@code postInject} reporting to the sink, defined next to it; null when it cannot be subclassed. */
	private static Class<?> counted(Class<?> type) throws ReflectiveOperationException {
		if (Modifier.isFinal(type.getModifiers())) return null;
		Method post = null;
		for (Class<?> c = type; c != null && post == null; c = c.getSuperclass()) {
			try {
				post = c.getDeclaredMethod("postInject");
			} catch (NoSuchMethodException ignored) {
				// the next superclass
			}
		}
		if (post == null || Modifier.isFinal(post.getModifiers())) return null;
		Constructor<?> ctor = null;
		for (Constructor<?> c : type.getDeclaredConstructors()) if (c.getParameterCount() == 3 && c.getParameterTypes()[0].getSimpleName().equals("MixinTargetContext")) ctor = c;
		if (ctor == null || Modifier.isPrivate(ctor.getModifiers())) return null;

		String name = type.getName().replace('.', '/') + "$UprootCounted";
		String superName = type.getName().replace('.', '/');
		String desc = org.objectweb.asm.Type.getConstructorDescriptor(ctor);
		ClassWriter w = new ClassWriter(0);
		w.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC, name, null, superName, null);
		MethodVisitor init = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		int slot = 1;
		for (org.objectweb.asm.Type a : org.objectweb.asm.Type.getArgumentTypes(desc)) {
			init.visitVarInsn(Opcodes.ALOAD, slot);
			slot += a.getSize();
		}
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", desc, false);
		// an injector whose method target was not found is dropped by Mixin before it injects, so it is looked at as soon as it is made
		init.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperties", "()Ljava/util/Properties;", false);
		init.visitLdcInsn(SINK_MADE);
		init.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Properties", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
		init.visitTypeInsn(Opcodes.CHECKCAST, "java/util/function/Consumer");
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V", true);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(Math.max(slot + 1, 3), slot);
		init.visitEnd();
		MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "postInject", "()V", null, null);
		m.visitCode();
		m.visitVarInsn(Opcodes.ALOAD, 0);
		m.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "postInject", "()V", false);
		m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperties", "()Ljava/util/Properties;", false);
		m.visitLdcInsn(SINK);
		m.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Properties", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
		m.visitTypeInsn(Opcodes.CHECKCAST, "java/util/function/Consumer");
		m.visitVarInsn(Opcodes.ALOAD, 0);
		m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V", true);
		m.visitInsn(Opcodes.RETURN);
		m.visitMaxs(3, 1);
		m.visitEnd();
		w.visitEnd();
		return MethodHandles.privateLookupIn(type, MethodHandles.lookup()).defineClass(w.toByteArray());
	}

	private static void record(Object o) {
		if (!(o instanceof InjectionInfo info) || info.getInjectedCallbackCount() > 0) return;
		try {
			var context = info.getMixin();
			var mixinInfo = context.getMixin();
			String config = mixinInfo.getConfig().getName();
			String mixin = mixinInfo.getClassRef();
			String owner = UprootBoot.ownerOf(config);
			StringBuilder intended = new StringBuilder();
			AnnotationNode annotation = info.getAnnotationNode();
			for (int i = 0; annotation.values != null && i + 1 < annotation.values.size(); i += 2) {
				if (annotation.values.get(i).equals("method") || annotation.values.get(i).equals("target")) intended.append(annotation.values.get(i + 1)).append(' ');
				if (annotation.values.get(i).equals("at") || annotation.values.get(i).equals("constant")) intended.append(describeAt(annotation.values.get(i + 1))).append(' ');
			}
			Dead dead = new Dead(owner == null ? "?" : owner, config, mixin, info.getMethodName(), context.getTargetClassName(), intended.toString().strip(), info.getTargetCount());
			DEAD.add(dead);
			LOGGER.warn("Dead hook: {} {} {}.{} into {} matched nothing (scanned {} target(s)); wanted {}", dead.owner(), config, mixin, dead.handler(), dead.target(), dead.targetsScanned(), dead.intended());
		} catch (RuntimeException e) {
			LOGGER.warn("Dead hook that could not be described", e);
		}
	}

	private static String describeAt(Object at) {
		if (at instanceof AnnotationNode node && node.values != null) {
			StringBuilder out = new StringBuilder("@At(");
			for (int i = 0; i + 1 < node.values.size(); i += 2) out.append(node.values.get(i)).append('=').append(node.values.get(i + 1)).append(' ');
			return out.append(')').toString();
		}
		if (at instanceof List<?> list) {
			StringBuilder out = new StringBuilder();
			for (Object o : list) out.append(describeAt(o)).append(' ');
			return out.toString().strip();
		}
		return String.valueOf(at);
	}
}
