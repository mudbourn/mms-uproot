package info.mudbourn.mmsuproot.shim.compat;

import java.util.Locale;
import java.util.stream.Stream;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Item, stack and text-format calls whose 1.21.11 answer 26.3 gives by other means. */
public final class LegacyStacks {
	private LegacyStacks() { }

	/** {@code ItemStack.getTags()}: the tags of the item's holder. */
	public static Stream<TagKey<Item>> getTags(ItemStack stack) {
		return stack.typeHolder().tags();
	}

	/** {@code Item.getName()}: the item's name component, empty when the item has none. */
	public static Component getName(Item item) {
		return item.components().getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY);
	}

	/** {@code ChatFormatting.getName()}: the constant's name in lower case. */
	public static String formattingName(ChatFormatting format) {
		return format.name().toLowerCase(Locale.ROOT);
	}

	/** {@code ChatFormatting.isColor()}: neither one of the style formats nor reset; those are the ones with a text colour. */
	public static boolean formattingIsColor(ChatFormatting format) {
		return TextColor.fromLegacyFormat(format) != null;
	}
}
