package info.mudbourn.mmsuproot.shim.compat.trinkets;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	/** Old {@code forEach}: each non-cosmetic slot with its stack, empty ones included. */
	void forEach(BiConsumer<LegacySlotReference, ItemStack> consumer);

	/** Old {@code getInventory}: group name to slot name to inventory. */
	Map<String, Map<String, LegacyTrinketInventory>> getInventory();

	default boolean isEquipped(Item item) {
		return isEquipped(stack -> stack.is(item));
	}

	default boolean isEquipped(Predicate<ItemStack> predicate) {
		return !getEquipped(predicate).isEmpty();
	}

	default List<LegacyTuple<LegacySlotReference, ItemStack>> getEquipped(Predicate<ItemStack> predicate) {
		List<LegacyTuple<LegacySlotReference, ItemStack>> found = new ArrayList<>();
		forEach((slot, stack) -> {
			if (predicate.test(stack)) found.add(new LegacyTuple<>(slot, stack));
		});
		return found;
	}

	default List<LegacyTuple<LegacySlotReference, ItemStack>> getEquipped(Item item) {
		return getEquipped(stack -> stack.is(item));
	}

	default List<LegacyTuple<LegacySlotReference, ItemStack>> getAllEquipped() {
		return getEquipped(stack -> !stack.isEmpty());
	}

	static LegacyTrinketComponent of(Object attachment) {
		return new Impl(attachment);
	}

	final class Impl implements LegacyTrinketComponent {
	private static final Method FOR_EACH = LegacyTrinketsApi.method("TrinketAttachment", "forEach", BiConsumer.class);
	private static final Method COSMETIC = LegacyTrinketsApi.method("TrinketSlotAccess", "cosmetic");

		private static final Method GET_INVENTORY = LegacyTrinketsApi.method("TrinketAttachment", "getInventory");

		private final Object attachment;

		Impl(Object attachment) {
			this.attachment = attachment;
		}

		@Override
		@SuppressWarnings("unchecked")
		public Map<String, Map<String, LegacyTrinketInventory>> getInventory() {
			Map<String, Map<String, LegacyTrinketInventory>> groups = new LinkedHashMap<>();
			((Map<String, Map<String, Object>>) LegacyTrinketsApi.call(GET_INVENTORY, attachment)).forEach((group, slots) -> {
				Map<String, LegacyTrinketInventory> wrapped = new LinkedHashMap<>();
				slots.forEach((slot, inventory) -> wrapped.put(slot, new LegacyTrinketInventory(inventory)));
				groups.put(group, wrapped);
			});
			return groups;
		}

		@Override
		public void forEach(BiConsumer<LegacySlotReference, ItemStack> consumer) {
			BiConsumer<Object, ItemStack> each = (access, stack) -> {
				if (Boolean.TRUE.equals(LegacyTrinketsApi.call(COSMETIC, access))) return;
				consumer.accept(new LegacySlotReference(access), stack);
			};
			LegacyTrinketsApi.call(FOR_EACH, attachment, each);
		}
	}
}
