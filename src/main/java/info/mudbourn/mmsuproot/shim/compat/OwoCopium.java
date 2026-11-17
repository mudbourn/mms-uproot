package info.mudbourn.mmsuproot.shim.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * owo-lib's copium ore hook ({@code Maldenhagen.injectCopium}): ore features write blocks straight into chunk sections, which skips the
 * block update and block entity set-up of a real {@code setBlock}, so for the blocks a mod registered with owo the feature's positions are
 * written again through the level when the feature is done. owo is reached through reflection (Uproot has no build dependency on it); when
 * it is not there this does nothing.
 */
public final class OwoCopium {
	private static final ThreadLocal<Map<BlockPos, BlockState>> COPING = ThreadLocal.withInitial(HashMap::new);
	private static volatile MethodHandle isOnCopium;
	private static volatile boolean absent;

	private OwoCopium() { }

	/** Called for each ore block a feature wrote into a section: remembers it when owo has the block on its list. */
	public static void note(BlockPos pos, BlockState state) {
		if (isOnCopium(state.getBlock())) COPING.get().put(pos.immutable(), state);
	}

	/** Called when the ore feature is done: writes every remembered block through the level (flag 3, as owo did) and forgets them. */
	public static void flush(LevelAccessor level) {
		Map<BlockPos, BlockState> coping = COPING.get();
		if (coping.isEmpty()) return;
		coping.forEach((pos, state) -> level.setBlock(pos, state, 3));
		coping.clear();
	}

	private static boolean isOnCopium(Block block) {
		if (absent) return false;
		try {
			MethodHandle handle = isOnCopium;
			if (handle == null) {
				Class<?> owo = Class.forName("io.wispforest.owo.util.Maldenhagen", true, OwoCopium.class.getClassLoader());
				isOnCopium = handle = MethodHandles.publicLookup().findStatic(owo, "isOnCopium", MethodType.methodType(boolean.class, Block.class));
			}
			return (boolean) handle.invoke(block);
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			absent = true;
			return false;
		} catch (Throwable e) {
			throw new IllegalStateException("cannot ask owo about copium", e);
		}
	}
}
