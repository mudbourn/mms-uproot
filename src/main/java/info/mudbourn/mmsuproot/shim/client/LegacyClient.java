package info.mudbourn.mmsuproot.shim.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.ARGB;

/** Client calls that 26.3 moved off {@code Minecraft}; the remap sends the old calls here. */
public final class LegacyClient {
	private LegacyClient() { }

	/** 1.21.11's static {@code Minecraft.renderNames()}: false while the HUD is hidden (F1). The flag lives on the HUD now. */
	public static boolean renderNames() {
		return !Minecraft.getInstance().gui.hud.isHidden();
	}

	/** {@code Minecraft.setScreen(Screen)}: the screen is the HUD's to open and close now. */
	public static void setScreen(Minecraft minecraft, Screen screen) {
		minecraft.gui.setScreen(screen);
	}

	/**
	 * 1.21.11 handed the collector a baked block model and a colour; 26.3 takes the model's parts and one tint
	 * per tint index. The parts come from the model with a fixed seed, as a block entity draw would.
	 */
	public static void submitBlockModel(SubmitNodeCollector collector, PoseStack pose, RenderType type, BlockStateModel model,
			float red, float green, float blue, int light, int overlay, int outline) {
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42L), parts);
		collector.submitBlockModel(pose, type, parts, new int[] {ARGB.colorFromFloat(1.0F, red, green, blue)}, light, overlay, outline);
	}

	/**
	 * A mod that baked its extra block models by hand, through classes 26.3 dropped, gets the one Fabric API
	 * ships for the same job.
	 */
	public static SimpleUnbakedExtraModel<BlockStateModel> blockStateModel(Identifier id) {
		return SimpleUnbakedExtraModel.blockStateModel(id);
	}
}
