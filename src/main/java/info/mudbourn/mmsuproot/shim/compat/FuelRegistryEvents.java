package info.mudbourn.mmsuproot.shim.compat;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

/**
 * Fabric's old fuel events. Nothing fires them in 26.3, so {@link #fire()} does, once the mods have initialised: the build
 * event, then the exclusions event, which in 1.21.11 ran after vanilla's own exclusion. The context offers the built-in
 * registries (the tags in them are not loaded yet) and the default feature flags, since no world exists to ask.
 */
public final class FuelRegistryEvents {
	/** Called when the fuel table is built, after vanilla's fuels and before exclusions. */
	public static final Event<BuildCallback> BUILD = EventFactory.createArrayBacked(BuildCallback.class, callbacks -> (builder, context) -> {
		for (BuildCallback callback : callbacks) {
			callback.build(builder, context);
		}
	});

	/** Called when the fuel table is built, after vanilla's exclusions. */
	public static final Event<ExclusionsCallback> EXCLUSIONS = EventFactory.createArrayBacked(ExclusionsCallback.class, callbacks -> (builder, context) -> {
		for (ExclusionsCallback callback : callbacks) {
			callback.buildExclusions(builder, context);
		}
	});

	private FuelRegistryEvents() { }

	public static void fire() {
		FuelValues.Builder builder = new FuelValues.Builder();
		Context context = new ContextImpl(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), FeatureFlags.DEFAULT_FLAGS, 200);
		BUILD.invoker().build(builder, context);
		EXCLUSIONS.invoker().buildExclusions(builder, context);
	}

	@FunctionalInterface
	public interface BuildCallback {
		void build(FuelValues.Builder builder, Context context);
	}

	@FunctionalInterface
	public interface ExclusionsCallback {
		void buildExclusions(FuelValues.Builder builder, Context context);
	}

	public interface Context {
		/** The smelting time of one item, 200 ticks for furnaces. */
		int baseSmeltTime();

		/** The registries the table is built from. */
		HolderLookup.Provider registries();

		/** The feature flags the table is built for. */
		FeatureFlagSet enabledFeatures();
	}

	private record ContextImpl(HolderLookup.Provider registries, FeatureFlagSet enabledFeatures, int baseSmeltTime) implements Context { }
}
