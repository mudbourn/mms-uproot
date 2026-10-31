package info.mudbourn.mmsuproot.shim.recipe;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleItemRecipe;

/**
 * The serializer of a mod's single-input recipe class, built on 26.3's record, because the mod's own
 * serializer class implements what is a record now and cannot load. The recipe class keeps working: it is
 * built through its own (group, ingredient, result) constructor. Reads the same JSON the mod's codec did.
 */
public final class FurnishRecipes {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");
	private static final AtomicInteger BUILT = new AtomicInteger();
	private static final String RECIPE_CLASS = "io.github.wouink.furnish.recipe.FurnitureRecipe";

	/** What the mod's {@code SERIALIZER} field reads as; the remap moves the reads here. */
	public static final RecipeSerializer<? extends SingleItemRecipe> SERIALIZER = serializer();

	private FurnishRecipes() { }

	private static RecipeSerializer<SingleItemRecipe> serializer() {
		SingleItemRecipe.Factory<SingleItemRecipe> factory = (info, ingredient, result) -> create(ingredient, result);
		return new RecipeSerializer<>(SingleItemRecipe.simpleMapCodec(factory), SingleItemRecipe.simpleStreamCodec(factory));
	}

	private static SingleItemRecipe create(Ingredient ingredient, ItemStackTemplate result) {
		try {
			Class<?> type = Class.forName(RECIPE_CLASS, true, FurnishRecipes.class.getClassLoader());
			Constructor<?> ctor = type.getConstructor(String.class, Ingredient.class, ItemStack.class);
			LegacySingleItemRecipe.PENDING.set(result);
			SingleItemRecipe recipe = (SingleItemRecipe) ctor.newInstance("", ingredient, ItemStack.EMPTY);
			int n = BUILT.incrementAndGet();
			if (n % 100 == 0) LOGGER.info("Furnish workbench recipes built so far: {}", n);
			return recipe;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot build " + RECIPE_CLASS, e);
		} finally {
			LegacySingleItemRecipe.PENDING.remove();
		}
	}
}
