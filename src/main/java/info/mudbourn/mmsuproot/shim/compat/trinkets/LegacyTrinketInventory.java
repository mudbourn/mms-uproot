package info.mudbourn.mmsuproot.shim.compat.trinkets;

import java.lang.reflect.Method;

/** Old {@code TrinketInventory}, over a Trinkets 4.2 one; only what 1.21.11 mods read. */
public final class LegacyTrinketInventory {
	private static final Method SLOT_TYPE = LegacyTrinketsApi.method("TrinketInventory", "slotType");

	private final Object inventory;

	LegacyTrinketInventory(Object inventory) {
		this.inventory = inventory;
	}

	public LegacySlotType getSlotType() {
		return new LegacySlotType(LegacyTrinketsApi.call(SLOT_TYPE, inventory));
	}
}
