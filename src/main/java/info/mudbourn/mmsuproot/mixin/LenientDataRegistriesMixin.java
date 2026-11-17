package info.mudbourn.mmsuproot.mixin;

import java.util.Map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import info.mudbourn.mmsuproot.data.LenientData;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.3 loads loot tables, advancements, predicates and item modifiers through {@code RegistryDataLoader}, which fails the whole server start
 * when any one of them does not parse. 1.21.11 read them through {@code ReloadableServerRegistries}, which has no such path: a file that did not
 * parse cost only itself. The packs Uproot converts (mods built for 1.21.11, data packs of a world written for an older format) carry files that
 * never parsed, so for elements read from those packs the error is logged, with the pack, and the element is left out, as before.
 * Elements of any other pack, and errors of a registry as a whole, keep the game's behaviour (see {@link LenientData}).
 * <p>
 * The target is the synthetic lambda of {@code load}, the only place the error map is checked. If a game update moves it, the injector finds
 * nothing and, with {@code require = 1}, the mixin fails the start instead of the leniency silently going away.
 */
@Mixin(RegistryDataLoader.class)
public abstract class LenientDataRegistriesMixin {
	@WrapOperation(method = "lambda$load$2", at = @At(value = "INVOKE", target = "Ljava/util/Map;isEmpty()Z"), require = 1)
	private static boolean mms_uproot$skipUnreadableData(Map<ResourceKey<?>, Exception> errors, Operation<Boolean> original) {
		synchronized (errors) {
			errors.entrySet().removeIf(e -> LenientData.skip(e.getKey(), e.getValue()));
			return original.call(errors);
		}
	}
}
