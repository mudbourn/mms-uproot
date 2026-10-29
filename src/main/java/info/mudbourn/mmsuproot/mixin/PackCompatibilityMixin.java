package info.mudbourn.mmsuproot.mixin;

import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mods built for older versions bundle resource and data packs whose {@code pack_format} is below
 * the current one. Vanilla marks those too old and will not enable them on its own. Treat them as
 * compatible; a pack that is too new stays too new.
 *
 * <p>Only packs a mod supplies are touched. Vanilla builds every pack it knows from one of five
 * sources (default for the user's pack folder, built-in, feature, world, server); a mod's packs
 * come from a source of its own, such as Fabric's resource loader. A user's own outdated pack keeps
 * vanilla's warning.
 */
@Mixin(Pack.class)
public abstract class PackCompatibilityMixin {
	@Inject(method = "readPackMetadata", at = @At("RETURN"), cancellable = true)
	private static void mms_uproot$acceptOlderModPacks(PackLocationInfo location, Pack.ResourcesSupplier resources, PackFormat current,
			PackType type, CallbackInfoReturnable<Pack.Metadata> cir) {
		Pack.Metadata metadata = cir.getReturnValue();
		if (metadata == null || metadata.compatibility() != PackCompatibility.TOO_OLD) return;

		PackSource source = location.source();
		if (source == PackSource.DEFAULT || source == PackSource.BUILT_IN || source == PackSource.FEATURE
				|| source == PackSource.WORLD || source == PackSource.SERVER) {
			return;
		}
		cir.setReturnValue(new Pack.Metadata(metadata.description(), PackCompatibility.COMPATIBLE, metadata.requestedFeatures(), metadata.overlays()));
	}
}
