package info.mudbourn.mmsuproot.mixin;

import java.util.function.Consumer;

import info.mudbourn.mmsuproot.data.gen.GenPack;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API offers every mod pack to the pack repository from here, and has no call to add a pack
 * that lives in memory. The data pack repository gets Uproot's generated pack after the mod packs.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator", remap = false)
public abstract class GenPackMixin {
	@Shadow @Final private PackType type;
	@Shadow @Final private boolean forKnownPacksManager;

	@Inject(method = "loadPacks", at = @At("RETURN"))
	private void mms_uproot$offerGenerated(Consumer<Pack> consumer, CallbackInfo ci) {
		if (type == PackType.SERVER_DATA && !forKnownPacksManager) consumer.accept(GenPack.create());
	}
}
