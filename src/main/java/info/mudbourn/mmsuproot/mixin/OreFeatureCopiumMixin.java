package info.mudbourn.mmsuproot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import info.mudbourn.mmsuproot.shim.compat.OwoCopium;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * owo-lib's copium ore hook for 26.3's {@code OreFeature.doPlace}, which has the shape owo's Copenhagen mixin was written for but
 * walks block replacements where 1.21.11 walked target states, so its local capture no longer fits (see {@link OwoCopium}).
 * The state written and the position it is written at are both at the one call that sets a block in a section.
 */
@Mixin(OreFeature.class)
public abstract class OreFeatureCopiumMixin {
	@WrapOperation(method = "doPlace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"), require = 1)
	private BlockState mms_uproot$noteCopium(LevelChunkSection section, int x, int y, int z, BlockState state, boolean lock, Operation<BlockState> original, @Local BlockPos.MutableBlockPos pos) {
		OwoCopium.note(pos, state);
		return original.call(section, x, y, z, state, lock);
	}

	@Inject(method = "doPlace", at = @At("RETURN"), require = 1)
	private void mms_uproot$flushCopium(WorldGenLevel level, RandomSource random, double startX, double endX, double startZ, double endZ, double startY, double endY,
			int x, int y, int z, int width, int height, CallbackInfoReturnable<Boolean> cir) {
		OwoCopium.flush(level);
	}
}
