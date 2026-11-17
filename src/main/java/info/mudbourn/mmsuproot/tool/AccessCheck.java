package info.mudbourn.mmsuproot.tool;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;

/**
 * The JVM's access rules for one reference into a game class, with the access wideners of the running game applied.
 *
 * <p>The scan's gap check only asks whether a member exists in 26.3. A member can exist and still be unreachable: 1.21.11 left it
 * public and 26.3 made it private, so the mod links against a member the JVM then refuses with an {@code IllegalAccessError}. This
 * class decides that from the real access flags in the 26.3 jar, after what {@code mms_uproot.accesswidener} (and every other
 * widener Fabric loader applies) has opened.
 *
 * <p>The widener semantics are the ones of Fabric's applier: {@code accessible} makes a class, method or field public;
 * {@code extendable} makes a class public and non-final and a method protected (or public) and non-final; {@code mutable} removes
 * {@code final} from a field and does nothing for access, and {@code accessible} on a field does not remove {@code final}, so a
 * write to a final field needs {@code mutable} whatever its visibility.
 */
final class AccessCheck {
	/** The class hierarchy of the scan: mod classes and game classes alike. */
	interface Hierarchy {
		/** Whether {@code cls} is {@code ancestor} or extends it (interfaces count). */
		boolean subclass(String cls, String ancestor);
	}

	/** Why a reference is refused, and the widener line that would open it (null when no widener can). */
	record Violation(String reason, String fix) { }

	private final Hierarchy hierarchy;
	private final Set<String> openClasses = new HashSet<>();
	private final Map<String, Integer> openMethods = new HashMap<>(); // "owner name desc" to ACCESSIBLE and EXTENDABLE bits
	private final Set<String> openFields = new HashSet<>(); // "owner name desc" made public
	private final Set<String> mutableFields = new HashSet<>();
	private int entries;

	private static final int ACCESSIBLE = 1;
	private static final int EXTENDABLE = 2;

	AccessCheck(Hierarchy hierarchy) {
		this.hierarchy = hierarchy;
	}

	/** Names a widener entry in 26.3 terms; the identity for a widener that is already in them. */
	interface Mapper {
		String cls(String name);
		String method(String owner, String name, String desc);
		String field(String owner, String name, String desc);
		String desc(String desc);
	}

	/** Reads one access widener (or class tweaker) file. {@code mapper} is null when its names are already the game's. */
	void widen(String text, Mapper mapper) {
		boolean header = true;
		for (String line : text.split("\\R")) {
			String t = line.strip();
			int hash = t.indexOf('#');
			if (hash >= 0) t = t.substring(0, hash).strip();
			if (t.isEmpty()) continue;
			String[] p = t.split("\\s+");
			if (header) {
				header = false;
				continue;
			}
			String action = p[0].startsWith("transitive-") ? p[0].substring("transitive-".length()) : p[0];
			boolean accessible = action.equals("accessible");
			boolean extendable = action.equals("extendable");
			boolean mutable = action.equals("mutable");
			if (!accessible && !extendable && !mutable || p.length < 3) continue;
			String owner = mapper == null ? p[2] : mapper.cls(p[2]);
			entries++;
			if (p[1].equals("class")) {
				if (accessible || extendable) openClasses.add(owner);
			} else if (p.length == 5 && p[1].equals("method")) {
				String name = mapper == null ? p[3] : mapper.method(p[2], p[3], p[4]);
				String desc = mapper == null ? p[4] : mapper.desc(p[4]);
				openMethods.merge(owner + ' ' + name + ' ' + desc, accessible ? ACCESSIBLE : extendable ? EXTENDABLE : 0, (a, b) -> a | b);
			} else if (p.length == 5 && p[1].equals("field")) {
				String name = mapper == null ? p[3] : mapper.field(p[2], p[3], p[4]);
				String desc = mapper == null ? p[4] : mapper.desc(p[4]);
				if (accessible) openFields.add(owner + ' ' + name + ' ' + desc);
				if (mutable) mutableFields.add(owner + ' ' + name + ' ' + desc);
			}
		}
	}

	int entries() {
		return entries;
	}

	/** The class's flags once the widener is applied. */
	private int classFlags(String cls, int flags) {
		return openClasses.contains(cls) ? pub(flags) : flags;
	}

	/** The flags of a member of {@code declaring} once the widener is applied. */
	private int memberFlags(String declaring, String name, String desc, boolean field, int flags) {
		String key = declaring + ' ' + name + ' ' + desc;
		if (field) {
			if (openFields.contains(key)) flags = pub(flags);
			if (mutableFields.contains(key)) flags &= ~Opcodes.ACC_FINAL;
			return flags;
		}
		int bits = openMethods.getOrDefault(key, 0);
		if ((bits & EXTENDABLE) != 0) flags = prot(flags) & ~Opcodes.ACC_FINAL;
		if ((bits & ACCESSIBLE) != 0) flags = pub(flags);
		return flags;
	}

	private static int pub(int flags) {
		return flags & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
	}

	private static int prot(int flags) {
		return (flags & Opcodes.ACC_PUBLIC) != 0 ? flags : flags & ~Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;
	}

	/** The outermost class of a nest: a game class and its nested classes are nestmates, so each reaches the other's private members. */
	private static String outer(String cls) {
		int dollar = cls.indexOf('$');
		return dollar < 0 ? cls : cls.substring(0, dollar);
	}

	private static String pkg(String cls) {
		int slash = cls.lastIndexOf('/');
		return slash < 0 ? "" : cls.substring(0, slash);
	}

	/**
	 * Why a class named by an instruction (a {@code new}, a cast, a supertype, a member owner) cannot be reached from the callers, or null.
	 * {@code flags} are the class file's, which for a nested class is public or package-private whatever the source said.
	 */
	Violation type(List<String> callers, String cls, int flags) {
		if ((classFlags(cls, flags) & Opcodes.ACC_PUBLIC) != 0) return null;
		for (String c : callers) if (pkg(c).equals(pkg(cls))) return null;
		return new Violation("the class is package-private", "accessible class " + cls);
	}

	/**
	 * Why the member cannot be used from the callers, or null. {@code callers} is the class itself, or for a mixin the classes it is merged
	 * into: its code runs as theirs, so a private member of the target is theirs to use and the receiver rule does not apply (the mixin's
	 * own name stands where the target's will). The reference is fine when any caller may make it.
	 *
	 * <p>The protected receiver rule (the object a protected member is used on must be the caller or a subclass of it) is only checked as far as the
	 * instruction shows: the verifier looks at the type on the stack, which a scan of the instructions cannot see, so a call through the caller's own
	 * superclass name (usually {@code this}) passes, and only a receiver type unrelated to the caller is refused.
	 *
	 * @param owner     the class the instruction names, which the member was found from
	 * @param declaring the class that declares the member
	 * @param init      a constructor, whose protected access is a {@code super(...)} call
	 * @param special   an {@code invokespecial} (a {@code super.foo()} call), whose receiver is the caller itself
	 */
	Violation member(List<String> callers, boolean mixin, String owner, String declaring, String name, String desc, boolean field, int flags,
			boolean statik, boolean write, boolean init, boolean special) {
		int eff = memberFlags(declaring, name, desc, field, flags);
		String kind = field ? "field" : "method";
		String open = "accessible " + kind + " " + declaring + " " + name + " " + desc;
		String reason = null;
		boolean widenable = true;
		boolean allowed = false;
		for (String c : callers) {
			String why = null;
			if ((eff & Opcodes.ACC_PUBLIC) != 0) {
				allowed = true;
			} else if ((eff & Opcodes.ACC_PRIVATE) != 0) {
				if (outer(c).equals(outer(declaring))) allowed = true; // the same class, or nestmates, which share the outermost class
				else why = "private in " + declaring;
			} else if (pkg(c).equals(pkg(declaring))) {
				allowed = true;
			} else if ((eff & Opcodes.ACC_PROTECTED) != 0) {
				if (!hierarchy.subclass(c, declaring)) {
					why = "protected in " + declaring + ", and the caller is not a subclass";
				} else if (!mixin && !statik && !init && !special && !hierarchy.subclass(owner, c) && !hierarchy.subclass(c, owner)) {
					why = "protected in " + declaring + ", reached through a " + owner + " receiver that is not the caller";
					widenable = false;
				} else {
					allowed = true;
				}
			} else {
				why = "package-private in " + declaring;
			}
			if (allowed) break;
			if (reason == null) reason = why;
		}
		if (allowed) reason = null;
		String fix = reason == null ? null : widenable ? open : null;
		if (write && field && (eff & Opcodes.ACC_FINAL) != 0 && !callers.contains(declaring)) {
			String mutable = "mutable field " + declaring + " " + name + " " + desc;
			reason = reason == null ? "final field written from outside its class" : reason + ", and final field written from outside its class";
			fix = fix == null ? (widenable ? mutable : null) : fix + "; " + mutable;
		}
		return reason == null ? null : new Violation(reason, fix);
	}
}
