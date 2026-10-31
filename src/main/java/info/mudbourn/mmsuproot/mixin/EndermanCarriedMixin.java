package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.entity.LegacyRenderStates;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11 put the carried {@code BlockState} itself in {@code EndermanRenderState.carriedBlock}; 26.3 keeps only its model there.
 * This keeps the state alongside for the mods that read it. See {@link LegacyRenderStates#carriedBlock}.
 */
@Mixin(EndermanRenderer.class)
public abstract class EndermanCarriedMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/Enderman;Lnet/minecraft/client/renderer/entity/state/EndermanRenderState;F)V", at = @At("TAIL"))
	private void mms_uproot$keepCarried(Enderman enderman, EndermanRenderState state, float partialTick, CallbackInfo ci) {
		LegacyRenderStates.setCarriedBlock(state, enderman.getCarriedBlock());
	}
}
