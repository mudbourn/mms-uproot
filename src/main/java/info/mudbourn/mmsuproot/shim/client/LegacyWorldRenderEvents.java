package info.mudbourn.mmsuproot.shim.client;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Fabric's old world render events. 26.3's Fabric API splits level rendering into extraction and render phases
 * (LevelExtractionEvents, LevelRenderEvents) with different contexts, so these keep registration working but never fire:
 * what a 1.21.11 mod draws through them is not drawn.
 */
public final class LegacyWorldRenderEvents {
	public static final Event<StartMain> START_MAIN = EventFactory.createArrayBacked(StartMain.class,
			listeners -> context -> {
				for (StartMain listener : listeners) listener.startMain(context);
			});
	public static final Event<AfterEntities> AFTER_ENTITIES = EventFactory.createArrayBacked(AfterEntities.class,
			listeners -> context -> {
				for (AfterEntities listener : listeners) listener.afterEntities(context);
			});

	private LegacyWorldRenderEvents() {
	}

	@FunctionalInterface
	public interface StartMain {
		void startMain(LegacyWorldTerrainRenderContext context);
	}

	@FunctionalInterface
	public interface AfterEntities {
		void afterEntities(LegacyWorldRenderContext context);
	}
}
