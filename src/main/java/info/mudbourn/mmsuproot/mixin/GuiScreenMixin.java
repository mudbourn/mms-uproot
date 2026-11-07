package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.client.ScreenBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends a screen change through {@code Minecraft.setScreen} first (see {@link ScreenBridge}), which runs the mod
 * injectors on it and ends back here with a change that skips this detour. The change that started here is then
 * always cancelled: either the forwarded one carried it out, or a mod injector cancelled it. The low priority puts
 * this at the head of the method ahead of other injectors, which the cancel then keeps from running twice.
 */
@Mixin(value = Gui.class, priority = 100)
public abstract class GuiScreenMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void mms_uproot$viaMinecraft(Screen screen, CallbackInfo ci) {
		if (ScreenBridge.consumeForwarded(screen)) return;
		((ScreenBridge.Entry) this.minecraft).setScreen(screen);
		ci.cancel();
	}
}
