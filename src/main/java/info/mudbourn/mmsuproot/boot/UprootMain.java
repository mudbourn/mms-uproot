package info.mudbourn.mmsuproot.boot;

import info.mudbourn.mmsuproot.data.gen.GenSetup;
import info.mudbourn.mmsuproot.shim.compat.LegacyWorld;
import info.mudbourn.mmsuproot.shim.surface.LegacyRule;
import net.fabricmc.api.ModInitializer;

/** Runs when mods initialise, which is after Mixin is done with the launcher proxy. */
public final class UprootMain implements ModInitializer {
	@Override
	public void onInitialize() {
		UprootBoot.release();
		GenSetup.register();
		LegacyRule.register();
		LegacyWorld.register();
	}
}
