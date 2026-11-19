package info.mudbourn.mmsuproot.shim.entity;

import java.util.Collection;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Projectile helpers whose 26.3 form takes an argument more. */
public final class LegacyProjectiles {
	private LegacyProjectiles() { }

	/** {@code ProjectileUtil.getManyEntityHitResult}: 26.3's last flag picks the point each hit reports: true is where the ray entered the entity's own box, false where it entered the margin-widened box. Older callers always got the first, so this passes true. */
	public static Collection<EntityHitResult> getManyEntityHitResult(Level level, Entity owner, Vec3 from, Vec3 to, AABB area, Predicate<Entity> filter,
			float margin, ClipContext.Block block, boolean hitInside) {
		return ProjectileUtil.getManyEntityHitResult(level, owner, from, to, area, filter, margin, block, hitInside, true);
	}

	/**
	 * {@code Projectile.mayBreak(ServerLevel)}: 26.3 also asks which block, for its new adventure-mode check, and the old call has none to give.
	 * This answers what the old method did: an impact projectile, with projectiles allowed to break blocks.
	 */
	public static boolean mayBreak(Projectile projectile, ServerLevel level) {
		return projectile.is(EntityTypeTags.IMPACT_PROJECTILES) && level.getGameRules().get(GameRules.PROJECTILES_CAN_BREAK_BLOCKS);
	}
}
