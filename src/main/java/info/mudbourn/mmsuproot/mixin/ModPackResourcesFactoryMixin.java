package info.mudbourn.mmsuproot.mixin;

import java.util.stream.Stream;

import info.mudbourn.mmsuproot.data.DataShim;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric API hands each mod's pack to the pack repository here. Packs of the mods Uproot rewrites
 * leave through {@link DataShim}, which shows their worldgen data in the 26.3 shapes.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.resource.pack.ModPackResourcesFactory", remap = false)
public abstract class ModPackResourcesFactoryMixin {
	@Inject(method = "openResources", at = @At("RETURN"), cancellable = true)
	private void mms_uproot$convertData(PackLocationInfo location, Pack.Metadata metadata, CallbackInfoReturnable<Stream<PackResources>> cir) {
		cir.setReturnValue(cir.getReturnValue().map(DataShim::wrap));
	}
}
