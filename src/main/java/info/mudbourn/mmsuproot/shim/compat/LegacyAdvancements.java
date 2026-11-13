package info.mudbourn.mmsuproot.shim.compat;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Validatable;
import net.minecraft.world.level.storage.loot.ValidationContextSource;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Advancement criteria that a mod defines. 1.21.11 held an entity condition of a criterion as a {@code ContextAwarePredicate} and
 * validated it through a {@code CriterionValidator}; 26.3 holds a loot condition in a holder and validates through the loot
 * validation context. The renames send the old types to {@code Holder} and {@code ValidationContextSource}, and the calls here.
 */
public final class LegacyAdvancements {
	private LegacyAdvancements() { }

	/** {@code ContextAwarePredicate.matches(context)}. */
	public static boolean matches(Holder<LootItemCondition> predicate, LootContext context) {
		return predicate.value().test(context);
	}

	/** {@code CriterionValidator.validateEntities(predicates, name)}. */
	public static void validateEntities(ValidationContextSource source, List<Holder<LootItemCondition>> predicates, String name) {
		Validatable.validateHolder(source.entityContext(), name, predicates);
	}

	/** {@code CriterionValidator.validateEntity(predicate, name)}. */
	public static void validateEntity(ValidationContextSource source, Optional<Holder<LootItemCondition>> predicate, String name) {
		Validatable.validateHolder(source.entityContext(), name, predicate);
	}
}
