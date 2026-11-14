package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.world.level.storage.loot.IntRangePredicate;

/** 1.21.11's {@code IntRange}: bounds that loot conditions test a number against. 26.3 calls it {@code IntRangePredicate}, with the same factories. */
public record LegacyIntRange(IntRangePredicate predicate) {
	public static LegacyIntRange exact(int value) {
		return new LegacyIntRange(IntRangePredicate.exact(value));
	}

	public static LegacyIntRange range(int min, int max) {
		return new LegacyIntRange(IntRangePredicate.range(min, max));
	}

	public static LegacyIntRange lowerBound(int min) {
		return new LegacyIntRange(IntRangePredicate.lowerBound(min));
	}

	public static LegacyIntRange upperBound(int max) {
		return new LegacyIntRange(IntRangePredicate.upperBound(max));
	}
}
