package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Recipe calls whose subject no longer exists in 26.3. */
public final class LegacyRecipes {
	private LegacyRecipes() { }

	/**
	 * Fabric's recipe sync takes the serializer a mod registered. A mod's own serializer class cannot load on 26.3
	 * (RecipeSerializer is a record now), so it is never registered and there is nothing to sync.
	 */
	public static void synchronizeRecipeSerializer(RecipeSerializer<?> serializer) { }

	/** {@code Recipe.assemble(input, registries)}: 26.3's recipes build their result from the input alone. */
	@SuppressWarnings("unchecked")
	public static ItemStack assemble(Recipe<?> recipe, RecipeInput input, HolderLookup.Provider registries) {
		return ((Recipe<RecipeInput>) recipe).assemble(input);
	}
}
