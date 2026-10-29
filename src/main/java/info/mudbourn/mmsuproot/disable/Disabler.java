package info.mudbourn.mmsuproot.disable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import info.mudbourn.mmsuproot.boot.Report;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cleanly switches off parts of a mod that cannot be bridged, as the rules in
 * {@code uproot/disable.txt} say. Four tools: {@code stub} loads a class with every body emptied,
 * {@code noop} empties single methods, and {@code skip} makes the mod's registry calls for one id
 * do nothing (the value is handed back unregistered, so the mod's own fields still fill), and
 * {@code mixin} leaves one mixin class out of its mod's mixin config.
 *
 * <p>{@link #transform} is the one entry point and belongs after the remap, on the 26.3-named
 * bytes. This class touches no Minecraft type, so the boot code can use it before the game is on the
 * class path; the rewritten calls go to {@link RegistryGate}, which does.
 */
public final class Disabler {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");
	private static final String REGISTRY = "net/minecraft/core/Registry";
	private static final byte[] REGISTRY_BYTES = REGISTRY.getBytes(StandardCharsets.UTF_8);
	private static final String GATE = "info/mudbourn/mmsuproot/disable/RegistryGate";

	private static volatile Disabler instance;

	private final List<Rule> rules;
	private final Map<String, List<Rule>> byClass = new HashMap<>();
	private final Map<String, Rule> skips = new HashMap<>();
	private final Map<String, Rule> mixins = new HashMap<>();
	private final List<String> failures = new ArrayList<>();
	private final Map<String, Boolean> seenClasses = new ConcurrentHashMap<>();

	private Disabler(List<Rule> rules) {
		this.rules = rules;
		for (Rule r : rules) {
			if (r.kind() == Rule.Kind.SKIP) skips.put(r.owner() + " " + r.name(), r);
			else if (r.kind() == Rule.Kind.MIXIN) mixins.put(r.owner(), r);
			else byClass.computeIfAbsent(r.owner(), k -> new ArrayList<>()).add(r);
		}
	}

	/** Loads the rules and hands the report a live view of them. Safe to call more than once. */
	public static synchronized void init(Report report) {
		if (instance != null) return;
		Disabler d = new Disabler(RuleFile.load("/uproot/disable.txt"));
		instance = d;
		if (report != null) report.disabledSource(d::reportLines);
		LOGGER.info("Disable rules loaded: {}", d.rules.size());
	}

	/** Applies the rules to one class. Returns the same array when no rule touches it. */
	public static byte[] transform(String className, byte[] bytes) {
		Disabler d = instance;
		if (d == null) {
			init(null);
			d = instance;
		}
		return d.apply(className.replace('.', '/'), bytes);
	}

	/** Called by {@link RegistryGate}: whether a registration is switched off. */
	static boolean skips(String registryPath, String id) {
		Disabler d = instance;
		if (d == null) return false;
		Rule r = d.skips.get(registryPath + " " + id);
		if (r == null) return false;
		r.hits().incrementAndGet();
		return true;
	}

	/** Whether a mixin class is left out of its config; counts as a use of the rule. */
	public static boolean dropsMixin(String internal) {
		Disabler d = instance;
		Rule r = d == null ? null : d.mixins.get(internal);
		if (r == null) return false;
		r.hits().incrementAndGet();
		return true;
	}

	/** Why a mixin class is left out of its config, or null when it is not. Does not count. */
	public static String mixinReason(String internal) {
		Disabler d = instance;
		Rule r = d == null ? null : d.mixins.get(internal);
		return r == null ? null : r.reason();
	}

	private byte[] apply(String internal, byte[] bytes) {
		List<Rule> own = byClass.get(internal);
		boolean gate = !skips.isEmpty() && mentions(bytes, REGISTRY_BYTES);
		if (own == null && !gate) return bytes;
		seenClasses.put(internal, Boolean.TRUE);
		try {
			ClassReader reader = new ClassReader(bytes);
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			boolean stub = own != null && own.stream().anyMatch(r -> r.kind() == Rule.Kind.STUB);
			if (stub) own.stream().filter(r -> r.kind() == Rule.Kind.STUB).forEach(r -> r.hits().incrementAndGet());
			ClassVisitor chain = new Rewriter(writer, internal, own, stub, gate, SuperCalls.scan(reader));
			reader.accept(chain, 0);
			return writer.toByteArray();
		} catch (RuntimeException e) {
			LOGGER.warn("Disable rules failed on {}", internal, e);
			synchronized (failures) {
				failures.add(internal + ": " + e);
			}
			return bytes;
		}
	}

	private static boolean mentions(byte[] haystack, byte[] needle) {
		outer:
		for (int i = 0; i <= haystack.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) continue outer;
			}
			return true;
		}
		return false;
	}

	private List<String> reportLines() {
		List<String> out = new ArrayList<>();
		for (Rule r : rules) {
			int h = r.hits().get();
			out.add((h > 0 ? "applied" : "not matched yet") + " (" + h + "): " + r.describe() + " -- " + r.reason());
		}
		synchronized (failures) {
			for (String f : failures) out.add("FAILED " + f);
		}
		return out;
	}

	/** One pass over a class: stubs it, empties named methods, and reroutes registry calls. */
	private final class Rewriter extends ClassVisitor {
		private final String owner;
		private final List<Rule> own;
		private final boolean stub;
		private final boolean gate;
		private final Map<String, String[]> superCalls;

		Rewriter(ClassVisitor next, String owner, List<Rule> own, boolean stub, boolean gate, Map<String, String[]> superCalls) {
			super(Opcodes.ASM9, next);
			this.owner = owner;
			this.own = own;
			this.stub = stub;
			this.gate = gate;
			this.superCalls = superCalls;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
			if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return mv;
			if (stub) return new BodyReplacer(mv, name, descriptor, superCalls.get(name + descriptor), false);
			if (own != null) {
				for (Rule r : own) {
					if (r.matchesMethod(name, descriptor)) {
						r.hits().incrementAndGet();
						return new BodyReplacer(mv, name, descriptor, null, true);
					}
				}
			}
			if (!gate) return mv;
			return new MethodVisitor(Opcodes.ASM9, mv) {
				@Override
				public void visitMethodInsn(int opcode, String insnOwner, String insnName, String insnDesc, boolean itf) {
					if (opcode == Opcodes.INVOKESTATIC && insnOwner.equals(REGISTRY) && insnName.equals("register") && RegistryGate.handles(insnDesc)) {
						super.visitMethodInsn(opcode, GATE, insnName, insnDesc, false);
					} else {
						super.visitMethodInsn(opcode, insnOwner, insnName, insnDesc, itf);
					}
				}
			};
		}
	}
}
