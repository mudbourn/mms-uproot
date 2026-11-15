package info.mudbourn.mmsuproot.shim.client;

import java.lang.reflect.Field;
import java.util.List;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuBackend;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import info.mudbourn.mmsuproot.shim.Approximate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Vector4f;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;

/** Stand-ins for render calls and fields that 26.3 reshaped, answered for the mods built for 26.2 and 1.21.11. */
public final class LegacyRender {
	private LegacyRender() { }

	/**
	 * 1.21.11's {@code TextureManager.INTENTIONAL_MISSING_TEXTURE}: the identifier with the empty path, which names no sprite
	 * (26.3's texture manager no longer holds it).
	 */
	public static final Identifier INTENTIONAL_MISSING_TEXTURE = Identifier.withDefaultNamespace("");

	/** 26.2's {@code RenderSystem.outputColorTextureOverride}: the colour target of the picture-in-picture render in progress, null outside one (see PictureInPictureRendererMixin). */
	public static GpuTextureView outputColorTextureOverride;
	/** 26.2's {@code RenderSystem.outputDepthTextureOverride}: likewise the depth target. */
	public static GpuTextureView outputDepthTextureOverride;

	private static Field backendField;

	/** {@code GuiGraphicsExtractor.textWithWordWrap(font, text, x, y, width, color, shadow)} returned nothing in 26.2; 26.3 returns the height. */
	public static void textWithWordWrap(GuiGraphicsExtractor graphics, Font font, FormattedText text, int x, int y, int width, int color, boolean shadow) {
		graphics.textWithWordWrap(font, text, x, y, width, color, shadow);
	}

	/** {@code GuiGraphicsExtractor.tooltip(font, components, x, y, positioner, style)}: 26.3 takes one more flag after the style; it is false here. */
	public static void tooltip(GuiGraphicsExtractor graphics, Font font, List<ClientTooltipComponent> components, int x, int y,
			ClientTooltipPositioner positioner, Identifier style) {
		graphics.tooltip(font, components, x, y, positioner, style, false);
	}

	/**
	 * {@code Window.backend()}: the GPU backend the window was made for. 26.3 keeps it only on {@code RenderSystem}
	 * (a private field), and there is one backend for the game, so a window of the same game has that one.
	 */
	public static GpuBackend backend(Window window) {
		try {
			if (backendField == null) {
				Field f = RenderSystem.class.getDeclaredField("BACKEND");
				f.setAccessible(true);
				backendField = f;
			}
			return (GpuBackend) backendField.get(null);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("The GPU backend of 26.3 is not readable", e);
		}
	}

	/**
	 * {@code GpuDevice.createSurface(window)}: 26.3 also takes a supplier that says whether the window is minimised, which
	 * the surface asks before drawing. A window made through this call is never reported minimised.
	 */
	@Approximate("a window minimised at run time is still drawn to")
	public static GpuSurface createSurface(GpuDevice device, long window) {
		return device.createSurface(window, () -> false);
	}

	/**
	 * {@code new TextureTarget(name, width, height, useDepth, colorFormat)}: 26.3 takes the depth format itself, null for none;
	 * a depth target is D32_FLOAT there, as the main target has it.
	 */
	public static Object[] textureTarget(String name, int width, int height, boolean useDepth, GpuFormat colorFormat) {
		return new Object[] {name, width, height, colorFormat, useDepth ? GpuFormat.D32_FLOAT : null};
	}

	/** 1.21.11's {@code Minecraft.UNIFORM_FONT}: the font that ignores the language's glyph choices (26.3 dropped the constant). */
	public static final Identifier UNIFORM_FONT = Identifier.withDefaultNamespace("uniform");

	/** {@code GuiGraphics.renderDeferredElements()}: 26.3 takes the mouse position and a tick fraction; the graphics knows the position it was made with. */
	@Approximate("the tick fraction handed to a preedit overlay is 0")
	public static void renderDeferredElements(GuiGraphicsExtractor graphics) {
		graphics.extractDeferredElements(graphics.mouseX, graphics.mouseY, 0.0F);
	}

	/** {@code ScissorStack.push(rect)} gave back the rectangle it pushed (the clipped one); 26.3 returns nothing, and the top of the stack is that rectangle. */
	public static ScreenRectangle pushScissor(GuiGraphicsExtractor.ScissorStack stack, ScreenRectangle rectangle) {
		stack.push(rectangle);
		return stack.peek();
	}

	/** {@code new TextureTarget(name, width, height, useDepth)}: a target of 26.3 names both formats; the colour one is RGBA8 as it always was here, and a depth one is D32_FLOAT. */
	public static Object[] textureTargetNoFormat(String name, int width, int height, boolean useDepth) {
		return new Object[] {name, width, height, GpuFormat.RGBA8_UNORM, useDepth ? GpuFormat.D32_FLOAT : null};
	}

	/** {@code CommandEncoder.clearColorAndDepthTextures(color, argb, depth, depthValue)}: 26.3 takes the clear colour as floats. */
	public static void clearColorAndDepthTextures(CommandEncoder encoder, GpuTexture color, int argb, GpuTexture depth, double depthValue) {
		float a = (argb >>> 24 & 255) / 255.0F, r = (argb >>> 16 & 255) / 255.0F, g = (argb >>> 8 & 255) / 255.0F, b = (argb & 255) / 255.0F;
		encoder.clearColorAndDepthTextures(color, new Vector4f(r, g, b, a), depth, depthValue);
	}

	/** The collector the picture-in-picture render in progress submits to, null outside one (set by PictureInPictureRendererMixin). */
	public static SubmitNodeCollector collector;

	/**
	 * {@code FeatureRenderDispatcher.getSubmitNodeStorage()}: 1.21.11's renderers submitted to the dispatcher's storage and then asked it to
	 * draw. In 26.3 a picture-in-picture render is handed its own storage and the game draws it when the render returns, so that
	 * storage is the answer. Outside such a render this throws.
	 */
	public static SubmitNodeStorage submitNodeStorage(FeatureRenderDispatcher dispatcher) {
		if (!(collector instanceof SubmitNodeStorage storage)) throw new IllegalStateException("Nothing is submitted outside a picture-in-picture render in 26.3");
		return storage;
	}

	/** {@code GameRenderer.getSubmitNodeStorage()}: see {@link #submitNodeStorage}; with no render in progress it is a fresh storage that nothing draws. */
	@Approximate("outside a picture-in-picture render the storage is a fresh one that is never drawn")
	public static SubmitNodeStorage gameSubmitNodeStorage(net.minecraft.client.renderer.GameRenderer renderer) {
		return collector instanceof SubmitNodeStorage storage ? storage : new SubmitNodeStorage();
	}

	/** {@code FeatureRenderDispatcher.renderAllFeatures()}: the game draws what a picture-in-picture render submitted when it returns, so there is nothing to do here. */
	@Approximate("what was submitted is drawn when the picture-in-picture render returns, not at this call; outside one nothing is drawn")
	public static void renderAllFeatures(FeatureRenderDispatcher dispatcher) { }

	/** {@code new PictureInPictureRenderer(bufferSource)}: 26.3's renderer takes nothing. */
	public static Object[] pictureInPictureConstructor(LegacyBufferSource.BufferSource buffers) {
		return new Object[0];
	}

	/** {@code PictureInPictureRenderer.bufferSource}: the stand-in batch (see {@link LegacyBufferSource.BufferSource}). */
	public static LegacyBufferSource.BufferSource bufferSource(PictureInPictureRenderer<?> renderer) {
		return LegacyBufferSource.BufferSource.INSTANCE;
	}

	/** {@code RenderBuffers.bufferSource()}: the stand-in batch. */
	public static LegacyBufferSource.BufferSource bufferSource(RenderBuffers buffers) {
		return LegacyBufferSource.BufferSource.INSTANCE;
	}

	/** {@code EditBox.setFilter(predicate)}: 26.3 dropped it; {@code mixin.EditBoxFilterMixin} gives the box a filter again. */
	public static void setFilter(net.minecraft.client.gui.components.EditBox box, java.util.function.Predicate<String> filter) {
		((FilteredEditBox) box).mms_uproot$setFilter(filter);
	}

	/** Fabric's {@code SpecialGuiElementRegistry.Context.vertexConsumers()}: the stand-in batch. */
	public static LegacyBufferSource.BufferSource vertexConsumers(net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry.Context context) {
		return LegacyBufferSource.BufferSource.INSTANCE;
	}

	/** {@code new GuiRenderer(state, buffers, collector, dispatcher, renderers)}: 26.3 takes the state, the dispatcher and the renderers. */
	public static Object[] guiRenderer(net.minecraft.client.renderer.state.gui.GuiRenderState state, LegacyBufferSource.BufferSource buffers,
			SubmitNodeCollector collector, FeatureRenderDispatcher dispatcher, java.util.List<PictureInPictureRenderer<?>> renderers) {
		return new Object[] {state, dispatcher, renderers};
	}
}
