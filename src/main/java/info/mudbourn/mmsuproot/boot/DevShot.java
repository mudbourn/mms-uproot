package info.mudbourn.mmsuproot.boot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Dev-only: with {@code -Duproot.devShot=<dir>} the client saves its own framebuffer (never the desktop), once per
 * 100 ticks after a world is loaded. Before shot N it runs the function {@code t:sN} of the test datapack (if any),
 * 60 ticks ahead of the shot, so a datapack can stage each scene. Then it quits. {@code -Duproot.devShotCount} sets
 * how many (default 6). {@code -Duproot.devKeepScreen} keeps an open screen for the shot. Without the property this does nothing.
 */
public final class DevShot implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		String dir = System.getProperty("uproot.devShot");
		if (dir == null) return;
		int count = Integer.getInteger("uproot.devShotCount", 6);
		int[] ticks = {0};
		int[] taken = {0};
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null || mc.player == null || taken[0] >= count) return;
			// an unfocused window would open the pause menu over the shot
			mc.options.pauseOnLostFocus = false;
			if (mc.gui.screen() != null && System.getProperty("uproot.devKeepScreen") == null) mc.gui.setScreen(null);
			ticks[0]++;
			int next = taken[0] + 1;
			int stage = 80 + 100 * (next - 1);
			if (ticks[0] == stage && mc.getSingleplayerServer() != null) {
				var server = mc.getSingleplayerServer();
				server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "function t:s" + next));
			}
			if (ticks[0] != stage + 60) return;
			int n = ++taken[0];
			RenderTarget target = mc.gameRenderer.mainRenderTarget();
			Screenshot.takeScreenshot(target, image -> {
				try (image) {
					Path out = Path.of(dir, "shot" + n + ".png");
					Files.createDirectories(out.getParent());
					image.writeToFile(out);
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (n == count) Minecraft.getInstance().stop();
			});
		});
	}
}
