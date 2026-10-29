package info.mudbourn.mmsuproot.data.gen;

import info.mudbourn.mmsuproot.shim.compat.FabricBrewingRecipeRegistryBuilder;
import info.mudbourn.mmsuproot.shim.compat.FuelRegistryEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;

/**
 * Hooks the parts of the generated data that are components on items rather than files. Called once
 * at init; the recorders are filled by the mods' own init, which runs before the event fires.
 */
public final class GenSetup {
	private GenSetup() { }

	private static boolean fired;

	/**
	 * Fabric's old fuel and brewing events had a caller in 1.21.11 and have none now; they fire once,
	 * the first time the generated data or components are needed, after every mod has registered.
	 */
	public static synchronized void fireLegacyEvents() {
		if (fired) return;
		fired = true;
		FuelRegistryEvents.fire();
		FabricBrewingRecipeRegistryBuilder.fire();
	}

	public static void register() {
		DefaultItemComponentEvents.MODIFY.register(context -> {
			fireLegacyEvents();
			Fuel.apply(context);
			Compost.apply(context);
		});
	}
}
