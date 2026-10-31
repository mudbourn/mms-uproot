package info.mudbourn.mmsuproot.shim.client;

import java.lang.reflect.Method;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container screens of 1.21.11 drew their panel in {@code renderBg(graphics, partialTick, mouseX, mouseY)}, which the
 * screen called itself. 26.3's {@code AbstractContainerScreen} has no such method; its subclasses draw the panel in
 * {@code extractBackground}. A mod's screen still declares {@code renderBg}, so the background mixin calls it here,
 * at the point of the background, and the mod's drawing runs unchanged.
 */
public final class LegacyScreens {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");
	private static final ClassValue<Optional<Method>> RENDER_BG = new ClassValue<>() {
		@Override
		protected Optional<Method> computeValue(Class<?> type) {
			for (Class<?> c = type; c != null && c != AbstractContainerScreen.class; c = c.getSuperclass()) {
				try {
					Method m = c.getDeclaredMethod("renderBg", GuiGraphicsExtractor.class, float.class, int.class, int.class);
					m.setAccessible(true);
					return Optional.of(m);
				} catch (NoSuchMethodException e) {
					// not declared here; the superclass may
				} catch (RuntimeException e) {
					LOGGER.warn("Cannot reach renderBg of {}: {}", c.getName(), e.toString());
				}
			}
			return Optional.empty();
		}
	};

	private LegacyScreens() { }

	/** Draws the panel of a screen that still declares the old {@code renderBg}; does nothing for any other. */
	public static void renderBg(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
		Optional<Method> method = RENDER_BG.get(screen.getClass());
		if (method.isEmpty()) return;
		try {
			method.get().invoke(screen, graphics, partialTick, mouseX, mouseY);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("renderBg of " + screen.getClass().getName() + " failed", e);
		}
	}
}
