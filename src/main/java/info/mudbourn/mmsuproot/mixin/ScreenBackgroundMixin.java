package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.client.LegacyScreens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives a container screen of a 1.21.11 mod back its {@code renderBg} hook: after the screen's background is
 * extracted, the old method that draws its panel is called (see {@link LegacyScreens}).
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void mms_uproot$legacyBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		if ((Object) this instanceof AbstractContainerScreen<?> screen) LegacyScreens.renderBg(screen, graphics, partialTick, mouseX, mouseY);
	}
}
