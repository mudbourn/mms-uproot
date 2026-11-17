package info.mudbourn.mmsuproot.shim.compat.trinkets;

import java.lang.reflect.Method;

/** Old {@code SlotReference}, over a Trinkets 4.2 {@code TrinketSlotAccess}. */
public final class LegacySlotReference {
	private static final Method INVENTORY = LegacyTrinketsApi.method("TrinketSlotAccess", "inventory");
	private static final Method INDEX = LegacyTrinketsApi.method("TrinketSlotAccess", "index");

	private final Object access;

	LegacySlotReference(Object access) {
		this.access = access;
	}

	public LegacyTrinketInventory inventory() {
		return new LegacyTrinketInventory(LegacyTrinketsApi.call(INVENTORY, access));
	}

	public int index() {
		return (Integer) LegacyTrinketsApi.call(INDEX, access);
	}
}
