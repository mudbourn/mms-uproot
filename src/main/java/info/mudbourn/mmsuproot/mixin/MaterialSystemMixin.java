package info.mudbourn.mmsuproot.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.material.MaterialSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Surface building lets eroded badlands grow their pillars. Biomes We've Gone did the same for two
 * of its own biomes by a mixin on the 1.21.11 surface system, which cannot apply; this puts them
 * on the same check. A biome that is not installed never matches.
 */
@Mixin(MaterialSystem.class)
public abstract class MaterialSystemMixin {
	private static final ResourceKey<Biome> SHATTERED_GLACIER = biome("shattered_glacier");
	private static final ResourceKey<Biome> ERODED_BOREALIS = biome("eroded_borealis");

	private static ResourceKey<Biome> biome(String path) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("biomeswevegone", path));
	}

	@Redirect(method = "buildSurface", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/resources/ResourceKey;)Z"))
	private boolean mms_uproot$pillarBiomes(Holder<Biome> biome, ResourceKey<Biome> key) {
		return biome.is(key) || (key == Biomes.ERODED_BADLANDS && (biome.is(SHATTERED_GLACIER) || biome.is(ERODED_BOREALIS)));
	}
}
