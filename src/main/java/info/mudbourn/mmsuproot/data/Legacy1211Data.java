package info.mudbourn.mmsuproot.data;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import info.mudbourn.mmsuproot.util.Json;

/**
 * The front stage for the data and assets of a mod built for 1.21.1. The converters of this package start from what 1.21.11 wrote; this
 * puts what changed between 1.21.1 and 1.21.11 right first, so that they can carry on:
 * <ul>
 * <li>recipes and advancements: the recipe ingredient objects ({@code {"item": ...}}, {@code {"tag": ...}}) and the recipe triggers are already
 *     handled by {@link FeatureJson}, which reads those shapes too; what is added here is Spell Engine's item model component, which 1.21.1
 *     kept under its own name and 26.3 has as the game's {@code minecraft:item_model};</li>
 * <li>item models: 1.21.2 reads an item's model from a definition in {@code assets/<namespace>/items/}, which 1.21.1 had not, so every item model
 *     file ({@code models/item/<name>.json}) gets a definition that points at it;</li>
 * <li>armor: a 1.21.1 material named the textures of its armor (<code>textures/models/armor/&lt;name&gt;_layer_1.png</code> for the body and
 *     <code>_layer_2</code> for the legs), 1.21.2 names an equipment asset ({@code equipment/<name>.json}) whose layers point at textures under
 *     {@code textures/entity/equipment/humanoid} and {@code humanoid_leggings}.</li>
 * </ul>
 */
public final class Legacy1211Data {
	private Legacy1211Data() { }

	/** Data components 1.21.1 mods kept under their own names, and what they are in 26.3. */
	private static final Map<String, String> COMPONENTS = Map.of("spell_engine:item_model", "minecraft:item_model");

	/** The advancement with every data component renamed that has a new name, wherever a stack is written in it (an icon, an item predicate). */
	static Map<String, Object> advancement(Map<String, Object> root, FeatureJson.Stats stats) {
		return renameComponents(root, stats);
	}

	/** The same for a recipe, whose result may carry components. */
	static Map<String, Object> recipe(Map<String, Object> root, FeatureJson.Stats stats) {
		return renameComponents(root, stats);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> renameComponents(Map<String, Object> in, FeatureJson.Stats stats) {
		return (Map<String, Object>) walk(in, false, stats);
	}

	private static Object walk(Object value, boolean inComponents, FeatureJson.Stats stats) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> out = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : map.entrySet()) {
				String key = String.valueOf(e.getKey());
				String renamed = inComponents && COMPONENTS.containsKey(key) ? COMPONENTS.get(key) : key;
				if (!renamed.equals(key)) stats.count("data components renamed");
				out.put(renamed, walk(e.getValue(), key.equals("components"), stats));
			}
			return out;
		}
		if (value instanceof List<?> list) {
			List<Object> out = new ArrayList<>();
			for (Object o : list) out.add(walk(o, false, stats));
			return out;
		}
		return value;
	}

	/** The item definition for an item model file: it shows the model the file is. */
	static byte[] itemDefinition(String namespace, String modelPath) {
		Map<String, Object> model = new LinkedHashMap<>();
		model.put("type", "minecraft:model");
		model.put("model", namespace + ":item/" + modelPath);
		return Json.write(Map.of("model", model)).getBytes(StandardCharsets.UTF_8);
	}

	/** The name of the transparent texture a material without textures wears; an asset needs a layer, and this one draws nothing. */
	static final String EMPTY_TEXTURE = "uproot_empty";

	/** A fully transparent 1 by 1 PNG. */
	static final byte[] EMPTY_PNG = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

	/** The equipment asset of a material: its body layer and, when there is a legs texture, the legs layer. A material with neither wears the transparent texture. */
	static byte[] equipmentAsset(String namespace, String name, boolean body, boolean legs) {
		Map<String, Object> layers = new LinkedHashMap<>();
		if (body) layers.put("humanoid", List.of(Map.of("texture", namespace + ":" + name)));
		if (legs) layers.put("humanoid_leggings", List.of(Map.of("texture", namespace + ":" + name)));
		if (!body && !legs) {
			layers.put("humanoid", List.of(Map.of("texture", namespace + ":" + EMPTY_TEXTURE)));
			layers.put("humanoid_leggings", List.of(Map.of("texture", namespace + ":" + EMPTY_TEXTURE)));
		}
		return Json.write(Map.of("layers", layers)).getBytes(StandardCharsets.UTF_8);
	}

	/** Equipment assets the mods' code asked for (an armor material made at run time), as {@code namespace:path}: each needs an asset even when the mod has no texture for it. */
	private static final Set<String> REGISTERED_ASSETS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** Notes the equipment asset of an armor material the code made. */
	public static void registerAsset(net.minecraft.resources.Identifier asset) {
		REGISTERED_ASSETS.add(asset.getNamespace() + ":" + asset.getPath());
	}

	static Set<String> registeredAssets() {
		return REGISTERED_ASSETS;
	}
}
