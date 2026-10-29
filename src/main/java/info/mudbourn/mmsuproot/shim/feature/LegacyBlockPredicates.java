package info.mudbourn.mmsuproot.shim.feature;

import java.util.List;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** The block predicate factories whose offset parameter changed type; the same predicates with an offset vector. */
public final class LegacyBlockPredicates {
	private LegacyBlockPredicates() { }

	public static BlockPredicate matchesBlocks(Vec3i offset, Block... blocks) {
		return BlockPredicate.matchesBlocks(offset, List.of(blocks));
	}

	public static BlockPredicate matchesFluids(Vec3i offset, Fluid... fluids) {
		return BlockPredicate.matchesFluids(offset, List.of(fluids));
	}

	public static BlockPredicate noFluid(Vec3i offset) {
		return BlockPredicate.matchesFluids(offset, List.of(Fluids.EMPTY));
	}
}
