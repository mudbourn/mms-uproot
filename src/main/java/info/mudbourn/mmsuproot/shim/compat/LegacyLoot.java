package info.mudbourn.mmsuproot.shim.compat;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
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

	/**
	 * {@code SetComponentsFunction}'s constructor, which a mod reached through a mixin invoker: 26.3 made it private and gave it an optional
	 * condition holder where it took a list of conditions.
	 */
	private static final java.lang.invoke.MethodHandle SET_COMPONENTS = setComponentsConstructor();

	private static java.lang.invoke.MethodHandle setComponentsConstructor() {
		try {
			java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.privateLookupIn(SetComponentsFunction.class, java.lang.invoke.MethodHandles.lookup());
			return lookup.findConstructor(SetComponentsFunction.class, java.lang.invoke.MethodType.methodType(void.class, java.util.Optional.class, DataComponentPatch.class));
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	/** {@code SetComponentsFunctionAccessor.createSetComponentsLootFunction(conditions, components)}. */
	public static SetComponentsFunction setComponents(java.util.List<LootItemCondition> conditions, DataComponentPatch components) {
		if (!conditions.isEmpty()) {
			throw new UnsupportedOperationException("Uproot can only build a set_components function without conditions, got " + conditions);
		}
		try {
			return (SetComponentsFunction) SET_COMPONENTS.invoke(java.util.Optional.empty(), components);
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
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
