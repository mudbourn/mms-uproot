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
 * and 10 ticks later picks up the stack in menu slot {@code slot} and 10 more later puts it into menu slot 0.
 * With {@code -Duproot.devCommand=<command>} the player also sends that chat command (no slash; {@code ~} stands for a space, as the property cannot hold one) at tick {@code uproot.devCommandTick} (default 100).
 * With {@code -Duproot.devOp=true} the integrated server makes the player an operator at tick 20, so the command above can reach
 * commands that need the owner permission, which a world without cheats does not give (there is no op command in single player).
 * With {@code -Duproot.devOp=true} the integrated server makes the player an operator at tick 20, so the command above can reach
 * commands that need the owner permission, which a world without cheats does not give (there is no op command in single player).
 * With {@code -Duproot.strictMixins=<mod id>} every target class of every mixin config is loaded at tick 40, so mixins the game would load late
 * show their failures in the log too. Without these properties this does nothing.
 */
public final class DevUse implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		String command = System.getProperty("uproot.devCommand");
		if (command != null) command = command.replace('~', ' ');
		if (Boolean.getBoolean("uproot.devOp")) {
			int[] n = {0};
			ClientTickEvents.END_CLIENT_TICK.register(mc -> {
				net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
				if (server == null || mc.player == null || ++n[0] != 20) return;
				server.execute(() -> server.getPlayerList().getPlayers().forEach(p -> server.getPlayerList().op(p.nameAndId())));
			});
		}
		if (command != null) {
			String sent = command;
			int commandAt = Integer.getInteger("uproot.devCommandTick", 100);
			int[] n = {0};
			ClientTickEvents.END_CLIENT_TICK.register(mc -> {
				if (mc.level != null && mc.player != null && ++n[0] == commandAt) mc.player.connection.sendCommand(sent);
			});
		}
		if (System.getProperty("uproot.strictMixins") != null) {
			int[] n = {0};
			ClientTickEvents.END_CLIENT_TICK.register(mc -> {
				if (mc.level == null || ++n[0] != 40) return;
				int loaded = 0;
				try {
					// Mixins.getConfigs() only lists configs not yet prepared; the registry of all of them is private
					java.lang.reflect.Field f = org.spongepowered.asm.mixin.transformer.Config.class.getDeclaredField("allConfigs");
					f.setAccessible(true);
					for (Object o : ((java.util.Map<?, ?>) f.get(null)).values()) {
						for (String target : ((org.spongepowered.asm.mixin.transformer.Config) o).getConfig().getTargets()) {
							try {
								Class.forName(target, false, DevUse.class.getClassLoader());
								loaded++;
							} catch (Throwable t) {
								org.slf4j.LoggerFactory.getLogger("mms-uproot").error("strict mixins: {} failed to load", target, t);
							}
						}
					}
				} catch (ReflectiveOperationException e) {
					org.slf4j.LoggerFactory.getLogger("mms-uproot").error("strict mixins: cannot list the mixin configs", e);
				}
				org.slf4j.LoggerFactory.getLogger("mms-uproot").info("strict mixins: loaded {} target classes", loaded);
			});
		}
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
			} else if (t == at + 20 && mc.player.containerMenu != null) {
				mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 0, ContainerInput.PICKUP, mc.player);
			}
		});
	}
}
