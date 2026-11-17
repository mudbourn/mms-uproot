package info.mudbourn.mmsuproot.mixin;

import java.util.List;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.server.ReloadableServerResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.3 binds the default components of every registry entry only after the data reload has finished
 * ({@code updateComponentsAndStaticRegistryTags}), and its own data readers build ItemStackTemplates until then. A mod built for 1.21.11
 * builds real ItemStacks while it reads its data (Arc does, for the stacks in its conditions and rewards), and
 * {@code new ItemStack(item)} throws "Components not bound yet" on the first load. The components are already computed when the
 * reload starts, so entries that have none yet get theirs then; a later {@code /reload} keeps the bound components of the running game until it is done.
 */
@Mixin(ReloadableServerResources.class)
public abstract class ReloadableServerResourcesMixin {
	@Shadow
	@Final
	private List<DataComponentInitializers.PendingComponents<?>> newComponents;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void mms_uproot$bindComponentsEarly(CallbackInfo ci) {
		for (DataComponentInitializers.PendingComponents<?> pending : this.newComponents) bindUnbound(pending);
	}

	private static <T> void bindUnbound(DataComponentInitializers.PendingComponents<T> pending) {
		pending.forEach((holder, components) -> {
			if (!holder.areComponentsBound()) holder.bindComponents(components);
		});
	}
}
