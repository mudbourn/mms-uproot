package info.mudbourn.mmsuproot.shim.compat;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;

/**
 * Fabric's game rule builder took a {@link CustomGameRuleCategory} through {@code category}; it only takes a vanilla category
 * now. The rules in renames.txt send those calls here, one method per builder type because a call site names the type it
 * holds the builder as. Each gives the builder the vanilla category the custom one stands for, and returns the builder.
 */
public final class LegacyGameRules {
	private LegacyGameRules() { }

	public static GameRuleBuilder<?> category(GameRuleBuilder<?> builder, CustomGameRuleCategory category) {
		builder.category(category.vanilla());
		return builder;
	}

	public static GameRuleBuilder.BooleanRuleBuilder category(GameRuleBuilder.BooleanRuleBuilder builder, CustomGameRuleCategory category) {
		return builder.category(category.vanilla());
	}

	public static GameRuleBuilder.IntegerRuleBuilder category(GameRuleBuilder.IntegerRuleBuilder builder, CustomGameRuleCategory category) {
		return builder.category(category.vanilla());
	}

	public static GameRuleBuilder.DoubleRuleBuilder category(GameRuleBuilder.DoubleRuleBuilder builder, CustomGameRuleCategory category) {
		return builder.category(category.vanilla());
	}

	public static <E extends Enum<E>> GameRuleBuilder.EnumRuleBuilder<E> category(GameRuleBuilder.EnumRuleBuilder<E> builder, CustomGameRuleCategory category) {
		return builder.category(category.vanilla());
	}
}
