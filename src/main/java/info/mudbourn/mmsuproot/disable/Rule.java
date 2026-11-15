package info.mudbourn.mmsuproot.disable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * One line of {@code disable.txt}. The reason is kept for the report; {@code hits} counts how often
 * the rule found something to act on, so a rule that never matched can be listed too. {@code legacyOnly} limits the rule to
 * mods remapped from 1.21.11 (a mod built for 26.1 or 26.2 may hold a class of the same name that needs no switching off).
 */
record Rule(Kind kind, String owner, String name, String desc, String reason, int line, AtomicInteger hits, boolean legacyOnly) {
	enum Kind { STUB, NOOP, SKIP, MIXIN }

	/** For MIXIN rules {@code owner} is the mixin class. For SKIP rules {@code owner} is the registry path and {@code name} the full id. */
	String describe() {
		return switch (kind) {
			case STUB -> "stub " + owner;
			case NOOP -> "noop " + owner + "." + name + desc;
			case SKIP -> "skip " + owner + " " + name;
			case MIXIN -> "mixin " + owner;
		};
	}

	boolean matchesMethod(String methodName, String methodDesc) {
		return kind == Kind.NOOP && name.equals(methodName) && (desc.equals("*") || desc.equals(methodDesc));
	}
}
