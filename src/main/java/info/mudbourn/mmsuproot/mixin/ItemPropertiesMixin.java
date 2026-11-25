package info.mudbourn.mmsuproot.mixin;

import info.mudbourn.mmsuproot.shim.item.LegacyItemIds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An item of a mod built for 1.21.1 is made before it has a name, and 26.3 asks its properties for the id when the item is made. When a class of such a mod is
 * the one making it, the properties answer with a placeholder key ({@link LegacyItemIds}); anything else without an id still fails as it does.
 */
@Mixin(Item.Properties.class)
public abstract class ItemPropertiesMixin {
	@Shadow
	private ResourceKey<Item> id;

	@Inject(method = "itemIdOrThrow", at = @At("HEAD"), cancellable = true)
	private void mms_uproot$placeholderId(CallbackInfoReturnable<ResourceKey<Item>> cir) {
		if (id == null && info.mudbourn.mmsuproot.shim.item.LegacyClasses.onStack()) cir.setReturnValue(LegacyItemIds.PLACEHOLDER);
	}
}
