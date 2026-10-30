package info.mudbourn.mmsuproot.shim.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * What a mod's single-input recipe class extends in place of {@link SingleItemRecipe}. 1.21.11's base took a
 * group and a result stack and answered {@code group()} and {@code recipeBookCategory()} itself; 26.3's takes a
 * notification flag and an item template and leaves both to the subclass. This keeps the old constructor and
 * those two answers (the stonecutter's book category), and the old stack-returning {@code result()}.
 */
public abstract class LegacySingleItemRecipe extends SingleItemRecipe {
	/**
	 * The template a recipe is being built with. A stack cannot be made while recipes decode (the item's components
	 * are not bound yet), so a serializer that builds a mod's recipe passes an empty stack and the template here.
	 */
	static final ThreadLocal<ItemStackTemplate> PENDING = new ThreadLocal<>();

	private final String group;

	protected LegacySingleItemRecipe(String group, Ingredient ingredient, ItemStack result) {
		super(new Recipe.CommonInfo(true), ingredient, template(result));
		this.group = group;
	}

	private static ItemStackTemplate template(ItemStack result) {
		ItemStackTemplate pending = PENDING.get();
		return pending != null ? pending : ItemStackTemplate.fromStack(result);
	}

	@Override
	public String group() {
		return group;
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return RecipeBookCategories.STONECUTTER;
	}

	/** The old {@code result()}: a stack. Assembling the template is the public way to one; the remap sends the old calls here. */
	public static ItemStack legacyResultOf(LegacySingleItemRecipe recipe) {
		return recipe.assemble(new SingleRecipeInput(ItemStack.EMPTY));
	}

	/** {@code new ItemStackSlotDisplay(stack)} as 26.3 takes it. */
	public static Object[] slotDisplayArgs(ItemStack stack) {
		return new Object[] {ItemStackTemplate.fromStack(stack)};
	}
}
