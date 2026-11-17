package info.mudbourn.mmsuproot.shim.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.minecraft.resources.Identifier;

/**
 * The 2.x call {@code Placeholders.register(Identifier, PlaceholderHandler)}, answered by Placeholder API 3.x ({@code registerCommon} with a
 * {@code Placeholder.Handler}). Reached through reflection, so Uproot needs no build dependency on Placeholder API and the class only
 * loads when a remapped mod calls it.
 */
public final class LegacyPlaceholders {
	private static final String API = "eu.pb4.placeholders.api.";

	private LegacyPlaceholders() { }

	/** Old {@code register}: a placeholder that answers for any context with a player or a level, which is what registerCommon is. */
	public static void register(Identifier id, LegacyPlaceholderHandler handler) {
		try {
			ClassLoader loader = LegacyPlaceholders.class.getClassLoader();
			Class<?> handlerType = Class.forName(API + "Placeholder$Handler", true, loader);
			Method register = Class.forName(API + "Placeholders", true, loader).getMethod("registerCommon", Identifier.class, handlerType);
			Object proxy = Proxy.newProxyInstance(loader, new Class<?>[] { handlerType }, (self, method, args) -> switch (method.getName()) {
				case "onPlaceholderRequest" -> handler.onPlaceholderRequest(args[0], args[1]);
				case "equals" -> self == args[0];
				case "hashCode" -> System.identityHashCode(self);
				case "toString" -> "uproot placeholder " + id;
				default -> throw new UnsupportedOperationException(method.getName());
			});
			register.invoke(null, id, proxy);
		} catch (InvocationTargetException e) {
			throw new IllegalStateException("cannot register placeholder " + id, e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot register placeholder " + id, e);
		}
	}
}
