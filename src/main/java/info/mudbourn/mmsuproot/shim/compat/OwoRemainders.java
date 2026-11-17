package info.mudbourn.mmsuproot.shim.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * owo-lib's recipe remainders: a recipe file may carry an {@code owo:remainders} object that maps an ingredient item to the stack it leaves
 * behind. owo read it while its hook in the 1.21.11 recipe reader parsed the file and handed it to its own {@code RecipeRemainderStorage};
 * 26.3 reads recipes through the data registries, so this runs on the parsed file of a recipe and hands owo's storage the same map.
 * owo is reached through reflection (Uproot has no build dependency on it), only when a recipe has the entry.
 */
public final class OwoRemainders {
	private static final String ENTRY = "owo:remainders";
	private static MethodHandle store;

	private OwoRemainders() { }

	/** Looks at the parsed file of one data element; does nothing for anything but a recipe that has the entry. */
	public static void read(ResourceKey<?> key, JsonElement json) {
		if (!(json instanceof JsonObject object) || !object.has(ENTRY) || !key.registry().equals(Registries.RECIPE.identifier())) return;
		Map<Item, ItemStack> remainders = new HashMap<>();
		for (Entry<String, JsonElement> entry : object.getAsJsonObject(ENTRY).entrySet()) {
			Holder<Item> item = GsonHelper.convertToItem(new JsonPrimitive(entry.getKey()), entry.getKey());
			ItemStack stack;
			if (entry.getValue().isJsonObject()) {
				stack = ItemStack.CODEC.parse(JsonOps.INSTANCE, entry.getValue().getAsJsonObject()).getOrThrow(JsonParseException::new);
			} else {
				stack = new ItemStack(GsonHelper.convertToItem(entry.getValue(), "item"));
			}
			remainders.put(item.value(), stack);
		}
		if (!remainders.isEmpty()) store(key.identifier(), remainders);
	}

	private static synchronized void store(Identifier recipe, Map<Item, ItemStack> remainders) {
		try {
			if (store == null) {
				Class<?> storage = Class.forName("io.wispforest.owo.util.RecipeRemainderStorage", true, OwoRemainders.class.getClassLoader());
				store = MethodHandles.publicLookup().findStatic(storage, "store", MethodType.methodType(void.class, Identifier.class, Map.class));
			}
			store.invoke(recipe, remainders);
		} catch (Throwable e) {
			throw new IllegalStateException("cannot hand owo the remainders of " + recipe, e);
		}
	}
}
