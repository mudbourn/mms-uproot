package info.mudbourn.mmsuproot.shim.client;

import java.util.List;
import java.util.function.Consumer;

import info.mudbourn.mmsuproot.shim.Approximate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Camera, picking and option calls of 1.21.11 that 26.3 moved to other classes or gave more arguments. */
public final class LegacyView {
	private LegacyView() { }

	/** {@code GameRenderer.pick(partialTick)}: the crosshair pick is {@code Minecraft}'s own, with the same body. */
	public static void pick(GameRenderer renderer, float partialTick) {
		Minecraft.getInstance().pick(partialTick);
	}

	/** {@code LevelRenderer.needsUpdate()}: the section graph and the clouds are marked for a rebuild, which is all it did. */
	public static void needsUpdate(LevelRenderer renderer) {
		renderer.sectionOcclusionGraph().invalidate();
		renderer.cloudRenderer().markForRebuild();
	}

	/**
	 * {@code BlockState.isViewBlocking(level, pos)}: 26.3's test also gets the box that is being looked through, which no 1.21.11
	 * test read. The box handed over is the whole block.
	 */
	@Approximate("the test is given the block's full box, not the box being looked through")
	public static boolean isViewBlocking(BlockState state, BlockGetter level, BlockPos pos) {
		return state.isViewBlocking(level, pos, new AABB(pos));
	}

	/** {@code OptionInstance.createBoolean(name, tooltip, initial, onChange)}: the listener is a type of its own now. */
	public static OptionInstance<Boolean> createBoolean(String name, OptionInstance.TooltipSupplier<Boolean> tooltip, boolean initial, Consumer<Boolean> onChange) {
		return OptionInstance.createBoolean(name, tooltip, initial, onChange::accept);
	}

	/** {@code OptionInstance.createBoolean(name, initial, onChange)}: the listener is a type of its own now. */
	public static OptionInstance<Boolean> createBoolean(String name, boolean initial, Consumer<Boolean> onChange) {
		return OptionInstance.createBoolean(name, initial, onChange::accept);
	}

	/**
	 * {@code GuiGraphics.renderTooltip(font, lines, x, y, positioner, background)}: 26.3 only adds the gap after the first line when
	 * told to, where 1.21.11 always did.
	 */
	public static void renderTooltip(GuiGraphicsExtractor graphics, Font font, List<ClientTooltipComponent> lines, int x, int y,
			ClientTooltipPositioner positioner, Identifier background) {
		graphics.tooltip(font, lines, x, y, positioner, background, true);
	}
}
