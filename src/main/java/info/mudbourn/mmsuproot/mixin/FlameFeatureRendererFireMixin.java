package info.mudbourn.mmsuproot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import info.mudbourn.mmsuproot.shim.client.PrometheusFire;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Prometheus's per-entity fire sprites for 26.3. {@code buildGroup} reads the two flame sprites once and hands them to {@code prepare} for every
 * entity of the batch, so the sprites are replaced at that call, for the entity it draws (see {@link PrometheusFire}).
 */
@Mixin(FlameFeatureRenderer.class)
public abstract class FlameFeatureRendererFireMixin {
	@WrapOperation(method = "buildGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FlameFeatureRenderer;prepare(Lnet/minecraft/client/renderer/feature/FlameFeatureRenderer$Submit;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"), require = 1)
	private void mms_uproot$fireSprites(FlameFeatureRenderer renderer, FlameFeatureRenderer.Submit submit, VertexConsumer consumer, TextureAtlasSprite sprite0, TextureAtlasSprite sprite1, Operation<Void> original) {
		original.call(renderer, submit, consumer,
				PrometheusFire.sprite(submit.entityRenderState(), sprite0, 0), PrometheusFire.sprite(submit.entityRenderState(), sprite1, 1));
	}
}
