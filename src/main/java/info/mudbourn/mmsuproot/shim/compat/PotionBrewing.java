package info.mudbourn.mmsuproot.shim.compat;

import info.mudbourn.mmsuproot.data.gen.Brewing;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 1.21.11's brewing table. 26.3 brews from {@code minecraft:brewing} recipes, so the builder hands
 * each mix to {@link Brewing}, which writes the recipes.
 */
public final class PotionBrewing {
	private static final FeatureFlagSet ENABLED_FEATURES = FeatureFlags.DEFAULT_FLAGS;

	private PotionBrewing() { }

	public static final class Builder {
		public void addMix(Holder<Potion> from, Item ingredient, Holder<Potion> to) {
			Brewing.addMix(from, ingredient, to);
		}

		public void addStartMix(Item ingredient, Holder<Potion> to) {
			Brewing.addStartMix(ingredient, to);
		}

		public void addContainer(Item container) {
			Brewing.addContainer(container);
		}

		public void addContainerRecipe(Item from, Item ingredient, Item to) {
			Brewing.addContainerRecipe(from, ingredient, to);
		}

		/**
		 * Fabric's {@code registerItemRecipe}: a bottle type that turns into another with the ingredient. The data names each item, so
		 * an ingredient is written once for every item it accepts, which a tag cannot do before the tags are loaded.
		 */
		public void registerItemRecipe(Item input, Ingredient ingredient, Item output) {
			if (input.isEnabled(ENABLED_FEATURES) && output.isEnabled(ENABLED_FEATURES)) {
				items(ingredient).forEach(item -> Brewing.addContainerRecipe(input, item, output));
			}
		}

		/** Fabric's {@code registerPotionRecipe}: one potion into another with the ingredient. */
		public void registerPotionRecipe(Holder<Potion> input, Ingredient ingredient, Holder<Potion> output) {
			if (input.value().isEnabled(ENABLED_FEATURES) && output.value().isEnabled(ENABLED_FEATURES)) {
				items(ingredient).forEach(item -> Brewing.addMix(input, item, output));
			}
		}

		/** Fabric's {@code registerRecipes}: water into mundane, and awkward into the potion, with the ingredient. */
		public void registerRecipes(Ingredient ingredient, Holder<Potion> potion) {
			if (potion.value().isEnabled(ENABLED_FEATURES)) {
				this.registerPotionRecipe(Potions.WATER, ingredient, Potions.MUNDANE);
				this.registerPotionRecipe(Potions.AWKWARD, ingredient, potion);
			}
		}

		/** Fabric's {@code getEnabledFeatures}: the flags the game runs with by default, since the data is written before any world's flags are known. */
		public FeatureFlagSet getEnabledFeatures() {
			return ENABLED_FEATURES;
		}

		private static List<Item> items(Ingredient ingredient) {
			return ingredient.items().map(Holder::value).toList();
		}
	}
}
