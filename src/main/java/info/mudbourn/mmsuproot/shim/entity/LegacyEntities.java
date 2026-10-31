package info.mudbourn.mmsuproot.shim.entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;

/** Entity and player calls that 26.3 removed or split. */
public final class LegacyEntities {
	private LegacyEntities() { }

	/** {@code EntityType.is(TagKey)}: tags are tested through the type's registry holder now. */
	public static boolean isTagged(EntityType<?> type, TagKey<EntityType<?>> tag) {
		return type.builtInRegistryHolder().is(tag);
	}

	/** {@code Player.displayClientMessage(Component, boolean)}: the flag chose the action bar over chat. */
	public static void displayClientMessage(Player player, Component message, boolean overlay) {
		if (overlay) {
			player.sendOverlayMessage(message);
		} else {
			player.sendSystemMessage(message);
		}
	}

	/** {@code Player.drop(ItemStack, boolean)}: a menu drops as the player's own action, which 26.3 says is predicted by the client. */
	public static ItemEntity drop(Player player, ItemStack stack, boolean randomly) {
		return player.drop(stack, randomly, Prediction.PREDICTED);
	}

	/** The fixed one-per-entity value a mod kept in {@code LivingEntity.attackAnim}; 26.3 keeps the swing state there. */
	private static final Map<LivingEntity, Float> ATTACK_ANIM = Collections.synchronizedMap(new WeakHashMap<>());

	/**
	 * {@code LivingEntity.swing(InteractionHand)}, the plain attack swing. 26.3 only has the form that takes the animation:
	 * the one the held item brings, as {@code Mob.swingForAttack} does.
	 */
	public static void swingForAttack(LivingEntity entity, InteractionHand hand) {
		entity.swing(hand, entity.getItemInHand(hand).getAttackAnimation(), false);
	}

	/** {@code LivingEntity.swing(InteractionHand, boolean)}: the flag said whether to tell the swinging player too. */
	public static void swing(LivingEntity entity, InteractionHand hand, boolean updateSelf) {
		entity.swing(hand, SwingAnimation.DEFAULT, updateSelf);
	}

	/** {@code LivingEntity.updateSwingTime()} ticked the swing by hand; 26.3 ticks it with the entity, so there is nothing to do. */
	public static void updateSwingTime(LivingEntity entity) { }

	/** The {@code updateSwingTime()} a mod class adds to a living entity, by class; empty when it has none. */
	private static final ClassValue<Optional<MethodHandle>> SWING_TICK = new ClassValue<>() {
		@Override
		protected Optional<MethodHandle> computeValue(Class<?> type) {
			try {
				return Optional.of(MethodHandles.privateLookupIn(type, MethodHandles.lookup())
						.findVirtual(type, "updateSwingTime", MethodType.methodType(void.class)));
			} catch (NoSuchMethodException e) {
				return Optional.empty();
			} catch (IllegalAccessException e) {
				throw new IllegalStateException("Uproot could not reach updateSwingTime of " + type.getName(), e);
			}
		}
	};

	/**
	 * Called at the end of every living entity tick (mixin.LegacySwingMixin). 1.21.11 called {@code updateSwingTime()} there, and a
	 * mod could override it to change how the swing ticks; the call is gone, so this makes it for a class that has the override.
	 * The value the override kept in {@code attackAnim} lasts one tick, as the swing progress it replaced did.
	 */
	public static void tickSwing(LivingEntity entity) {
		Optional<MethodHandle> tick = SWING_TICK.get(entity.getClass());
		if (tick.isEmpty()) return;
		ATTACK_ANIM.remove(entity);
		try {
			tick.get().invoke(entity);
		} catch (Throwable t) {
			throw new IllegalStateException("Uproot could not tick the swing of an entity", t);
		}
	}

	/** The swing progress a mod set through {@code attackAnim}, which the entity is drawn with; null when none. */
	public static Float swingOverride(LivingEntity entity) {
		return ATTACK_ANIM.isEmpty() ? null : ATTACK_ANIM.get(entity);
	}

	/** {@code LivingEntity.onEquippedItemBroken(Item, EquipmentSlot)} took the item; 26.3 takes a stack of it. */
	public static void onEquippedItemBroken(LivingEntity entity, net.minecraft.world.item.Item item, EquipmentSlot slot) {
		entity.onEquippedItemBroken(new ItemStack(item), slot);
	}

	/** {@code LivingEntity.entityAttackRange()}: the reach with whatever the entity holds in its main hand. */
	public static AttackRange entityAttackRange(LivingEntity entity) {
		return entity.getAttackRangeWith(entity.getMainHandItem());
	}

	/**
	 * {@code LivingEntity.attackAnim}, the swing progress. A mod that kept a flag of its own in the field gets that back;
	 * otherwise it is the progress of the swing now.
	 */
	public static float attackAnim(LivingEntity entity) {
		Float own = ATTACK_ANIM.get(entity);
		return own != null ? own : entity.getSwingAnimation(1.0F);
	}

	/** A write to {@code LivingEntity.attackAnim}: kept per entity, since 26.3 has no field to hold it. */
	public static void setAttackAnim(LivingEntity entity, float value) {
		ATTACK_ANIM.put(entity, value);
	}

	/** {@code Entity.loadEntityRecursive(CompoundTag, ...)}: 26.3 reads entities through a value input. */
	public static Entity loadEntityRecursive(CompoundTag tag, Level level, EntitySpawnReason reason, EntityProcessor processor) {
		return EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag), level, reason, processor);
	}

	/** {@code new ServerBossEvent(Component, color, overlay)}: the id is part of the constructor now; 1.21.11 made one up. */
	public static Object[] serverBossEvent(Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
		return new Object[] {UUID.randomUUID(), name, color, overlay};
	}

	/** {@code new ItemParticleOption(type, ItemStack)}: 26.3 describes the item by a template of it. */
	public static Object[] itemParticleOption(ParticleType<ItemParticleOption> type, ItemStack stack) {
		return new Object[] {type, ItemStackTemplate.fromNonEmptyStack(stack)};
	}

	private static Field swingState;
	private static Field swingTicks;

	/** {@code LivingEntity.swingTime}, the ticks into the current swing; 26.3 keeps them in the swing state. */
	public static int swingTime(LivingEntity entity) {
		try {
			if (swingTicks == null) {
				swingState = LivingEntity.class.getDeclaredField("swingState");
				swingState.setAccessible(true);
				swingTicks = swingState.getType().getDeclaredField("ticks");
				swingTicks.setAccessible(true);
			}
			return swingTicks.getInt(swingState.get(entity));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Uproot could not read the swing ticks of an entity", e);
		}
	}

	/** {@code LivingEntity.swingingArm}: the hand of the swing going on, or null when there is none. */
	public static InteractionHand swingingArm(LivingEntity entity) {
		LivingEntity.SwingDescription swing = entity.getCurrentSwing();
		return swing == null ? null : swing.hand();
	}

	private static VarHandle wasOnGround;

	private static VarHandle wasOnGround() {
		if (wasOnGround == null) {
			try {
				wasOnGround = MethodHandles.privateLookupIn(AbstractCubeMob.class, MethodHandles.lookup()).findVarHandle(AbstractCubeMob.class, "wasOnGround", boolean.class);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Uproot could not reach the on-ground flag of a slime", e);
			}
		}
		return wasOnGround;
	}

	/** {@code Slime.wasOnGround}: the flag moved into the cube mob base class, where it is private. */
	public static boolean wasOnGround(AbstractCubeMob mob) {
		return (boolean) wasOnGround().get(mob);
	}

	/** A write to {@code Slime.wasOnGround}. */
	public static void setWasOnGround(AbstractCubeMob mob, boolean value) {
		wasOnGround().set(mob, value);
	}
}
