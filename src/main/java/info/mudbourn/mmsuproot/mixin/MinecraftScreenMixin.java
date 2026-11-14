package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.client.ScreenBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds the {@code Minecraft.setScreen(Screen)} that 26.3 removed, so mixins written for it still find their target
 * (see {@link ScreenBridge}). The low priority applies this mixin before the mods' own: Mixin merges a method before
 * it applies the injectors of the mixins that come after.
 */
@Mixin(value = Minecraft.class, priority = 100)
public abstract class MinecraftScreenMixin implements ScreenBridge.Entry {
	/** The write that 1.21.11's {@code setScreen} made to its screen field, which injectors written for it aim at (renames.txt points them here). */
	@Unique
	private Screen uprootScreen;

	@Override
	public void setScreen(Screen screen) {
		this.uprootScreen = screen;
		ScreenBridge.forward((Minecraft) (Object) this, screen);
	}
}
