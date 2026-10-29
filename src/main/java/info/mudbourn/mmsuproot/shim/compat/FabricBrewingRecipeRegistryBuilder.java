package info.mudbourn.mmsuproot.shim.compat;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Fabric's old brewing event. Nothing fires it in 26.3, so {@link #fire()} does, once the mods have initialised. */
public final class FabricBrewingRecipeRegistryBuilder {
	public static final Event<BuildCallback> BUILD = EventFactory.createArrayBacked(BuildCallback.class, callbacks -> builder -> {
		for (BuildCallback callback : callbacks) {
			callback.build(builder);
		}
	});

	private FabricBrewingRecipeRegistryBuilder() { }

	public static void fire() {
		BUILD.invoker().build(new PotionBrewing.Builder());
	}

	@FunctionalInterface
	public interface BuildCallback {
		void build(PotionBrewing.Builder builder);
	}
}
