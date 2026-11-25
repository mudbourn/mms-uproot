package info.mudbourn.mmsuproot.shim.item;

import com.mojang.serialization.Codec;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Item constants 26.3 moved into per-colour collections, and the stack codec it renamed. */
public final class LegacyItemFields {
	private LegacyItemFields() { }

	public static final Item GRAY_STAINED_GLASS_PANE = Items.STAINED_GLASS_PANE.gray();
	public static final Item WHITE_BANNER = Items.BANNER.white();

	// the wool items 26.3 holds as a colour collection
	public static final Item BLACK_WOOL = Items.WOOL.black();
	public static final Item BLUE_WOOL = Items.WOOL.blue();
	public static final Item BROWN_WOOL = Items.WOOL.brown();
	public static final Item CYAN_WOOL = Items.WOOL.cyan();
	public static final Item GRAY_WOOL = Items.WOOL.gray();
	public static final Item GREEN_WOOL = Items.WOOL.green();
	public static final Item LIGHT_BLUE_WOOL = Items.WOOL.lightBlue();
	public static final Item LIGHT_GRAY_WOOL = Items.WOOL.lightGray();
	public static final Item LIME_WOOL = Items.WOOL.lime();
	public static final Item MAGENTA_WOOL = Items.WOOL.magenta();
	public static final Item ORANGE_WOOL = Items.WOOL.orange();
	public static final Item PINK_WOOL = Items.WOOL.pink();
	public static final Item PURPLE_WOOL = Items.WOOL.purple();
	public static final Item RED_WOOL = Items.WOOL.red();
	public static final Item WHITE_WOOL = Items.WOOL.white();
	public static final Item YELLOW_WOOL = Items.WOOL.yellow();

	/** {@code ItemStack.STRICT_CODEC}: the stack codec that also rejects a stack its components or size do not allow, as {@code validateStrict} does. */
	public static final Codec<ItemStack> STRICT_CODEC = ItemStack.CODEC.validate(ItemStack::validateStrict);
}
