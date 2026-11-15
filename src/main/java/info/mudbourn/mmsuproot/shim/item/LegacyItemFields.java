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

	/** {@code ItemStack.STRICT_CODEC}: the stack codec that also rejects a stack its components or size do not allow, as {@code validateStrict} does. */
	public static final Codec<ItemStack> STRICT_CODEC = ItemStack.CODEC.validate(ItemStack::validateStrict);
}
