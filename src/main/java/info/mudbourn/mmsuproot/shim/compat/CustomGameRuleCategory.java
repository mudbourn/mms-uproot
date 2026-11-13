package info.mudbourn.mmsuproot.shim.compat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Fabric's old custom game rule category, an id and a display name. Fabric's game rule API no longer has it: vanilla registers
 * categories itself ({@link GameRuleCategory#register(Identifier)}) and takes the name from the language key
 * {@code gamerule.category.<namespace>.<path>}. A category made here is registered with vanilla once per id, and
 * {@code LegacyGameRuleCategoryMixin} shows the name the mod gave it. {@link LegacyGameRules} hands it to a builder.
 */
public final class CustomGameRuleCategory {
	private static final Map<Identifier, GameRuleCategory> REGISTERED = new HashMap<>();
	private static final Map<GameRuleCategory, CustomGameRuleCategory> BY_VANILLA = new HashMap<>();

	private final Identifier id;
	private final Component name;
	private final GameRuleCategory category;

	public CustomGameRuleCategory(Identifier id, Component name) {
		this.id = id;
		this.name = name;
		this.category = register(id);
		synchronized (CustomGameRuleCategory.class) {
			BY_VANILLA.putIfAbsent(category, this);
		}
	}

	public Identifier getId() {
		return id;
	}

	public Component getName() {
		return name;
	}

	/** The category of a rule, when it was built with one of these. */
	public static <T> Optional<CustomGameRuleCategory> getCategory(GameRule<T> rule) {
		synchronized (CustomGameRuleCategory.class) {
			return Optional.ofNullable(BY_VANILLA.get(rule.category()));
		}
	}

	/** The vanilla category this one stands for. */
	GameRuleCategory vanilla() {
		return category;
	}

	/** The name a mod gave to a vanilla category made here, or null for any other category. */
	public static Component nameOf(GameRuleCategory category) {
		synchronized (CustomGameRuleCategory.class) {
			CustomGameRuleCategory custom = BY_VANILLA.get(category);
			return custom == null ? null : custom.name;
		}
	}

	/** Vanilla refuses an id twice, so two objects with one id share a category. An id some other code took falls back to misc. */
	private static synchronized GameRuleCategory register(Identifier id) {
		GameRuleCategory known = REGISTERED.get(id);
		if (known == null) {
			try {
				known = GameRuleCategory.register(id);
			} catch (IllegalArgumentException alreadyTaken) {
				known = GameRuleCategory.MISC;
			}
			REGISTERED.put(id, known);
		}
		return known;
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof CustomGameRuleCategory c && id.equals(c.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}
}
