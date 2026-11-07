package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.client.LegacyTextInput;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Starts SDL text input when a 1.21.11 mod screen that types its own text is initialised, at the point where
 * 26.3's sign screen starts it (see {@link LegacyTextInput}). {@code init(int, int)} is final, so a mod screen cannot
 * skip it.
 */
@Mixin(Screen.class)
public abstract class LegacyTextInputMixin {
	@Inject(method = "init(II)V", at = @At("HEAD"))
	private void mms_uproot$startTextInput(int width, int height, CallbackInfo ci) {
		LegacyTextInput.start((Screen) (Object) this);
	}
}
