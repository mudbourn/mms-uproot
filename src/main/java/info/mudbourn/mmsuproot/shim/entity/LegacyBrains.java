package info.mudbourn.mmsuproot.shim.entity;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Brain calls of 1.21.11 whose form changed. A provider is made with the activities of the mob in 26.3, and an activity is
 * added with its memory conditions and the memories it erases; a mob that builds its brain by hand adds the activities itself,
 * which still works, so the old calls fill in what they did not say.
 */
public final class LegacyBrains {
	private LegacyBrains() { }

	/** {@code Brain.provider(memoryTypes, sensorTypes)}: a provider that has no activities of its own. */
	public static <E extends LivingEntity> Brain.Provider<E> provider(Collection<? extends MemoryModuleType<?>> memories,
			Collection<? extends SensorType<? extends Sensor<? super E>>> sensors) {
		return Brain.provider(memories, sensors, entity -> List.of());
	}

	/** {@code Brain.addActivity(activity, behaviors)}: no conditions, nothing erased when it stops. */
	public static <E extends LivingEntity> void addActivity(Brain<E> brain, Activity activity,
			ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> behaviors) {
		brain.addActivity(activity, behaviors, Set.of(), Set.of());
	}

	/** {@code Brain.addActivityWithConditions(activity, behaviors, conditions)}. */
	public static <E extends LivingEntity> void addActivityWithConditions(Brain<E> brain, Activity activity,
			ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super E>>> behaviors,
			Set<Pair<MemoryModuleType<?>, MemoryStatus>> conditions) {
		brain.addActivity(activity, behaviors, conditions, Set.of());
	}

	/**
	 * {@code Brain.copyWithoutBehaviors()}: a brain with the same memories and sensors and no behaviors. The
	 * brain is not copied here; its behaviors are cleared and the caller adds its activities again.
	 */
	public static <E extends LivingEntity> Brain<E> copyWithoutBehaviors(Brain<E> brain) {
		brain.removeAllBehaviors();
		return brain;
	}
}
