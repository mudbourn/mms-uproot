package info.mudbourn.mmsuproot.mixin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11 blocks bounced entities by overriding {@code updateEntityMovementAfterFallOn(BlockGetter, Entity)}, which
 * {@code Entity.move} called on the block under the entity after a vertical collision, with the velocity as it was before the
 * collision (the horizontal axes that collided already zeroed). 26.3 decides the bounce itself in
 * {@code restituteMovementAfterCollisions}, from the block's {@code getBounceRestitution}. For a block class that still
 * declares the old override, this runs the override at the end of that method on the velocity 1.21.11 gave it, replacing what
 * 26.3 worked out. An override that calls the removed {@code super} method gets the block default, which zeroed the vertical speed.
 */
@Mixin(Entity.class)
public abstract class LegacyBounceMixin {
	private static final ClassValue<Optional<MethodHandle>> LEGACY_BOUNCE = new ClassValue<>() {
		@Override
		protected Optional<MethodHandle> computeValue(Class<?> type) {
			for (Class<?> c = type; c != null && c != Block.class; c = c.getSuperclass()) {
				try {
					return Optional.of(MethodHandles.privateLookupIn(c, MethodHandles.lookup())
							.findVirtual(c, "updateEntityMovementAfterFallOn", MethodType.methodType(void.class, BlockGetter.class, Entity.class)));
				} catch (NoSuchMethodException e) {
					// keep looking up the hierarchy
				} catch (IllegalAccessException e) {
					throw new IllegalStateException("Uproot could not reach updateEntityMovementAfterFallOn of " + c.getName(), e);
				}
			}
			return Optional.empty();
		}
	};

	@Unique
	private Vec3 mms_uproot$beforeLanding;

	@Inject(method = "restituteMovementAfterCollisions", at = @At("HEAD"))
	private void mms_uproot$keepVelocity(BlockState state, boolean xCollision, boolean zCollision, Vec3 movement, CallbackInfo ci) {
		mms_uproot$beforeLanding = ((Entity) (Object) this).getDeltaMovement();
	}

	@Inject(method = "restituteMovementAfterCollisions", at = @At("TAIL"))
	private void mms_uproot$legacyBounce(BlockState state, boolean xCollision, boolean zCollision, Vec3 movement, CallbackInfo ci) {
		Vec3 before = mms_uproot$beforeLanding;
		mms_uproot$beforeLanding = null;
		Entity entity = (Entity) (Object) this;
		if (before == null || !entity.verticalCollision) return;
		Optional<MethodHandle> override = LEGACY_BOUNCE.get(state.getBlock().getClass());
		if (override.isEmpty()) return;
		entity.setDeltaMovement(xCollision ? 0.0 : before.x, before.y, zCollision ? 0.0 : before.z);
		try {
			override.get().invoke(state.getBlock(), entity.level(), entity);
		} catch (NoSuchMethodError e) {
			// the override called Block's own version, which is gone: it kept the horizontal speed and dropped the vertical
			entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
		} catch (Throwable t) {
			throw new IllegalStateException("Uproot could not run updateEntityMovementAfterFallOn of a block", t);
		}
	}
}
