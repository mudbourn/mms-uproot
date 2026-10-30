package info.mudbourn.mmsuproot.shim.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only, with {@code -Duproot.devUse=x,y,z,tick,slot}: once the world has been up for {@code tick} ticks the player
 * uses the block at x,y,z, as a right click would (this opens a block's screen for {@link info.mudbourn.mmsuproot.boot.DevShot}),
 * and 10 ticks later moves the stack in menu slot {@code slot} into menu slot 0. Without the property this does nothing.
 */
public final class DevUse implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		String spec = System.getProperty("uproot.devUse");
		if (spec == null) return;
		String[] p = spec.split(",");
		BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
		int at = Integer.parseInt(p[3]);
		int slot = Integer.parseInt(p[4]);
		int[] ticks = {0};
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null || mc.player == null || mc.gameMode == null) return;
			int t = ++ticks[0];
			if (t == at) {
				mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
			} else if (t == at + 10 && mc.player.containerMenu != null) {
				int id = mc.player.containerMenu.containerId;
				mc.gameMode.handleContainerInput(id, slot, 0, ContainerInput.PICKUP, mc.player);
				mc.gameMode.handleContainerInput(id, 0, 0, ContainerInput.PICKUP, mc.player);
			}
		});
	}
}
