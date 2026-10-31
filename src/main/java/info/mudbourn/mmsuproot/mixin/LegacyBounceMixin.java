package info.mudbourn.mmsuproot.mixin;

import java.lang.reflect.Method;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.11 blocks bounced entities by overriding {@code updateEntityMovementAfterFallOn(BlockGetter, Entity)}, which 26.3 no
 * longer calls: a bounce is now the block's {@code getBounceRestitution}, applied by {@code Entity} itself. A block class that
 * still declares the old override (and that does not declare a restitution of its own) gets the factor the beds used, 0.66;
 * the override's own sneak check is {@code Entity.isSuppressingBounce} in 26.3.
 */
@Mixin(Block.class)
public abstract class LegacyBounceMixin {
	private static final ClassValue<Boolean> LEGACY_BOUNCE = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			for (Class<?> c = type; c != null && c != Block.class; c = c.getSuperclass()) {
				try {
					Method m = c.getDeclaredMethod("updateEntityMovementAfterFallOn", BlockGetter.class, Entity.class);
					return m != null;
				} catch (NoSuchMethodException e) {
					// keep looking up the hierarchy
				}
			}
			return false;
		}
	};

	@Inject(method = "getBounceRestitution", at = @At("HEAD"), cancellable = true)
	private void mms_uproot$legacyBounce(CallbackInfoReturnable<Float> cir) {
		if (LEGACY_BOUNCE.get(getClass())) cir.setReturnValue(0.66F);
	}
}
