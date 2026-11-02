package info.mudbourn.mmsuproot.shim.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * What a mod's structure processor extends in place of {@link StructureProcessor}. 1.21.11's was an abstract class
 * that named its codec through {@code getType()}; 26.3's is an interface whose {@code finalizeProcessing} keeps
 * the old shape and whose {@code codec()} is the codec itself. The mod's class keeps the methods it overrides and
 * this answers {@code codec()} from {@code getType()}.
 *
 * <p>{@code processBlock} changed: 1.21.11 passed the template's block as it was (position, state and tag) and the block as
 * the processors before had left it; 26.3 passes only the position of the first. The method a mod overrides is the old one.
 * While 26.3 runs the processor chain, {@code StructureProcessorChainMixin} hands this the loop's own original block info,
 * so every processor reads the true original. Any other caller reaches the old method through the {@code BlockPos} override
 * below, which rebuilds the original from that position and the block it was given: the template's block exactly for the
 * first processor, and for a later one off wherever an earlier processor changed the state or tag.
 */
public abstract class LegacyStructureProcessor implements StructureProcessor {
	protected LegacyStructureProcessor() { }

	protected abstract StructureProcessorType<?> getType();

	/** 1.21.11's {@code processBlock}: a mod overrides this one. */
	public StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos position, StructureBlockInfo original, StructureBlockInfo processed,
			StructurePlaceSettings settings) {
		return processed;
	}

	@Override
	public StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos position, BlockPos originalPos, StructureBlockInfo processed,
			StructurePlaceSettings settings) {
		return processBlock(level, offset, position, new StructureBlockInfo(originalPos, processed.state(), processed.nbt()), processed, settings);
	}

	@Override
	public MapCodec<? extends StructureProcessor> codec() {
		return getType().codec();
	}
}
