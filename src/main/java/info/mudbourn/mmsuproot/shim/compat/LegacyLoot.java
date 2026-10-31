package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.UniformGenerator;

/** Loot calls whose argument type disappeared. */
public final class LegacyLoot {
	private LegacyLoot() { }

	/**
	 * {@code LootPool.Builder.setRolls(NumberProvider)}: 26.3 takes a holder of an int provider. Constant and
	 * uniform counts convert; anything else would have to be rolled by a provider that no longer exists.
	 */
	public static LootPool.Builder setRolls(LootPool.Builder builder, LegacyNumberProvider rolls) {
		return builder.setRolls(count(rolls));
	}

	/** {@code SetItemCountFunction.setCount(NumberProvider)}, likewise. */
	public static LootItemConditionalFunction.Builder<?> setCount(LegacyNumberProvider count) {
		return SetItemCountFunction.setCount(count(count));
	}

	/** {@code LootPool.Builder.apply(LootItemFunction.Builder)}: the pool takes the built function in a holder now. */
	public static LootPool.Builder apply(LootPool.Builder builder, LootItemFunction.Builder function) {
		return builder.apply(Holder.direct(function.build()));
	}

	private static Holder<ContextIntProvider> count(LegacyNumberProvider provider) {
		if (provider instanceof LegacyConstantValue) {
			return Holder.direct(new ConstantValue(Math.round(provider.value())));
		}
		if (provider instanceof LegacyUniformGenerator uniform) {
			return Holder.direct(new UniformGenerator(
				Holder.direct(new ConstantValue(Math.round(uniform.min()))),
				Holder.direct(new ConstantValue(Math.round(uniform.max())))));
		}
		throw new UnsupportedOperationException("Uproot can only convert constant and uniform loot counts, got " + provider);
	}
}
