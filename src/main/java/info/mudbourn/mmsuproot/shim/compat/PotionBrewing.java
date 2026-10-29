package info.mudbourn.mmsuproot.shim.compat;

import info.mudbourn.mmsuproot.data.gen.Brewing;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

/**
 * 1.21.11's brewing table. 26.3 brews from {@code minecraft:brewing} recipes, so the builder hands
 * each mix to {@link Brewing}, which writes the recipes.
 */
public final class PotionBrewing {
	private PotionBrewing() { }

	public static final class Builder {
		public void addMix(Holder<Potion> from, Item ingredient, Holder<Potion> to) {
			Brewing.addMix(from, ingredient, to);
		}

		public void addStartMix(Item ingredient, Holder<Potion> to) {
			Brewing.addStartMix(ingredient, to);
		}

		public void addContainerRecipe(Item from, Item ingredient, Item to) {
			Brewing.addContainerRecipe(from, ingredient, to);
		}
	}
}
