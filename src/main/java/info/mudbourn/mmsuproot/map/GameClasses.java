package info.mudbourn.mmsuproot.map;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What the running Minecraft contains. It tells the remapper whether a name still exists and lets
 * the access widener and mixin checks skip or report names that vanished.
 */
public final class GameClasses {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");
	private static final Info ABSENT = new Info(null, List.of(), Set.of(), Set.of(), false, Set.of(), Map.of(), Map.of());

	/** Members of one class, as {@code name} and {@code name+desc} strings. */
	public record Info(String superName, List<String> interfaces, Set<String> fields, Set<String> methods, boolean isInterface, Set<String> privateFields,
			Map<String, Integer> methodAccess, Map<String, Integer> fieldAccess) {
		public boolean hasField(String name, String desc) { return fields.contains(name + ":" + desc); }
		public boolean hasMethod(String name, String desc) { return methods.contains(name + desc); }
		public boolean hasFieldNamed(String name) { return fields.stream().anyMatch(f -> f.startsWith(name + ":")); }
		public boolean hasMethodNamed(String name) { return methods.stream().anyMatch(m -> m.startsWith(name + "(")); }
		/** Access flags of a method declared by this class, or -1. */
		public int methodFlags(String name, String desc) { return methodAccess.getOrDefault(name + desc, -1); }
		/** Access flags of a field declared by this class, or -1. */
		public int fieldFlags(String name, String desc) { return fieldAccess.getOrDefault(name + ":" + desc, -1); }
	}

	private final ClassLoader loader;
	private final Set<String> names = new HashSet<>();
	private final Map<String, List<String>> bySimpleName = new HashMap<>();
	private final Map<String, Info> infos = new ConcurrentHashMap<>();

	public GameClasses(ClassLoader loader, Collection<Path> classPath) {
		this.loader = loader;
		for (Path p : classPath) {
			if (!Files.isRegularFile(p)) continue;
			try (ZipFile zip = new ZipFile(p.toFile())) {
				if (zip.getEntry("net/minecraft/world/level/Level.class") == null) continue;
				zip.stream().map(ZipEntry::getName).filter(n -> n.startsWith("net/minecraft/") && n.endsWith(".class")).forEach(n -> {
					String cls = n.substring(0, n.length() - 6);
					names.add(cls);
					if (cls.indexOf('$') < 0) bySimpleName.computeIfAbsent(cls.substring(cls.lastIndexOf('/') + 1), k -> new ArrayList<>()).add(cls);
				});
			} catch (IOException e) {
				LOGGER.warn("Could not index {}", p, e);
			}
		}
		LOGGER.info("Indexed {} Minecraft classes", names.size());
	}

	public boolean has(String internalName) {
		if (names.contains(internalName)) return true;
		return names.isEmpty() && loader.getResource(internalName + ".class") != null;
	}

	/** The one class that kept {@code simpleName} but changed package, or null when unclear. */
	public String uniqueBySimpleName(String simpleName) {
		List<String> hits = bySimpleName.get(simpleName);
		return hits != null && hits.size() == 1 ? hits.get(0) : null;
	}

	public int size() {
		return names.size();
	}

	public boolean indexed() {
		return !names.isEmpty();
	}

	public Optional<Info> info(String internalName) {
		Info info = infos.computeIfAbsent(internalName, this::read);
		return info == ABSENT ? Optional.empty() : Optional.of(info);
	}

	private Info read(String name) {
		try (InputStream in = loader.getResourceAsStream(name + ".class")) {
			if (in == null) return ABSENT;
			ClassReader reader = new ClassReader(in);
			Set<String> fields = new HashSet<>();
			Set<String> methods = new HashSet<>();
			Set<String> privateFields = new HashSet<>();
			Map<String, Integer> methodAccess = new HashMap<>();
			Map<String, Integer> fieldAccess = new HashMap<>();
			boolean itfFlag = (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0;
			reader.accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public FieldVisitor visitField(int access, String n, String desc, String sig, Object value) {
					fields.add(n + ":" + desc);
					fieldAccess.put(n + ":" + desc, access);
					if ((access & Opcodes.ACC_PRIVATE) != 0) privateFields.add(n + ":" + desc);
					return null;
				}

				@Override
				public MethodVisitor visitMethod(int access, String n, String desc, String sig, String[] ex) {
					methods.add(n + desc);
					methodAccess.put(n + desc, access);
					return null;
				}
			}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			String[] itf = reader.getInterfaces();
			return new Info(reader.getSuperName(), List.of(itf), fields, methods, itfFlag, privateFields, methodAccess, fieldAccess);
		} catch (IOException e) {
			return ABSENT;
		}
	}

	/** Whether a Minecraft class is an interface in the running game; false for a class or a name the game lacks. */
	public boolean isInterface(String internalName) {
		return internalName.charAt(0) != '[' && names.contains(internalName) && info(internalName).map(Info::isInterface).orElse(false);
	}

	/** Whether a Minecraft class is a plain class (not an interface) in the running game. */
	public boolean isClass(String internalName) {
		return internalName.charAt(0) != '[' && names.contains(internalName) && info(internalName).map(i -> !i.isInterface()).orElse(false);
	}

	/** Finds a field by name and descriptor on a class or anything it extends or implements. */
	public boolean findField(String owner, String name, String desc) {
		return walk(owner, new HashSet<>(), i -> desc == null ? i.hasFieldNamed(name) : i.hasField(name, desc));
	}

	/** Whether the field is found on the class or something it extends and is private there, so the caller cannot read it. */
	public boolean isPrivateField(String owner, String name, String desc) {
		String key = name + ":" + desc;
		for (String c = owner; c != null; ) {
			Info info = info(c).orElse(null);
			if (info == null) return false;
			if (info.fields().contains(key)) return info.privateFields().contains(key);
			c = info.superName();
		}
		return false;
	}

	/**
	 * The class on or above {@code owner} (superclasses first, then interfaces) that declares the method, or null.
	 * The first hit wins, as a call would resolve it.
	 */
	public String declaringMethod(String owner, String name, String desc) {
		return declaring(owner, new HashSet<>(), i -> i.hasMethod(name, desc), name + desc, false);
	}

	/** As {@link #declaringMethod} for a field. */
	public String declaringField(String owner, String name, String desc) {
		return declaring(owner, new HashSet<>(), i -> i.hasField(name, desc), name + ":" + desc, true);
	}

	private String declaring(String owner, Set<String> seen, java.util.function.Predicate<Info> test, String key, boolean field) {
		if (owner == null || !seen.add(owner)) return null;
		Info info = info(owner).orElse(null);
		if (info == null) return null;
		if (test.test(info)) return owner;
		String up = declaring(info.superName(), seen, test, key, field);
		if (up != null) return up;
		for (String i : info.interfaces()) {
			up = declaring(i, seen, test, key, field);
			if (up != null) return up;
		}
		return null;
	}

	public boolean findMethod(String owner, String name, String desc) {
		return walk(owner, new HashSet<>(), i -> desc == null ? i.hasMethodNamed(name) : i.hasMethod(name, desc));
	}

	private boolean walk(String owner, Set<String> seen, java.util.function.Predicate<Info> test) {
		if (owner == null || !seen.add(owner)) return false;
		Info info = info(owner).orElse(null);
		if (info == null) return false;
		if (test.test(info)) return true;
		if (walk(info.superName(), seen, test)) return true;
		for (String i : info.interfaces()) if (walk(i, seen, test)) return true;
		return false;
	}
}
