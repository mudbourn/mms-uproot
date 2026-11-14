package info.mudbourn.mmsuproot.mixin;

import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The clock manager keys its clocks by the registry's own holders, which a loot condition built in code has no way to reach
 * ({@code TimeCheck.time} takes one now). A holder that names a clock by key is traded for the registry's holder of that key.
 * See {@code LegacyLoot.time}.
 */
@Mixin(ServerClockManager.class)
public abstract class ServerClockManagerMixin {
	@Shadow
	private Map<Holder<WorldClock>, ?> clocks;

	@ModifyVariable(method = "getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/clock/ServerClockManager$ServerClockInstance;", at = @At("HEAD"), argsOnly = true)
	private Holder<WorldClock> mms_uproot$registryHolder(Holder<WorldClock> holder) {
		if (clocks.containsKey(holder)) return holder;
		for (Holder<WorldClock> registered : clocks.keySet()) {
			if (holder.unwrapKey().equals(registered.unwrapKey())) return registered;
		}
		return holder;
	}
}
