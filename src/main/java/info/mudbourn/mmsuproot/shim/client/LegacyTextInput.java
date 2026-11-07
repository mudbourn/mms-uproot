package info.mudbourn.mmsuproot.shim.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;

/**
 * 1.21.11 read the keyboard through GLFW, whose character callback was always on, so any screen that declared
 * {@code charTyped} got typed characters. 26.3 reads it through SDL, which sends text only after
 * {@code SDL_StartTextInput}: a focused {@code EditBox} asks for it, and {@code AbstractSignEditScreen} asks for it
 * at the start of {@code init()} and gives it back in {@code removed()}. A 1.21.11 mod screen that types its own text
 * does neither, so its keys arrive but its characters never do. This asks for text input on such a screen the way
 * the sign screen does.
 */
public final class LegacyTextInput {
	private static final ClassValue<Boolean> TYPES_OWN_TEXT = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			for (Class<?> c = type; c != null && c != Screen.class; c = c.getSuperclass()) {
				// Vanilla screens already ask for text input where they need it.
				if (c.getName().startsWith("net.minecraft.")) return false;
				try {
					c.getDeclaredMethod("charTyped", CharacterEvent.class);
					return true;
				} catch (NoSuchMethodException e) {
					// not declared here; the superclass may
				}
			}
			return false;
		}
	};

	private LegacyTextInput() { }

	/** Called when a screen is initialised: starts text input for a mod screen that types its own text. */
	public static void start(Screen screen) {
		if (TYPES_OWN_TEXT.get(screen.getClass())) Minecraft.getInstance().textInputManager().startTextInput(screen);
	}

	/** Called when a screen is replaced: stops text input if this screen still holds it. */
	public static void stop(Screen screen) {
		if (screen != null && TYPES_OWN_TEXT.get(screen.getClass())) Minecraft.getInstance().textInputManager().stopTextInput(screen);
	}
}
