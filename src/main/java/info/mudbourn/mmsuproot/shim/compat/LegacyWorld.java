package info.mudbourn.mmsuproot.shim.compat;

import java.util.List;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** World calls that moved or changed argument shape. */
public final class LegacyWorld {
	private LegacyWorld() { }

	/**
	 * {@code Level.getDayTime()}. 26.3 has several world clocks; the dimension's own default clock is
	 * what the single 1.21.11 day time became, so conditions on "time of day" read that one.
	 */
	public static long getDayTime(Level level) {
		return level.getDefaultClockTime();
	}

	/** {@code StructureManager.startsForStructure(SectionPos, Structure)}, now taking the chunk coordinates. */
	public static List<StructureStart> startsForStructure(StructureManager manager, SectionPos section, Structure structure) {
		return manager.startsForStructure(section.x(), section.z(), structure);
	}
}
