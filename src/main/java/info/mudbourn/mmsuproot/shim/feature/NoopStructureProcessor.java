package info.mudbourn.mmsuproot.shim.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;

/**
 * What a mod's own structure processor decodes to. The processors of 1.21.11 extended a class that is
 * an interface now, with other methods, so the mod's class cannot load; its data still has to decode,
 * and the processor changes nothing.
 */
public final class NoopStructureProcessor implements StructureProcessor {
	public static final NoopStructureProcessor INSTANCE = new NoopStructureProcessor();
	public static final MapCodec<NoopStructureProcessor> CODEC = MapCodec.unit(INSTANCE);

	private NoopStructureProcessor() { }

	/** A registry takes each value once, so every processor type gets a codec of its own. */
	public static MapCodec<NoopStructureProcessor> newCodec() {
		return MapCodec.unit(INSTANCE);
	}

	@Override
	public MapCodec<? extends StructureProcessor> codec() {
		return CODEC;
	}
}
