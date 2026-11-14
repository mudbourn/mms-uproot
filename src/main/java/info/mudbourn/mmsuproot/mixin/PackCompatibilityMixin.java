package info.mudbourn.mmsuproot.mixin;

import java.util.List;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
 *
 * <p>A data pack of an older game that carries overlay directories picks them for the data format of 1.21.11, the game its mods
 * were made for: 26.3 would pick the overlays that name its own format, and a pack from before it has none.
 */
@Mixin(Pack.class)
public abstract class PackCompatibilityMixin {
	/** The data format of 1.21.11, the newest game a mod bridged by Uproot was made for. */
	@Unique
	private static final PackFormat LEGACY_DATA = PackFormat.of(94, 1);

	/** Whether a pack comes from a mod: vanilla builds its own from the five sources of the user, the game and the world. */
	@Unique
	private static boolean fromMod(PackLocationInfo location) {
		PackSource source = location.source();
		return !(source == PackSource.DEFAULT || source == PackSource.BUILT_IN || source == PackSource.FEATURE
				|| source == PackSource.WORLD || source == PackSource.SERVER);
	}

	@WrapOperation(method = "readPackMetadata", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/packs/OverlayMetadataSection;overlaysForVersion(Lnet/minecraft/server/packs/metadata/pack/PackFormat;)Ljava/util/List;"))
	private static List<String> mms_uproot$legacyOverlays(OverlayMetadataSection section, PackFormat current, Operation<List<String>> original,
			@Local(argsOnly = true) PackLocationInfo location, @Local(argsOnly = true) PackType type, @Local PackMetadataSection pack) {
		boolean older = type == PackType.SERVER_DATA && fromMod(location) && pack.supportedFormats().maxInclusive().compareTo(current) < 0;
		return original.call(section, older ? LEGACY_DATA : current);
	}

	@Inject(method = "readPackMetadata", at = @At("RETURN"), cancellable = true)
	private static void mms_uproot$acceptOlderModPacks(PackLocationInfo location, Pack.ResourcesSupplier resources, PackFormat current,
			PackType type, CallbackInfoReturnable<Pack.Metadata> cir) {
		Pack.Metadata metadata = cir.getReturnValue();
		if (metadata == null || metadata.compatibility() != PackCompatibility.TOO_OLD) return;

		if (!fromMod(location)) return;
		cir.setReturnValue(new Pack.Metadata(metadata.description(), PackCompatibility.COMPATIBLE, metadata.requestedFeatures(), metadata.overlays()));
	}
}
