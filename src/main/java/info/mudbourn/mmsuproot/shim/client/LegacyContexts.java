package info.mudbourn.mmsuproot.shim.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * The context the entity renderers are being made with. A render layer of a library takes it as an argument in 26.3 where a
 * mod's call of 1.21.11 had no such argument, so the remap passes this field for it (a {@code ctor} rule). Set by
 * {@link info.mudbourn.mmsuproot.mixin.EntityRenderersMixin} before the renderers are created.
 */
public final class LegacyContexts {
	public static EntityRendererProvider.Context rendererContext;

	private LegacyContexts() { }
}
