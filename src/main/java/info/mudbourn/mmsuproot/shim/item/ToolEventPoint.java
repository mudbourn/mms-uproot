package info.mudbourn.mmsuproot.shim.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Calls that do nothing and stand where a hook of a mod expects a call to inject before. 1.21.11's axe played the strip sound
 * where Arc's strip-log hook injects; the sound is the block transformer's now, so renames.txt points that hook at this call
 * instead (see {@link ToolEvents}).
 */
public final class ToolEventPoint {
	private ToolEventPoint() { }

	/** The place where a log is about to be stripped, before its sound. */
	public static void stripLog(Level level, BlockPos pos) { }
}
