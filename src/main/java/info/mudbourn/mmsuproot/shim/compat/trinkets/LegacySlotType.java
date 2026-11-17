package info.mudbourn.mmsuproot.shim.compat.trinkets;

import java.lang.reflect.Method;

/** Old {@code SlotType}, over a Trinkets 4.2 one: the slot's name and its group, such as "cosmetic" in "head". */
public final class LegacySlotType {
	private static final Method NAME = LegacyTrinketsApi.method("SlotType", "name");
	private static final Method GROUP = LegacyTrinketsApi.method("SlotType", "group");

	private final Object type;

	LegacySlotType(Object type) {
		this.type = type;
	}

	public String getName() {
		return (String) LegacyTrinketsApi.call(NAME, type);
	}

	public String getGroup() {
		return (String) LegacyTrinketsApi.call(GROUP, type);
	}
}
