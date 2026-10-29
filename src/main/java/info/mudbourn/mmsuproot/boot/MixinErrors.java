package info.mudbourn.mmsuproot.boot;

import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Records mixin failures in the report and keeps the game going. */
public final class MixinErrors implements IMixinErrorHandler {
	@Override
	public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin, ErrorAction action) {
		record(mixin, "prepare failed", th);
		return ErrorAction.WARN;
	}

	@Override
	public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin, ErrorAction action) {
		record(mixin, "apply to " + targetClassName + " failed", th);
		return ErrorAction.WARN;
	}

	private static void record(IMixinInfo mixin, String what, Throwable th) {
		UprootBoot.recordMixinFailure(mixin.getConfig().getName(), mixin.getName(), what + ": " + th.getMessage());
	}
}
