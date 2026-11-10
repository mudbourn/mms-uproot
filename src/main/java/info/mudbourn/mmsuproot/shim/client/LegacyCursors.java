package info.mudbourn.mmsuproot.shim.client;

import org.lwjgl.sdl.SDLMouse;

/**
 * Cursor calls of mods built before 26.3 that went to GLFW. 26.3 sets the mouse cursor through SDL, whose system cursors have
 * their own ids; the window handle a mod passes is not needed, SDL has one cursor per application.
 */
public final class LegacyCursors {
	private LegacyCursors() { }

	/** {@code glfwCreateStandardCursor(shape)}: a system cursor of SDL, for a GLFW shape constant (0x36001 and up). */
	public static long createStandardCursor(int glfwShape) {
		int id = switch (glfwShape) {
			case 0x36001 -> 0; // arrow
			case 0x36002 -> 1; // text
			case 0x36003 -> 3; // crosshair
			case 0x36004 -> 11; // pointer
			case 0x36005 -> 7; // east-west resize
			case 0x36006 -> 8; // north-south resize
			case 0x36007 -> 5; // north-west to south-east resize
			case 0x36008 -> 6; // north-east to south-west resize
			case 0x36009 -> 9; // move
			case 0x3600A -> 10; // not allowed
			default -> 0;
		};
		return SDLMouse.SDL_CreateSystemCursor(id);
	}

	/** {@code glfwSetCursor(window, cursor)}: cursor 0 is the default one. */
	public static void setCursor(long window, long cursor) {
		SDLMouse.SDL_SetCursor(cursor == 0L ? SDLMouse.SDL_GetDefaultCursor() : cursor);
	}

	/** {@code glfwDestroyCursor(cursor)}. */
	public static void destroyCursor(long cursor) {
		if (cursor != 0L) SDLMouse.SDL_DestroyCursor(cursor);
	}
}
