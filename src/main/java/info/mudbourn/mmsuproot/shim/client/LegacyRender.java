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

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
}
