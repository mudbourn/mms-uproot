package info.mudbourn.mmsuproot.shim.compat.trinkets;

import java.lang.reflect.Method;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Old {@code TrinketInventory}, over a Trinkets 4.2 one. Both are containers, so the container calls go straight to the
 * 4.2 inventory.
 */
public final class LegacyTrinketInventory implements Container {
	private static final Method SLOT_TYPE = LegacyTrinketsApi.method("TrinketInventory", "slotType");

	private final Object inventory;
	private final Container container;

	LegacyTrinketInventory(Object inventory) {
		this.inventory = inventory;
		this.container = (Container) inventory;
	}

	public LegacySlotType getSlotType() {
		return new LegacySlotType(LegacyTrinketsApi.call(SLOT_TYPE, inventory));
	}

	@Override
	public int getContainerSize() {
		return container.getContainerSize();
	}

	@Override
	public boolean isEmpty() {
		return container.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return container.getItem(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		return container.removeItem(slot, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return container.removeItemNoUpdate(slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		container.setItem(slot, stack);
	}

	@Override
	public void setChanged() {
		container.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return container.stillValid(player);
	}

	@Override
	public void clearContent() {
		container.clearContent();
	}
}
