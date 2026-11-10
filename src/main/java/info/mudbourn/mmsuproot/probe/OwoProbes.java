package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import info.mudbourn.mmsuproot.shim.client.LegacyKeyCodes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * owo-lib's config screens, which every owo-config mod here (hwr and the Expanded * Enchanting mods) registers in
 * {@code ConfigScreenProviders}. Typing goes the way the game sends it: {@code KeyboardHandler.keyPress} for keys and
 * {@code textInput} (which becomes {@code charTyped}) for text. owo's {@code ConfigScreen.keyPressed} answers Ctrl+F (its key code
 * 70 is a GLFW code, which {@code LegacyKeyCodes.key} hands it) by focusing the {@code search-field} text box, and Enter in that box
 * by searching the options for the text.
 */
final class OwoProbes {
	private static final int PRESS = 1;
	/** GLFW codes of the keys used below; each goes through {@link LegacyKeyCodes#keyEvent}, as a legacy mod's own KeyEvent would. */
	private static final int GLFW_A = 65, GLFW_F = 70, GLFW_RETURN = 257;
	/** {@code KMOD_LCTRL}, what owo's {@code hasControlDown()} reads (the raw Ctrl bits, not the platform's edit-shortcut key). */
	private static final int MOD_CTRL = 64;

	private OwoProbes() { }

	static List<Probe> all() {
		List<Probe> list = new ArrayList<>();
		Map<String, Function<Screen, ? extends Screen>> providers = providers();
		if (providers.isEmpty()) {
			list.add(Probe.once("owo.config-screen-providers", c -> {
				throw new Probe.Fail("owo has no registered config screen provider");
			}));
		}
		for (Map.Entry<String, Function<Screen, ? extends Screen>> e : providers.entrySet()) {
			list.add(Probe.steps("owo.config-screen." + e.getKey() + ".opens-and-renders", 120, (c, t) -> open(c, t, e.getKey(), e.getValue())));
		}
		Function<Screen, ? extends Screen> hwr = providers.get("hwr");
		if (hwr != null) {
			list.add(Probe.steps("owo.config-screen.hwr.typing-select-all-search", 200, (c, t) -> typing(c, t, hwr)));
		} else {
			list.add(Probe.once("owo.config-screen.hwr.typing-select-all-search", c -> {
				throw new Probe.Skip("no config screen provider for hwr");
			}));
		}
		list.add(Probe.eyes("owo.config-screen-layout", "open each Expanded * Enchanting config screen from Mod Menu and confirm labels, toggles and the search box do not overlap or clip"));
		return list;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Function<Screen, ? extends Screen>> providers() {
		Map<String, Function<Screen, ? extends Screen>> map = new LinkedHashMap<>();
		try {
			Class<?> cls = Class.forName("io.wispforest.owo.config.ui.ConfigScreenProviders", true, OwoProbes.class.getClassLoader());
			Method forEach = cls.getMethod("forEach", BiConsumer.class);
			forEach.invoke(null, (BiConsumer<String, Function<Screen, ? extends Screen>>) map::put);
		} catch (ReflectiveOperationException | LinkageError e) {
			// no owo, no provider: the probe list says so
		}
		return map;
	}

	private static void render(Minecraft mc, Screen screen) {
		GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(mc, new GuiRenderState(), 10, 10);
		screen.extractRenderStateWithTooltipAndSubtitles(graphics, 10, 10, 1.0F);
	}

	private static String open(Probe.Ctx c, int tick, String id, Function<Screen, ? extends Screen> provider) {
		Minecraft mc = c.mc;
		if (tick == 1) {
			Screen screen = provider.apply(mc.gui.screen());
			if (screen == null) throw new Probe.Fail("the provider of " + id + " returned no screen");
			c.state.put("screen", screen);
			mc.gui.setScreen(screen);
			return null;
		}
		Screen screen = (Screen) c.state.get("screen");
		if (tick < 6) return null;
		if (mc.gui.screen() != screen) throw new Probe.Fail("the screen closed itself: now " + mc.gui.screen());
		for (int i = 0; i < 3; i++) render(mc, screen);
		if (mc.gui.screen() != screen) throw new Probe.Fail("the screen closed itself while rendering");
		mc.gui.setScreen(null);
		return screen.getClass().getName() + " opened, ran " + (tick - 1) + " ticks and 3 extracted frames, still open";
	}

	private static Object field(Object target, String name) throws ReflectiveOperationException {
		for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
			try {
				Field f = k.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(target);
			} catch (NoSuchFieldException e) {
				// try the superclass
			}
		}
		throw new NoSuchFieldException(name + " in " + target.getClass());
	}

	private static Object root(Screen screen) throws ReflectiveOperationException {
		return field(field(screen, "uiAdapter"), "rootComponent");
	}

	private static EditBox searchField(Screen screen) throws ReflectiveOperationException {
		Object root = root(screen);
		Class<?> component = Class.forName("io.wispforest.owo.ui.core.UIComponent", true, OwoProbes.class.getClassLoader());
		Object found = root.getClass().getMethod("childById", Class.class, String.class).invoke(root, component, "search-field");
		if (!(found instanceof EditBox box)) throw new Probe.Fail("no search-field text box on the screen: " + found);
		return box;
	}

	private static Object focused(Screen screen) throws ReflectiveOperationException {
		Object root = root(screen);
		Object handler = root.getClass().getMethod("focusHandler").invoke(root);
		return handler.getClass().getMethod("focused").invoke(handler);
	}

	private static void key(Minecraft mc, int glfwKey, int mods) {
		Object[] k = LegacyKeyCodes.keyEvent(glfwKey, 0, 0);
		mc.keyboardHandler.keyPress(mc.getWindow().handle(), PRESS, new KeyEvent((int) k[0], (int) k[1], mods));
	}

	private static void type(Minecraft mc, String text) {
		mc.keyboardHandler.textInput(mc.getWindow().handle(), text);
	}

	private static String typing(Probe.Ctx c, int tick, Function<Screen, ? extends Screen> provider) throws Exception {
		Minecraft mc = c.mc;
		if (tick == 1) {
			Screen screen = provider.apply(mc.gui.screen());
			c.state.put("screen", screen);
			mc.gui.setScreen(screen);
			return null;
		}
		if (tick < 8) return null;
		Screen screen = (Screen) c.state.get("screen");
		if (mc.gui.screen() != screen) throw new Probe.Fail("the screen closed itself: now " + mc.gui.screen());
		EditBox search = searchField(screen);

		// before Ctrl+F the search box is not the focused component
		if (focused(screen) == search) throw new Probe.Fail("the search box already has focus before Ctrl+F");
		key(mc, GLFW_F, MOD_CTRL);
		if (focused(screen) != search) throw new Probe.Fail("Ctrl+F did not focus the search box; focused is " + focused(screen));

		type(mc, "pot");
		if (!"pot".equals(search.getValue())) throw new Probe.Fail("typing 'pot' into the search box gave '" + search.getValue() + "'");

		key(mc, GLFW_A, InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER);
		type(mc, "x");
		if (!"x".equals(search.getValue())) throw new Probe.Fail("Ctrl+A then 'x' left '" + search.getValue() + "', want 'x' (select-all did not select)");

		key(mc, GLFW_A, InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER);
		type(mc, "potion");
		if (!"potion".equals(search.getValue())) throw new Probe.Fail("Ctrl+A then 'potion' left '" + search.getValue() + "'");

		key(mc, GLFW_RETURN, 0);
		Object matches = field(screen, "currentMatches");
		if (matches == null) throw new Probe.Fail("Enter in the search box ran no search (currentMatches is null)");
		String query = (String) field(matches, "query");
		List<?> list = (List<?>) field(matches, "matches");
		if (!"potion".equals(query) || list.isEmpty()) throw new Probe.Fail("search for 'potion' gave query '" + query + "' and " + list.size() + " matches, want at least one (hwr has potion_recipe and potion_colour)");
		mc.gui.setScreen(null);
		return "Ctrl+F focused search-field; typed 'pot'; Ctrl+A + 'x' replaced it; 'potion' + Enter found " + list.size() + " option(s)";
	}
}
