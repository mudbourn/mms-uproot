package info.mudbourn.mmsuproot.disable;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Stands in for {@code Registry.register} in mod code when a {@code skip} rule exists. A skipped
 * id is handed straight back, never registered: the mod's fields still hold a usable object, but
 * nothing in the game can find it, and saved data naming the id is treated as unknown.
 */
public final class RegistryGate {
	private RegistryGate() { }

	static boolean handles(String desc) {
		return desc.equals("(Lnet/minecraft/core/Registry;Lnet/minecraft/resources/Identifier;Ljava/lang/Object;)Ljava/lang/Object;")
				|| desc.equals("(Lnet/minecraft/core/Registry;Lnet/minecraft/resources/ResourceKey;Ljava/lang/Object;)Ljava/lang/Object;")
				|| desc.equals("(Lnet/minecraft/core/Registry;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;");
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Object register(Registry registry, Identifier id, Object value) {
		if (Disabler.skips(registry.key().identifier().getPath(), id.toString())) return value;
		return Registry.register(registry, id, value);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Object register(Registry registry, ResourceKey key, Object value) {
		if (Disabler.skips(registry.key().identifier().getPath(), key.identifier().toString())) return value;
		return Registry.register(registry, key, value);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Object register(Registry registry, String name, Object value) {
		if (Disabler.skips(registry.key().identifier().getPath(), "minecraft:" + name)) return value;
		return Registry.register(registry, name, value);
	}
}
