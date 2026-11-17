package info.mudbourn.mmsuproot.shim.client;

import com.mojang.blaze3d.vertex.PoseStack;


/** Fabric's old world render context; see {@link LegacyWorldRenderEvents}, which never hands one out. */
public interface LegacyWorldRenderContext {
	LegacyBufferSource consumers();

	PoseStack matrices();
}
