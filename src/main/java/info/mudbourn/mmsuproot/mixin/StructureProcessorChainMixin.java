package info.mudbourn.mmsuproot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import info.mudbourn.mmsuproot.shim.feature.LegacyStructureProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.11's {@code processBlockInfos} kept two block infos as it ran a block through the processor chain: the template's
 * block as it was ({@code structureBlockInfo}, unchanged through the loop) and the block as the processors before had left
 * it. It handed both to each processor's {@code processBlock}. 26.3 keeps the same two but passes only the first's position,
 * so a mod's processor built for 1.21.11, which reads the original's state or tag, sees them only on the first processor of
 * the chain; after that {@link LegacyStructureProcessor} can rebuild the original from a position alone and fills in the
 * current block's state and tag, which are no longer the original's once an earlier processor has changed them.
 *
 * <p>This feeds the loop's own original block info to a {@link LegacyStructureProcessor} directly, the same object 1.21.11
 * passed, so every processor in the chain reads the true original. A 26.3 processor is called as before.
 */
@Mixin(StructureTemplate.class)
public abstract class StructureProcessorChainMixin {
	@WrapOperation(method = "processBlockInfos", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureProcessor;processBlock("
					+ "Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;"
					+ "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"
					+ "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;)"
					+ "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate$StructureBlockInfo;"))
	private static StructureBlockInfo mms_uproot$withOriginal(StructureProcessor processor, LevelReader level, BlockPos offset,
			BlockPos position, BlockPos originalPos, StructureBlockInfo processed, StructurePlaceSettings settings,
			Operation<StructureBlockInfo> original, @Local(ordinal = 0) StructureBlockInfo blockInfo) {
		if (processor instanceof LegacyStructureProcessor legacy) {
			return legacy.processBlock(level, offset, position, blockInfo, processed, settings);
		}
		return original.call(processor, level, offset, position, originalPos, processed, settings);
	}
}
