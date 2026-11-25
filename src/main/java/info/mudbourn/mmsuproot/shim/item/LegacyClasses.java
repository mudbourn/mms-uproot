package info.mudbourn.mmsuproot.shim.item;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The classes of the mods built in the 1.21.1 era. No game class is touched here, because the boot hook fills it before the game is on the class path.
 */
public final class LegacyClasses {
	private LegacyClasses() { }

	private static final Set<String> CLASSES = ConcurrentHashMap.newKeySet();

	/** Marks a class of a 1.21.1-era mod (a dotted name). */
	public static void add(String dottedName) {
		CLASSES.add(dottedName);
	}

	/** Whether a class of a 1.21.1-era mod is on the stack, a nested class counting as its outer one. */
	public static boolean onStack() {
		if (CLASSES.isEmpty()) return false;
		return StackWalker.getInstance().walk(frames -> frames.map(StackWalker.StackFrame::getClassName).anyMatch(name -> {
			if (CLASSES.contains(name)) return true;
			int dollar = name.indexOf('$');
			return dollar > 0 && CLASSES.contains(name.substring(0, dollar));
		}));
	}
}
