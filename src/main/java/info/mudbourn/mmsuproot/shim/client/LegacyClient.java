package info.mudbourn.mmsuproot.shim.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.mojang.blaze3d.Blaze3D;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.resources.WaypointStyleManager;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.util.ARGB;

/** Client calls that 26.3 moved off {@code Minecraft}; the remap sends the old calls here. */
public final class LegacyClient {
	private LegacyClient() { }

	/** 1.21.11's {@code HumanoidModel.setAllVisible}: shows or hides the head, hat, body, arms and legs. 26.3 dropped it. */
	public static void setAllVisible(HumanoidModel<?> model, boolean visible) {
		model.head.visible = visible;
		model.hat.visible = visible;
		model.body.visible = visible;
		model.rightArm.visible = visible;
		model.leftArm.visible = visible;
		model.rightLeg.visible = visible;
		model.leftLeg.visible = visible;
	}

	/** 1.21.11's static {@code Minecraft.renderNames()}: false while the HUD is hidden (F1). The flag lives on the HUD now. */
	public static boolean renderNames() {
		return !Minecraft.getInstance().gui.hud.isHidden();
	}

	/** 1.21.11's {@code Options.hideGui}, read: the HUD keeps the F1 flag now, so the options are not asked. */
	public static boolean hideGui(Options options) {
		return Minecraft.getInstance().gui.hud.isHidden();
	}

	/** 1.21.11's {@code Minecraft.screen}, read: the HUD holds the open screen now. */
	public static Screen screen(Minecraft minecraft) {
		return minecraft.gui.screen();
	}

	/** 1.21.11's {@code Minecraft.getWaypointStyles()}: the HUD holds the waypoint styles now. */
	public static WaypointStyleManager waypointStyles(Minecraft minecraft) {
		return minecraft.gui.hud.getWaypointStyles();
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

	/** 26.1 and 26.2's {@code Util.OS.openUri(String)}: the game opens a link through {@code Blaze3D} now, and takes a URI. */
	public static void openUri(Util.OS os, String uri) {
		Blaze3D.openUri(URI.create(uri));
	}

	/** 26.1 and 26.2's {@code ResourceManager.listResources(String, Predicate)}: the filter is a {@code Selector} now. */
	public static Map<Identifier, Resource> listResources(ResourceManager manager, String path, Predicate<Identifier> filter) {
		return manager.listResources(path, filter::test);
	}

	/** {@code new LocalPlayer(...)}: 26.3 also takes the item-activation animation the player shows, a fresh one for a player that is only drawn. */
	public static Object[] localPlayer(Minecraft minecraft, net.minecraft.client.multiplayer.ClientLevel level,
			net.minecraft.client.multiplayer.ClientPacketListener connection, net.minecraft.stats.StatsCounter stats,
			net.minecraft.client.ClientRecipeBook recipes, net.minecraft.world.entity.player.Input input, boolean wasShiftKeyDown,
			net.minecraft.client.multiplayer.chat.ChatAbilities chat) {
		return new Object[] {minecraft, level, connection, stats, recipes, input, wasShiftKeyDown, chat, new net.minecraft.client.player.ItemActivation()};
	}

	/** {@code new ConfirmLinkScreen(callback, String url, boolean trusted)}: 26.3 takes the address as a URI. */
	public static Object[] confirmLinkScreen(it.unimi.dsi.fastutil.booleans.BooleanConsumer callback, String url, boolean trusted) {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			// 26.2 took any string and showed it; an address that is no URI cannot be opened, so the screen offers a blank address to decline
			uri = URI.create("about:blank");
		}
		return new Object[] {callback, uri, trusted};
	}
}
