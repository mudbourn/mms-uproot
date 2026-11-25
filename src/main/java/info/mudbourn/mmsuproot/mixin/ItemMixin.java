package info.mudbourn.mmsuproot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import info.mudbourn.mmsuproot.shim.item.LegacyItemIds;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Holds back the component initialiser of an item made under the placeholder id (see {@link LegacyItemIds}); registration adds it under the real one. */
@Mixin(Item.class)
public abstract class ItemMixin {
	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/component/DataComponentInitializers;add(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/component/DataComponentInitializers$Initializer;)V"))
	private <T> void mms_uproot$holdPlaceholder(DataComponentInitializers initializers, ResourceKey<T> key, DataComponentInitializers.Initializer<T> initializer,
			Operation<Void> original, @Local(argsOnly = true) Item.Properties properties) {
		if (key == LegacyItemIds.PLACEHOLDER) {
			LegacyItemIds.pending((Item) (Object) this, properties);
			return;
		}
		original.call(initializers, key, initializer);
	}
}
