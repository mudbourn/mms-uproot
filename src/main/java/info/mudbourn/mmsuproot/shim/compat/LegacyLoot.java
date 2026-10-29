package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;

/** Loot calls whose argument type disappeared. */
public final class LegacyLoot {
	private LegacyLoot() { }

	/**
	 * {@code LootPool.Builder.setRolls(NumberProvider)}: 26.3 takes a holder of an int provider. Only
	 * constant rolls convert; anything else would have to be rolled by a provider that no longer exists.
	 */
	public static LootPool.Builder setRolls(LootPool.Builder builder, LegacyNumberProvider rolls) {
		if (!(rolls instanceof LegacyConstantValue)) {
			throw new UnsupportedOperationException("Uproot can only convert constant loot pool rolls, got " + rolls);
		}
		return builder.setRolls(Holder.direct(new ConstantValue(Math.round(rolls.value()))));
	}
}
