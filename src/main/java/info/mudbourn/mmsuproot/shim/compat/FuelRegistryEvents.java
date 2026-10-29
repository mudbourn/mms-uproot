package info.mudbourn.mmsuproot.shim.compat;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Fabric's old fuel event. Nothing fires it in 26.3, so {@link #fire()} does, once the mods have initialised. */
public final class FuelRegistryEvents {
	public static final Event<BuildCallback> BUILD = EventFactory.createArrayBacked(BuildCallback.class, callbacks -> (builder, context) -> {
		for (BuildCallback callback : callbacks) {
			callback.build(builder, context);
		}
	});

	private FuelRegistryEvents() { }

	public static void fire() {
		BUILD.invoker().build(new FuelValues.Builder(), new Context() { });
	}

	@FunctionalInterface
	public interface BuildCallback {
		void build(FuelValues.Builder builder, Context context);
	}

	public interface Context {
		/** The smelting time of one item, 200 ticks in every vanilla version. */
		default int baseSmeltTime() {
			return 200;
		}
	}
}
