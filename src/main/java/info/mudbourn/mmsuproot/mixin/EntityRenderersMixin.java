package info.mudbourn.mmsuproot.mixin;

import java.util.Map;

import info.mudbourn.mmsuproot.shim.client.LegacyContexts;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the context the entity renderers are made with, for {@link LegacyContexts}. */
@Mixin(EntityRenderers.class)
public abstract class EntityRenderersMixin {
	@Inject(method = "createEntityRenderers", at = @At("HEAD"))
	private static void mms_uproot$keepContext(EntityRendererProvider.Context context,
			CallbackInfoReturnable<Map<EntityType<?>, EntityRenderer<?, ?>>> cir) {
		LegacyContexts.rendererContext = context;
	}
}
