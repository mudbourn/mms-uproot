package info.mudbourn.mmsuproot.shim.surface;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;

/**
 * Stand-in for TerraBlender's 1.21.11 {@code SurfaceRuleManager}, which 26.3 renamed to
 * {@code MaterialRuleManager} and which takes a builder from the registries to a material rule.
 * TerraBlender is reached by reflection because it is not on Uproot's compile path and is not
 * always installed. The builder converts the mod's rule once the biome registry exists.
 */
public final class SurfaceRuleManager {
	private static final String MANAGER = "terrablender.api.MaterialRuleManager";

	/** Same constants as TerraBlender's category; looked up by name on the real one. */
	public enum RuleCategory { OVERWORLD, NETHER, END }

	private SurfaceRuleManager() { }

	public static void addSurfaceRules(RuleCategory category, String modId, SurfaceRules.RuleSource rules) {
		try {
			Class<?> manager = Class.forName(MANAGER);
			Class<?> categoryClass = Class.forName(MANAGER + "$RuleCategory");
			Class<?> builderClass = Class.forName(MANAGER + "$RuleBuilder");
			InvocationHandler handler = (proxy, method, args) -> invoke(method, args, rules);
			Object builder = Proxy.newProxyInstance(builderClass.getClassLoader(), new Class<?>[] {builderClass}, handler);
			@SuppressWarnings({"unchecked", "rawtypes"})
			Object real = Enum.valueOf((Class<Enum>) categoryClass, category.name());
			manager.getMethod("addRules", categoryClass, String.class, builderClass).invoke(null, real, modId, builder);
		} catch (InvocationTargetException e) {
			throw new IllegalStateException("TerraBlender refused the surface rules of " + modId, e.getCause());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("TerraBlender's material rule manager is not usable", e);
		}
	}

	private static Object invoke(Method method, Object[] args, SurfaceRules.RuleSource rules) {
		if (method.getName().equals("apply") && args != null && args.length == 1 && args[0] instanceof RegistryAccess access) {
			MaterialRule rule = SurfaceRules.toMaterial(rules, access.lookupOrThrow(Registries.BIOME));
			return rule;
		}
		if (method.getName().equals("toString")) return "mms-uproot converted surface rules";
		if (method.getName().equals("hashCode")) return System.identityHashCode(rules);
		if (method.getName().equals("equals")) return args[0] == rules;
		throw new UnsupportedOperationException(method.toString());
	}
}
