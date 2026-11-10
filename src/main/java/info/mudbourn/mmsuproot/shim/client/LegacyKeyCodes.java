package info.mudbourn.mmsuproot.shim.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;

/**
 * Key codes for mods built before 26.3. Up to 26.2 a keyboard key was a GLFW key code ({@code KEY_R} is 82); 26.3
 * reads the keyboard through SDL and its codes are SDL scancodes ({@code KEY_R} is 21). A mod compiles its key code
 * into the jar, so the remap sends every key a mod hands to a {@code KeyMapping} or to {@code Type.getOrCreate}
 * through here. The mouse keeps its buttons, and a keyboard code with no entry below is unknown (-1).
 */
public final class LegacyKeyCodes {
	/**
	 * {GLFW code, 26.3 code} for every {@code InputConstants.KEY_*} constant that both 1.21.11 and 26.3 declare, joined by the
	 * constant's name (read from both jars). The two Super keys are 1.21.11's {@code KEY_LSUPER} and {@code KEY_RSUPER},
	 * which 26.3 names {@code KEY_LGUI} and {@code KEY_RGUI}. {@code KEY_F25} has no 26.3 counterpart.
	 */
	private static final int[][] PAIRS = {
		{32, 44}, // KEY_SPACE
		{39, 52}, // KEY_APOSTROPHE
		{44, 54}, // KEY_COMMA
		{45, 45}, // KEY_MINUS
		{46, 55}, // KEY_PERIOD
		{47, 56}, // KEY_SLASH
		{48, 39}, // KEY_0
		{49, 30}, // KEY_1
		{50, 31}, // KEY_2
		{51, 32}, // KEY_3
		{52, 33}, // KEY_4
		{53, 34}, // KEY_5
		{54, 35}, // KEY_6
		{55, 36}, // KEY_7
		{56, 37}, // KEY_8
		{57, 38}, // KEY_9
		{59, 51}, // KEY_SEMICOLON
		{61, 46}, // KEY_EQUALS
		{65, 4}, // KEY_A
		{66, 5}, // KEY_B
		{67, 6}, // KEY_C
		{68, 7}, // KEY_D
		{69, 8}, // KEY_E
		{70, 9}, // KEY_F
		{71, 10}, // KEY_G
		{72, 11}, // KEY_H
		{73, 12}, // KEY_I
		{74, 13}, // KEY_J
		{75, 14}, // KEY_K
		{76, 15}, // KEY_L
		{77, 16}, // KEY_M
		{78, 17}, // KEY_N
		{79, 18}, // KEY_O
		{80, 19}, // KEY_P
		{81, 20}, // KEY_Q
		{82, 21}, // KEY_R
		{83, 22}, // KEY_S
		{84, 23}, // KEY_T
		{85, 24}, // KEY_U
		{86, 25}, // KEY_V
		{87, 26}, // KEY_W
		{88, 27}, // KEY_X
		{89, 28}, // KEY_Y
		{90, 29}, // KEY_Z
		{91, 47}, // KEY_LBRACKET
		{92, 49}, // KEY_BACKSLASH
		{93, 48}, // KEY_RBRACKET
		{96, 53}, // KEY_GRAVE
		{256, 41}, // KEY_ESCAPE
		{257, 40}, // KEY_RETURN
		{258, 43}, // KEY_TAB
		{259, 42}, // KEY_BACKSPACE
		{260, 73}, // KEY_INSERT
		{261, 76}, // KEY_DELETE
		{262, 79}, // KEY_RIGHT
		{263, 80}, // KEY_LEFT
		{264, 81}, // KEY_DOWN
		{265, 82}, // KEY_UP
		{266, 75}, // KEY_PAGEUP
		{267, 78}, // KEY_PAGEDOWN
		{268, 74}, // KEY_HOME
		{269, 77}, // KEY_END
		{280, 57}, // KEY_CAPSLOCK
		{281, 71}, // KEY_SCROLLLOCK
		{282, 83}, // KEY_NUMLOCK
		{283, 70}, // KEY_PRINTSCREEN
		{284, 72}, // KEY_PAUSE
		{290, 58}, // KEY_F1
		{291, 59}, // KEY_F2
		{292, 60}, // KEY_F3
		{293, 61}, // KEY_F4
		{294, 62}, // KEY_F5
		{295, 63}, // KEY_F6
		{296, 64}, // KEY_F7
		{297, 65}, // KEY_F8
		{298, 66}, // KEY_F9
		{299, 67}, // KEY_F10
		{300, 68}, // KEY_F11
		{301, 69}, // KEY_F12
		{302, 104}, // KEY_F13
		{303, 105}, // KEY_F14
		{304, 106}, // KEY_F15
		{305, 107}, // KEY_F16
		{306, 108}, // KEY_F17
		{307, 109}, // KEY_F18
		{308, 110}, // KEY_F19
		{309, 111}, // KEY_F20
		{310, 112}, // KEY_F21
		{311, 113}, // KEY_F22
		{312, 114}, // KEY_F23
		{313, 115}, // KEY_F24
		{320, 98}, // KEY_NUMPAD0
		{321, 89}, // KEY_NUMPAD1
		{322, 90}, // KEY_NUMPAD2
		{323, 91}, // KEY_NUMPAD3
		{324, 92}, // KEY_NUMPAD4
		{325, 93}, // KEY_NUMPAD5
		{326, 94}, // KEY_NUMPAD6
		{327, 95}, // KEY_NUMPAD7
		{328, 96}, // KEY_NUMPAD8
		{329, 97}, // KEY_NUMPAD9
		{330, 220}, // KEY_NUMPADCOMMA
		{332, 85}, // KEY_MULTIPLY
		{334, 87}, // KEY_ADD
		{335, 88}, // KEY_NUMPADENTER
		{336, 103}, // KEY_NUMPADEQUALS
		{340, 225}, // KEY_LSHIFT
		{341, 224}, // KEY_LCONTROL
		{342, 226}, // KEY_LALT
		{343, 227}, // KEY_LSUPER
		{344, 229}, // KEY_RSHIFT
		{345, 228}, // KEY_RCONTROL
		{346, 230}, // KEY_RALT
		{347, 231}, // KEY_RSUPER
	};

	private static final Map<Integer, Integer> TO_SDL = new HashMap<>();
	private static final Map<Integer, Integer> TO_GLFW = new HashMap<>();

	static {
		for (int[] pair : PAIRS) {
			TO_SDL.put(pair[0], pair[1]);
			TO_GLFW.putIfAbsent(pair[1], pair[0]);
		}
	}

	private LegacyKeyCodes() { }

	/** The 26.3 code for a GLFW key code; one with no entry (and -1, GLFW's unknown key) is -1. */
	public static int convert(int glfwCode) {
		return TO_SDL.getOrDefault(glfwCode, -1);
	}

	/** The GLFW key code for a 26.3 (SDL scancode) key code; one with no entry (and -1) is -1, GLFW's unknown key. */
	public static int toGlfw(int code) {
		return TO_GLFW.getOrDefault(code, -1);
	}

	private static int convert(InputConstants.Type type, int code) {
		return type == InputConstants.Type.KEYBOARD ? convert(code) : code;
	}

	/** {@code new KeyMapping(name, code, category)}: a keyboard key. */
	public static Object[] keyMapping(String name, int code, KeyMapping.Category category) {
		return new Object[] {name, convert(code), category};
	}

	/** {@code new KeyMapping(name, type, code, category)}. */
	public static Object[] keyMapping(String name, InputConstants.Type type, int code, KeyMapping.Category category) {
		return new Object[] {name, type, convert(type, code), category};
	}

	/** {@code new KeyMapping(name, type, code, category, order)}. */
	public static Object[] keyMapping(String name, InputConstants.Type type, int code, KeyMapping.Category category, int order) {
		return new Object[] {name, type, convert(type, code), category, order};
	}

	/** {@code type.getOrCreate(code)}. */
	public static InputConstants.Key getOrCreate(InputConstants.Type type, int code) {
		return type.getOrCreate(convert(type, code));
	}

	/**
	 * {@code KeyEvent.key()}: a mod built before 26.3 compares it with GLFW key codes it has compiled in, so it is handed the GLFW
	 * code of the pressed key.
	 */
	public static int key(KeyEvent event) {
		return toGlfw(event.key());
	}

	/** {@code KeyEvent.scancode()}: 26.3's key already is the layout-independent scancode. */
	public static int scancode(KeyEvent event) {
		return event.key();
	}

	/** SDL sets this bit on the key code of a key that has no character ({@code SDLK_SCANCODE_MASK}); the rest of the code is its scancode. */
	private static final int SCANCODE_MASK = 1 << 30;

	/**
	 * The 26.3 layout key code (SDL's keycode) of a key as a US layout types it: a printable key is its lowercase ASCII character,
	 * Return, Escape, Backspace, Tab and Delete are their control characters, and every other key is its scancode with
	 * {@code SDLK_SCANCODE_MASK} set. {@code InputWithModifiers.isCopy} and the like compare it with 97, 99, 118 and 120.
	 */
	private static int layoutKeycode(int glfwKey, int scancode) {
		if (scancode < 0) return 0;
		if (glfwKey >= 65 && glfwKey <= 90) return glfwKey + 32;
		if (glfwKey >= 32 && glfwKey <= 96) return glfwKey;
		return switch (glfwKey) {
			case 256 -> 27;
			case 257 -> 13;
			case 258 -> 9;
			case 259 -> 8;
			case 261 -> 127;
			default -> SCANCODE_MASK | scancode;
		};
	}

	/**
	 * {@code new KeyEvent(key, scancode, modifiers)}: the key is a GLFW code; 26.3 wants its SDL scancode, then the layout key code
	 * (see {@link #layoutKeycode}), then the modifiers. A key with no 26.3 counterpart is -1 with key code 0.
	 */
	public static Object[] keyEvent(int key, int scancode, int modifiers) {
		int sdl = convert(key);
		return new Object[] {sdl, layoutKeycode(key, sdl), modifiers};
	}

	/** {@code GLFW.glfwGetKeyScancode(key)}: the SDL scancode of a GLFW key, which is what 26.3 calls a key code. */
	public static int glfwGetKeyScancode(int key) {
		return convert(key);
	}

	/** {@code GLFW.glfwGetKey(window, key)}: {@code GLFW_PRESS} (1) while the key is down, else {@code GLFW_RELEASE} (0). The window is gone. */
	public static int glfwGetKey(long window, int key) {
		return isKeyDown(key) ? 1 : 0;
	}

	/** {@code InputConstants.isKeyDown(window, key)}: the window is gone and the key is a GLFW code. */
	public static boolean isKeyDown(Window window, int key) {
		return isKeyDown(key);
	}

	private static boolean isKeyDown(int glfwKey) {
		int sdl = convert(glfwKey);
		return sdl >= 0 && InputConstants.isKeyDown(sdl);
	}
}
