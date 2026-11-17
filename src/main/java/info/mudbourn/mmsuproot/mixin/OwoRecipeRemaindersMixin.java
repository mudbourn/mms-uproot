package info.mudbourn.mmsuproot.mixin;

import java.io.Reader;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import info.mudbourn.mmsuproot.shim.compat.OwoRemainders;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where 26.3 parses a data element's file ({@code loadFromResource}), recipes included: gives owo-lib the {@code owo:remainders} of a recipe
 * file, which owo read in 1.21.11 from inside {@code SimpleJsonResourceReloadListener.scanDirectory} (see {@link OwoRemainders}).
 * The file is parsed by the game as before and handed on unchanged.
 */
@Mixin(targets = "net.minecraft.resources.RegistryLoadTask$PendingRegistration")
public abstract class OwoRecipeRemaindersMixin {
	@WrapOperation(method = "loadFromResource", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/StrictJsonParser;parse(Ljava/io/Reader;)Lcom/google/gson/JsonElement;"), require = 1)
	private static JsonElement mms_uproot$readRemainders(Reader reader, Operation<JsonElement> original, @Local(argsOnly = true) ResourceKey<?> key) {
		JsonElement json = original.call(reader);
		OwoRemainders.read(key, json);
		return json;
	}
}
