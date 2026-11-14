package info.mudbourn.mmsuproot.boot;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Runs after Mixin is set up and before the game loads: from here on every injector reports whether it found anything. */
public final class UprootPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		DeadHooks.install();
	}
}
