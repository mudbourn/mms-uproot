package info.mudbourn.mmsuproot.mixin;

import java.util.ArrayList;
import java.util.List;

import info.mudbourn.mmsuproot.shim.compat.LegacyContainerListener;
import info.mudbourn.mmsuproot.shim.compat.ListeningContainer;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11's {@code SimpleContainer} kept a list of listeners and told each of them when {@code setChanged} ran. 26.3 dropped both; a mod that
 * keeps its inventory in one, such as Guard Villagers, listens through this.
 */
@Mixin(SimpleContainer.class)
public abstract class SimpleContainerListenersMixin implements ListeningContainer {
	@Unique
	private List<LegacyContainerListener> mms_uproot$listeners;

	@Override
	public void mms_uproot$addListener(LegacyContainerListener listener) {
		if (mms_uproot$listeners == null) mms_uproot$listeners = new ArrayList<>();
		mms_uproot$listeners.add(listener);
	}

	@Override
	public void mms_uproot$removeListener(LegacyContainerListener listener) {
		if (mms_uproot$listeners != null) mms_uproot$listeners.remove(listener);
	}

	@Inject(method = "setChanged", at = @At("TAIL"))
	private void mms_uproot$tellListeners(CallbackInfo ci) {
		if (mms_uproot$listeners == null) return;
		for (LegacyContainerListener listener : mms_uproot$listeners) listener.containerChanged((SimpleContainer) (Object) this);
	}
}
