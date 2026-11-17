package info.mudbourn.mmsuproot.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Decoder;
import info.mudbourn.mmsuproot.data.LenientData;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Tells {@link LenientData} which pack each element that failed to parse came from; the error map of the load keeps only the key. */
@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration")
public abstract class PendingRegistrationMixin {
	@ModifyReturnValue(method = "loadFromResource", at = @At("RETURN"))
	private static <T> Either<T, Exception> mms_uproot$noteFailedElement(Either<T, Exception> result, Decoder<T> decoder, RegistryOps<?> ops, ResourceKey<T> key, Resource resource) {
		if (result.right().isPresent()) LenientData.failed(key, resource.sourcePackId());
		return result;
	}
}
