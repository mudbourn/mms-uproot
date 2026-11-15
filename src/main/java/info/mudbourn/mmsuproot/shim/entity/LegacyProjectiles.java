package info.mudbourn.mmsuproot.shim.entity;

import java.util.Collection;
import java.util.function.Predicate;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Projectile helpers whose 26.3 form takes a flag more. */
public final class LegacyProjectiles {
	private LegacyProjectiles() { }

	/** {@code ProjectileUtil.getManyEntityHitResult}: 26.3 can report the point the margin-widened box was entered instead of the entity's own box; older callers always got the entity's own. */
	public static Collection<EntityHitResult> getManyEntityHitResult(Level level, Entity owner, Vec3 from, Vec3 to, AABB area, Predicate<Entity> filter,
			float margin, ClipContext.Block block, boolean hitInside) {
		return ProjectileUtil.getManyEntityHitResult(level, owner, from, to, area, filter, margin, block, hitInside, true);
	}
}
