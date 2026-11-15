package info.mudbourn.mmsuproot.mixin;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 1.21.11's {@code GuiGraphics.renderTooltip(font, lines, x, y, positioner, background)} drew a tooltip at once, with the gap after its first
 * line. 26.3 calls it {@code tooltip} and takes whether to leave that gap; this gives the old name back, answering with the gap, for the
 * mixins of mods that invoke it by name. Applied early (low priority) so those mixins find it.
 */
@Mixin(value = GuiGraphicsExtractor.class, priority = 500)
public abstract class GuiGraphicsTooltipMixin {
	@Shadow
	public abstract void tooltip(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner, Identifier background, boolean gap);

	public void renderTooltip(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner, Identifier background) {
		tooltip(font, lines, x, y, positioner, background, true);
	}
}
