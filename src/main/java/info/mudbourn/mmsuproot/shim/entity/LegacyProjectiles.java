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

	/** {@code ProjectileUtil.getManyEntityHitResult}: 26.3's last flag picks the point each hit reports: true is where the ray entered the entity's own box, false where it entered the margin-widened box. Older callers always got the first, so this passes true. */
	public static Collection<EntityHitResult> getManyEntityHitResult(Level level, Entity owner, Vec3 from, Vec3 to, AABB area, Predicate<Entity> filter,
			float margin, ClipContext.Block block, boolean hitInside) {
		return ProjectileUtil.getManyEntityHitResult(level, owner, from, to, area, filter, margin, block, hitInside, true);
	}
}
