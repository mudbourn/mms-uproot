package info.mudbourn.mmsuproot.shim.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/**
 * Prometheus gives each burning entity the flame sprites of its fire type. Its 1.21.11 hook replaced the two sprites a flame renderer asked for once per
 * entity; 26.3's {@code FlameFeatureRenderer} asks once per batch of entities and hands both sprites on to each entity's drawing, so the sprites are
 * swapped there (see FlameFeatureRendererFireMixin). Prometheus is reached through reflection (Uproot has no build dependency on it); the render
 * state of an entity carries its fire type through Prometheus's own {@code FireTyped} interface, which its other mixins add.
 */
public final class PrometheusFire {
	private static final ClassLoader LOADER = PrometheusFire.class.getClassLoader();
	private static volatile Lookup lookup;
	private static volatile boolean absent;

	private record Lookup(Class<?> fireTyped, MethodHandle fireType, MethodHandle registered, MethodHandle sprite0, MethodHandle sprite1) { }

	private PrometheusFire() { }

	/** The sprite for this entity: its fire type's {@code index}th flame sprite when the type is one Prometheus registered, else {@code vanilla}. */
	public static TextureAtlasSprite sprite(EntityRenderState state, TextureAtlasSprite vanilla, int index) {
		if (absent) return vanilla;
		try {
			Lookup found = lookup;
			if (found == null) lookup = found = find();
			if (!found.fireTyped().isInstance(state)) return vanilla;
			Identifier type = (Identifier) found.fireType().invoke(state);
			if (type == null || !(boolean) found.registered().invoke(type)) return vanilla;
			return (TextureAtlasSprite) (index == 0 ? found.sprite0() : found.sprite1()).invoke(type);
		} catch (ClassNotFoundException | NoClassDefFoundError | NoSuchMethodException e) {
			absent = true; // Prometheus is not there
			return vanilla;
		} catch (Throwable e) {
			throw new IllegalStateException("cannot ask Prometheus for a fire sprite", e);
		}
	}

	private static synchronized Lookup find() throws ReflectiveOperationException {
		if (lookup != null) return lookup;
		MethodHandles.Lookup l = MethodHandles.publicLookup();
		Class<?> typed = Class.forName("it.crystalnest.prometheus.api.type.FireTyped", true, LOADER);
		Class<?> manager = Class.forName("it.crystalnest.prometheus.api.FireManager", true, LOADER);
		Class<?> client = Class.forName("it.crystalnest.prometheus.api.client.FireClientManager", true, LOADER);
		return new Lookup(typed,
				l.findVirtual(typed, "getFireType", MethodType.methodType(Identifier.class)),
				l.findStatic(manager, "isRegisteredType", MethodType.methodType(boolean.class, Identifier.class)),
				l.findStatic(client, "getSprite0", MethodType.methodType(TextureAtlasSprite.class, Identifier.class)),
				l.findStatic(client, "getSprite1", MethodType.methodType(TextureAtlasSprite.class, Identifier.class)));
	}
}
