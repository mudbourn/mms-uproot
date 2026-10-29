package info.mudbourn.mmsuproot.shim.feature;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Keys of the vanilla patch features that 26.3 removed. Mods name them in their JSON and Java; the
 * data shim defines them again under the same ids, so the key is all a mod's code needs.
 */
public final class LegacyKeys {
	public static final ResourceKey<Feature> PATCH_CACTUS = key("patch_cactus");
	public static final ResourceKey<Feature> PATCH_GRASS = key("patch_grass");
	public static final ResourceKey<Feature> PATCH_GRASS_JUNGLE = key("patch_grass_jungle");
	public static final ResourceKey<Feature> PATCH_PUMPKIN = key("patch_pumpkin");
	public static final ResourceKey<Feature> PATCH_WATERLILY = key("patch_waterlily");

	private LegacyKeys() { }

	private static ResourceKey<Feature> key(String name) {
		return ResourceKey.create(Registries.FEATURE, Identifier.withDefaultNamespace(name));
	}
}
