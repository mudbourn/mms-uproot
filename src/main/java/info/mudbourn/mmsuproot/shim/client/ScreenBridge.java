package info.mudbourn.mmsuproot.shim.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keeps {@code Minecraft.setScreen(Screen)} alive for mods that inject into it. 26.3 moved the method to
 * {@code Gui}, so {@code MinecraftScreenMixin} gives {@code Minecraft} a method of that name again, which forwards
 * to {@code Gui.setScreen}, and {@code GuiScreenMixin} makes every screen change pass through it first. A mod
 * injector on the old method then runs, and can cancel the change, as it did before. Both calls happen on the
 * render thread.
 */
public final class ScreenBridge {
	/** The screen {@link #forward} is handing to {@code Gui.setScreen}; that call skips the detour once. */
	private static Screen forwarding;
	private static boolean forwarded;

	private ScreenBridge() { }

	/** What {@code Minecraft} gets from {@code MinecraftScreenMixin}: the old method, which code compiled against 26.3 cannot name on {@code Minecraft}. */
	public interface Entry {
		void setScreen(Screen screen);
	}

	/** Body of the {@code Minecraft.setScreen} that Uproot adds: runs after the mod injectors at its head. */
	public static void forward(Minecraft minecraft, Screen screen) {
		forwarding = screen;
		forwarded = true;
		try {
			minecraft.gui.setScreen(screen);
		} finally {
			forwarded = false; // the call did not reach the head of Gui.setScreen (an exception), or consumed it already
			forwarding = null;
		}
	}

	/** Whether the {@code Gui.setScreen} call that just began is the one {@link #forward} made; consumes the mark. */
	public static boolean consumeForwarded(Screen screen) {
		if (!forwarded || forwarding != screen) return false;
		forwarded = false;
		return true;
	}
}
