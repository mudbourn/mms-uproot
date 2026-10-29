package info.mudbourn.mmsuproot.shim.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;

/** 1.21.11's holder of a processor's codec. 26.3 registers the codec itself, in the same registry. */
@FunctionalInterface
public interface StructureProcessorType<P extends StructureProcessor> {
	MapCodec<P> codec();
}
