package info.mudbourn.mmsuproot.mixin;

import java.io.IOException;

import info.mudbourn.mmsuproot.shim.client.LegacyShaders;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets the shader sources of a mod built for 26.2 or older compile: 26.3 wants SPIR-V friendly GLSL (see {@link LegacyShaders}).
 * Sources already in the 26.3 shape pass through untouched.
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {
	@Redirect(method = "loadShader", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/Resource;readAllAsString()Ljava/lang/String;"))
	private static String mms_uproot$legacyShader(Resource resource) throws IOException {
		return LegacyShaders.shader(resource.readAllAsString());
	}

	@Redirect(method = {"loadInclude", "lambda$listAllIncludes$0"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/Resource;readAllAsString()Ljava/lang/String;"))
	private static String mms_uproot$legacyInclude(Resource resource) throws IOException {
		return LegacyShaders.include(resource.readAllAsString());
	}
}
