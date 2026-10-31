package info.mudbourn.mmsuproot.shim.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;

/**
 * What a mod's structure processor extends in place of {@link StructureProcessor}. 1.21.11's was an abstract class
 * that named its codec through {@code getType()}; 26.3's is an interface whose {@code processBlock} and
 * {@code finalizeProcessing} keep the old shape and whose {@code codec()} is the codec itself. The mod's class
 * keeps the methods it overrides and this answers {@code codec()} from {@code getType()}.
 */
public abstract class LegacyStructureProcessor implements StructureProcessor {
	protected LegacyStructureProcessor() { }

	protected abstract StructureProcessorType<?> getType();

	@Override
	public MapCodec<? extends StructureProcessor> codec() {
		return getType().codec();
	}
}
