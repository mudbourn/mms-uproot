package info.mudbourn.mmsuproot.shim.compat.trinkets;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import info.mudbourn.mmsuproot.shim.compat.LegacyTuple;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Old {@code TrinketComponent}, over a Trinkets 4.2 {@code TrinketAttachment}. An interface, as the old one was, since
 * mods call it with invokeinterface.
 */
public interface LegacyTrinketComponent {
	boolean isEquipped(Item item);

	List<LegacyTuple<LegacySlotReference, ItemStack>> getEquipped(Predicate<ItemStack> predicate);

	static LegacyTrinketComponent of(Object attachment) {
		return new Impl(attachment);
	}

	final class Impl implements LegacyTrinketComponent {
	private static final Method FOR_EACH = LegacyTrinketsApi.method("TrinketAttachment", "forEach", BiConsumer.class);
	private static final Method COSMETIC = LegacyTrinketsApi.method("TrinketSlotAccess", "cosmetic");

	private static final Method IS_EQUIPPED = LegacyTrinketsApi.method("TrinketAttachment", "isEquipped", Item.class);

	private final Object attachment;

	/** Old {@code isEquipped(Item)}: whether any trinket slot holds the item. */
	public boolean isEquipped(Item item) {
		return (Boolean) LegacyTrinketsApi.call(IS_EQUIPPED, attachment, item);
	}

	Impl(Object attachment) {
		this.attachment = attachment;
	}

	/**
	 * Old {@code getEquipped}: each slot whose stack passes, paired with its slot. Old Trinkets had no cosmetic
	 * copies of slots, so 4.2's cosmetic accesses are left out.
	 */
	public List<LegacyTuple<LegacySlotReference, ItemStack>> getEquipped(Predicate<ItemStack> predicate) {
		List<LegacyTuple<LegacySlotReference, ItemStack>> found = new ArrayList<>();
		BiConsumer<Object, ItemStack> each = (access, stack) -> {
			if (Boolean.TRUE.equals(LegacyTrinketsApi.call(COSMETIC, access))) return;
			if (predicate.test(stack)) found.add(new LegacyTuple<>(new LegacySlotReference(access), stack));
		};
		LegacyTrinketsApi.call(FOR_EACH, attachment, each);
		return found;
	}
}
}
