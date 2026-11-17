package info.mudbourn.mmsuproot.shim.compat.trinkets;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

import com.mojang.datafixers.util.Function3;

import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The part of the old Trinkets API ({@code dev.emi.trinkets.api.TrinketsApi}) that 1.21.11 mods call, answered
 * by the rewritten Trinkets 4.2 ({@code eu.pb4.trinkets}). Reached through reflection, so Uproot needs no build
 * dependency on Trinkets and these classes only load when a remapped mod calls them.
 */
public final class LegacyTrinketsApi {
	static final String API = "eu.pb4.trinkets.api.";

	private LegacyTrinketsApi() {
	}

	/** Old {@code getTrinketComponent}: the entity's trinket slots, empty when the entity has none. */
	public static Optional<LegacyTrinketComponent> getTrinketComponent(LivingEntity entity) {
		Object attachment = call(method("TrinketsApi", "getAttachment", LivingEntity.class), null, entity);
		return attachment == null ? Optional.empty() : Optional.of(LegacyTrinketComponent.of(attachment));
	}

	/**
	 * Old {@code registerTrinketPredicate}. Old predicates answered a TriState and Trinkets took the first answer
	 * that was not DEFAULT, falling back to false; the new ones answer a boolean, so only TRUE passes.
	 */
	public static void registerTrinketPredicate(Identifier id,
			Function3<ItemStack, LegacySlotReference, LivingEntity, TriState> predicate) {
		Class<?> type = type("TrinketsApi$TrinketPredicate");
		Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (self, m, args) -> {
			switch (m.getName()) {
			case "test":
				return predicate.apply((ItemStack) args[0], new LegacySlotReference(args[1]),
						(LivingEntity) args[2]) == TriState.TRUE;
			case "equals":
				return self == args[0];
			case "hashCode":
				return System.identityHashCode(self);
			case "toString":
				return "uproot trinket predicate " + id;
			default:
				throw new UnsupportedOperationException(m.getName());
			}
		});
		call(method("TrinketsApi", "registerTrinketPredicate", Identifier.class, type), null, id, proxy);
	}

	static Class<?> type(String name) {
		try {
			return Class.forName(API + name, true, LegacyTrinketsApi.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Trinkets 4.2 is not loaded: " + API + name, e);
		}
	}

	static Method method(String owner, String name, Class<?>... params) {
		try {
			return type(owner).getMethod(name, params);
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("Trinkets has no " + owner + "." + name, e);
		}
	}

	static Object call(Method method, Object target, Object... args) {
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException r) throw r;
			throw new IllegalStateException(e.getCause());
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}
}
