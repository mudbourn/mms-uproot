package info.mudbourn.mmsuproot.shim.entity;

import java.util.List;

import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.level.Level;

/**
 * What a mob with a brain of 1.21.11 extends in place of {@link PathfinderMob} (a {@code super} rule of the rename table).
 * That version let a mob override {@code brainProvider()} and {@code makeBrain(Dynamic)} and build the brain from them;
 * 26.3 makes the brain from the saved memories through {@code makeBrain(Brain.Packed)} and a provider that takes the
 * entity. The old pair is kept here: the new call reaches the mob's {@code makeBrain(Dynamic)}, and the provider's
 * brain is made with the memories that were saved.
 */
public abstract class LegacyBrainMob extends PathfinderMob {
	/** The memories 26.3 handed to {@code makeBrain(Packed)}, for the {@code makeBrain(Dynamic)} that runs inside it. */
	private static final ThreadLocal<Brain.Packed> SAVED = new ThreadLocal<>();

	protected LegacyBrainMob(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
	}

	/** 1.21.11's {@code LivingEntity.brainProvider()}: a brain with no memories or sensors, which a mob replaces. */
	protected Brain.Provider<?> brainProvider() {
		return LegacyBrains.provider(List.of(), List.of());
	}

	/** 1.21.11's {@code LivingEntity.makeBrain(Dynamic)}: the brain of the provider, with the memories being loaded. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	protected Brain<?> makeBrain(Dynamic<?> dynamic) {
		Brain.Packed saved = SAVED.get();
		return ((Brain.Provider) brainProvider()).makeBrain(this, saved != null ? saved : Brain.Packed.EMPTY);
	}

	@Override
	protected Brain<?> makeBrain(Brain.Packed packed) {
		Brain.Packed outer = SAVED.get();
		SAVED.set(packed);
		try {
			return makeBrain(new Dynamic<>(NbtOps.INSTANCE, new CompoundTag()));
		} finally {
			if (outer == null) SAVED.remove();
			else SAVED.set(outer);
		}
	}
}
