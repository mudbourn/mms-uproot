package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.world.item.crafting.RecipeSerializer;

/** Recipe calls whose subject no longer exists in 26.3. */
public final class LegacyRecipes {
	private LegacyRecipes() { }

	/**
	 * Fabric's recipe sync takes the serializer a mod registered. A mod's own serializer class cannot load on 26.3
	 * (RecipeSerializer is a record now), so it is never registered and there is nothing to sync.
	 */
	public static void synchronizeRecipeSerializer(RecipeSerializer<?> serializer) { }
}
