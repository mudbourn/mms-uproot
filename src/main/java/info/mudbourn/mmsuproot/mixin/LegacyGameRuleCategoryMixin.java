package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.compat.CustomGameRuleCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla names a game rule category from a language key built from its id. A category a mod made through Fabric's old
 * {@code CustomGameRuleCategory} carried its own name instead, and the mod may have no language entry for the key, so
 * {@link CustomGameRuleCategory} keeps the name and the label shows it.
 */
@Mixin(GameRuleCategory.class)
public abstract class LegacyGameRuleCategoryMixin {
	@Inject(method = "label", at = @At("HEAD"), cancellable = true)
	private void mms_uproot$legacyName(CallbackInfoReturnable<MutableComponent> cir) {
		Component name = CustomGameRuleCategory.nameOf((GameRuleCategory) (Object) this);
		if (name != null) cir.setReturnValue(name.copy());
	}
}
