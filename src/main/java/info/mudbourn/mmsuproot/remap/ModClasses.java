package info.mudbourn.mmsuproot.remap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Every class of every target mod, by internal name. Uproot never renames these, and it reads their
 * headers to follow a mod class up to the Minecraft class it extends. The headers and member lists are the same
 * whichever mod's names are being mapped, so they are read once here and shared by every {@link Namer}.
 */
public final class ModClasses {
	private final Map<String, Path> classes = new ConcurrentHashMap<>();
	private final Map<String, List<String>> parents = new ConcurrentHashMap<>();
	private final Map<String, Map<String, Integer>> methods = new ConcurrentHashMap<>();
	private final Map<String, String> readErrors = new ConcurrentHashMap<>();
	private final java.util.Set<String> mixins = ConcurrentHashMap.newKeySet();
	private volatile Map<String, List<String>> children;
	private volatile int childrenOf = -1;

	public void put(String internalName, Path path) {
		classes.put(internalName, path);
	}

	public Path path(String internalName) {
		return classes.get(internalName);
	}

	public boolean has(String internalName) {
		return classes.containsKey(internalName);
	}

	/** The error that stopped the header or the members of the class from being read, or null. */
	String readError(String internalName) {
		return readErrors.get(internalName);
	}

	/**
	 * Super class, interfaces and {@code @Mixin} targets of a mod class, in the mod's own names (an empty list for a class that is not one
	 * of the mods or cannot be read: see {@link #readError}).
	 */
	List<String> parents(String name) {
		List<String> cached = parents.get(name);
		if (cached != null) return cached;
		List<String> out = new ArrayList<>();
		Path p = classes.get(name);
		if (p != null) {
			try {
				ClassReader reader = new ClassReader(Files.readAllBytes(p));
				if (reader.getSuperName() != null) out.add(reader.getSuperName());
				out.addAll(List.of(reader.getInterfaces()));
				reader.accept(new ClassVisitor(Opcodes.ASM9) {
					@Override
					public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
						if (!desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
						mixins.add(name);
						return new AnnotationVisitor(Opcodes.ASM9) {
							@Override
							public AnnotationVisitor visitArray(String key) {
								return new AnnotationVisitor(Opcodes.ASM9) {
									@Override
									public void visit(String k, Object value) {
										if (value instanceof Type t && t.getSort() == Type.OBJECT) out.add(t.getInternalName());
										else if (value instanceof String s && "targets".equals(key)) out.add(s.replace('.', '/'));
									}
								};
							}
						};
					}
				}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			} catch (IOException | RuntimeException e) {
				readErrors.put(name, e.toString());
			}
		}
		List<String> raced = parents.putIfAbsent(name, out);
		return raced != null ? raced : out;
	}

	/** Whether the class is a mixin ({@code @Mixin}), whose members are merged into its targets whatever their access. */
	boolean isMixin(String name) {
		parents(name);
		return mixins.contains(name);
	}

	/** The mod classes that name {@code name} as a parent (a mixin counts as a child of each class it targets). */
	List<String> children(String name) {
		return childIndex().getOrDefault(name, List.of());
	}

	private Map<String, List<String>> childIndex() {
		Map<String, List<String>> index = children;
		if (index != null && childrenOf == classes.size()) return index;
		synchronized (this) {
			if (children != null && childrenOf == classes.size()) return children;
			Map<String, List<String>> built = new ConcurrentHashMap<>();
			classes.keySet().parallelStream().forEach(c -> {
				for (String parent : parents(c)) {
					if (classes.containsKey(parent)) built.computeIfAbsent(parent, k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(c);
				}
			});
			childrenOf = classes.size();
			children = built;
			return built;
		}
	}

	/**
	 * The methods a mod class declares: {@code name, a tab and the descriptor} to the access flags. Null when the class cannot be
	 * read (see {@link #readError}).
	 */
	Map<String, Integer> methods(String name) {
		Map<String, Integer> cached = methods.get(name);
		if (cached != null) return cached;
		Path p = classes.get(name);
		if (p == null) return null;
		Map<String, Integer> out = new java.util.LinkedHashMap<>();
		try {
			new ClassReader(Files.readAllBytes(p)).accept(new ClassVisitor(Opcodes.ASM9) {
				@Override
				public MethodVisitor visitMethod(int access, String n, String descriptor, String signature, String[] exceptions) {
					out.put(n + '\t' + descriptor, access);
					return null;
				}
			}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		} catch (IOException | RuntimeException e) {
			readErrors.put(name, e.toString());
			return null;
		}
		Map<String, Integer> raced = methods.putIfAbsent(name, out);
		return raced != null ? raced : out;
	}
}
