package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Field;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import info.mudbourn.mmsuproot.shim.client.LegacyKeyCodes;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;

/** Pure checks of {@link LegacyKeyCodes}; they need no world. */
final class KeyProbes {
	private KeyProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("keys.table-roundtrip", KeyProbes::roundTrip),
			Probe.once("keys.unmapped", KeyProbes::unmapped),
			Probe.once("keys.keyEvent-letters", KeyProbes::letters),
			Probe.once("keys.ctrl-a-select-all", KeyProbes::selectAll),
			Probe.once("keys.isKeyDown-shim", KeyProbes::isKeyDown));
	}

	private static int[][] pairs() throws ReflectiveOperationException {
		Field f = LegacyKeyCodes.class.getDeclaredField("PAIRS");
		f.setAccessible(true);
		return (int[][]) f.get(null);
	}

	private static String roundTrip(Probe.Ctx c) throws Exception {
		int[][] pairs = pairs();
		int bad = 0;
		StringBuilder why = new StringBuilder();
		for (int[] p : pairs) {
			if (LegacyKeyCodes.convert(p[0]) != p[1] || LegacyKeyCodes.toGlfw(p[1]) != p[0]) {
				bad++;
				if (why.length() < 200) why.append(" glfw=").append(p[0]).append("/sdl=").append(p[1]).append("->").append(LegacyKeyCodes.convert(p[0])).append("/").append(LegacyKeyCodes.toGlfw(p[1]));
			}
		}
		if (bad > 0) throw new Probe.Fail(bad + " of " + pairs.length + " table entries do not round-trip:" + why);
		return pairs.length + " entries convert GLFW->26.3->GLFW unchanged";
	}

	private static String unmapped(Probe.Ctx c) {
		// GLFW_KEY_UNKNOWN, a code in no table, KEY_F25 (no 26.3 counterpart), a mouse-sized value and a SDL code that is not in the table
		int[] glfw = {-1, 0, 31, 314, 999, 100000};
		for (int g : glfw) {
			if (LegacyKeyCodes.convert(g) != -1) throw new Probe.Fail("convert(" + g + ") = " + LegacyKeyCodes.convert(g) + ", want -1");
		}
		if (LegacyKeyCodes.toGlfw(-1) != -1 || LegacyKeyCodes.toGlfw(100000) != -1) throw new Probe.Fail("toGlfw of an unknown code is not -1");
		Object[] ev = LegacyKeyCodes.keyEvent(314, 0, 0);
		if ((int) ev[0] != -1 || (int) ev[1] != 0) throw new Probe.Fail("keyEvent(KEY_F25) = key " + ev[0] + " keycode " + ev[1] + ", want -1 and 0");
		return "convert is -1 for " + glfw.length + " unmapped codes; keyEvent(F25) is key -1, keycode 0";
	}

	private static String letters(Probe.Ctx c) {
		for (int g = 65; g <= 90; g++) {
			Object[] ev = LegacyKeyCodes.keyEvent(g, 0, 0);
			int sdl = (int) ev[0];
			int keycode = (int) ev[1];
			int want = 'a' + (g - 65);
			if (sdl != 4 + (g - 65)) throw new Probe.Fail("keyEvent(" + g + ") scancode " + sdl + ", want " + (4 + (g - 65)));
			if (keycode != want) throw new Probe.Fail("keyEvent(" + g + ") keycode " + keycode + ", want lowercase '" + (char) want + "' (" + want + ")");
			KeyEvent real = new KeyEvent(sdl, keycode, 0);
			if (LegacyKeyCodes.key(real) != g) throw new Probe.Fail("key(KeyEvent for " + (char) g + ") = " + LegacyKeyCodes.key(real) + ", want " + g);
		}
		return "A-Z: scancode 4..29, keycode = lowercase ASCII, key() returns the GLFW code";
	}

	private static String selectAll(Probe.Ctx c) {
		Object[] a = LegacyKeyCodes.keyEvent(65, 0, 0);
		int mod = InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER;
		KeyEvent ctrlA = new KeyEvent((int) a[0], (int) a[1], mod);
		if (!ctrlA.isSelectAll()) throw new Probe.Fail("KeyEvent built by keyEvent(65) with the edit-shortcut modifier " + mod + " is not isSelectAll()");
		if (new KeyEvent((int) a[0], (int) a[1], 0).isSelectAll()) throw new Probe.Fail("A without Ctrl is isSelectAll()");
		if (new KeyEvent((int) a[0], (int) a[1], mod | InputConstants.MOD_SHIFT).isSelectAll()) throw new Probe.Fail("Ctrl+Shift+A is isSelectAll()");
		Object[] x = LegacyKeyCodes.keyEvent(67, 0, 0);
		if (!new KeyEvent((int) x[0], (int) x[1], mod).isCopy()) throw new Probe.Fail("Ctrl+C built by keyEvent(67) is not isCopy()");
		return "Ctrl+A (modifier " + mod + ") is isSelectAll(); A, Ctrl+Shift+A are not; Ctrl+C is isCopy()";
	}

	private static String isKeyDown(Probe.Ctx c) {
		if (LegacyKeyCodes.convert(340) != 225) throw new Probe.Fail("convert(340 GLFW_KEY_LEFT_SHIFT) = " + LegacyKeyCodes.convert(340) + ", want 225");
		// no key is held in a probe run, so both sides read false; the shim must still ask for the converted code and never throw for an unknown one
		for (int g : new int[] {340, 341, 65, 32, 314, -1}) {
			int sdl = LegacyKeyCodes.convert(g);
			boolean want = sdl >= 0 && InputConstants.isKeyDown(sdl);
			boolean got = LegacyKeyCodes.isKeyDown(c.mc.getWindow(), g);
			if (got != want) throw new Probe.Fail("isKeyDown(window, " + g + ") = " + got + ", the 26.3 key " + sdl + " reads " + want);
		}
		return "340 -> 225; shim agrees with InputConstants.isKeyDown on the converted code for 6 keys (parity only: none is held)";
	}
}
