package info.mudbourn.mmsuproot.mixin;

import java.util.stream.Stream;

import info.mudbourn.mmsuproot.data.DataShim;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every pack the game opens leaves through here, whoever made it: Fabric API for a mod's own pack, or a mod's code for a pack it
 * builds itself (a data pack kept inside its jar). Packs of the mods Uproot converts go through {@link DataShim}, which shows their
 * data in the 26.3 shapes.
 */
@Mixin(Pack.class)
public abstract class PackDataMixin {
	@Inject(method = "open", at = @At("RETURN"), cancellable = true)
	private void mms_uproot$convertData(CallbackInfoReturnable<Stream<PackResources>> cir) {
		cir.setReturnValue(cir.getReturnValue().map(DataShim::wrap));
	}
}
