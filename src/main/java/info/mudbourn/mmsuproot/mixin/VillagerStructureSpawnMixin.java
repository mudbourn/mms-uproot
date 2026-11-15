package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.entity.StructureSpawnedVillager;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.11's villager remembered that a structure had spawned it ({@code assignProfessionWhenSpawned()}), from its spawn until its first
 * server tick, and saved the flag meanwhile under {@code AssignProfessionWhenSpawned}. 26.3 dropped it; Guard Villagers reads it to decide
 * which new villagers may get a guard.
 */
@Mixin(Villager.class)
public abstract class VillagerStructureSpawnMixin implements StructureSpawnedVillager {
	@Unique
	private boolean mms_uproot$structureSpawned;

	@Override
	public boolean mms_uproot$assignProfessionWhenSpawned() {
		return mms_uproot$structureSpawned;
	}

	@Inject(method = "finalizeSpawn", at = @At("HEAD"))
	private void mms_uproot$markStructureSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData data,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		if (reason == EntitySpawnReason.STRUCTURE) mms_uproot$structureSpawned = true;
	}

	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void mms_uproot$clearAfterFirstTick(CallbackInfo ci) {
		mms_uproot$structureSpawned = false;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void mms_uproot$saveStructureSpawn(ValueOutput output, CallbackInfo ci) {
		if (mms_uproot$structureSpawned) output.putBoolean("AssignProfessionWhenSpawned", true);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void mms_uproot$loadStructureSpawn(ValueInput input, CallbackInfo ci) {
		mms_uproot$structureSpawned = input.getBooleanOr("AssignProfessionWhenSpawned", false);
	}
}
