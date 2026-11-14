package info.mudbourn.mmsuproot.shim.client;

import info.mudbourn.mmsuproot.shim.UprootIds;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Fabric's old HUD callback. 26.3 has HUD elements instead, so the event's first use adds one element after the others that
 * runs every listener.
 */
@FunctionalInterface
public interface HudRenderCallback {
	Event<HudRenderCallback> EVENT = create();

	void onHudRender(GuiGraphicsExtractor graphics, DeltaTracker tickCounter);

	private static Event<HudRenderCallback> create() {
		Event<HudRenderCallback> event = EventFactory.createArrayBacked(HudRenderCallback.class, callbacks -> (graphics, tickCounter) -> {
			for (HudRenderCallback callback : callbacks) {
				callback.onHudRender(graphics, tickCounter);
			}
		});
		HudElementRegistry.addLast(UprootIds.id("legacy_hud_render_callback"), (graphics, tickCounter) -> event.invoker().onHudRender(graphics, tickCounter));
		return event;
	}
}
