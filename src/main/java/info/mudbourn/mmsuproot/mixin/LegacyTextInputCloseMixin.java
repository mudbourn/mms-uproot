package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.client.LegacyTextInput;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the text input {@link LegacyTextInputMixin} started when its screen is replaced. This hooks the screen
 * change rather than {@code removed()}, which a mod screen may override without calling up. The stop only takes
 * effect while that screen still holds text input, as with 26.3's sign screen.
 */
@Mixin(Gui.class)
public abstract class LegacyTextInputCloseMixin {
	@Shadow
	private Screen screen;

	@Inject(method = "setScreen", at = @At("HEAD"))
	private void mms_uproot$stopTextInput(Screen next, CallbackInfo ci) {
		if (this.screen != next) LegacyTextInput.stop(this.screen);
	}
}
