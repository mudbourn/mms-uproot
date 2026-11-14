package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Method;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/** Crawl on Demand (26.2 build): the crawl state of a player, its pose, and the packet that carries the state to the client. */
final class CrawlProbes {
	private static final String MANAGER = "me.thecraftycoder.minecraft.crawlondemand.platform.CrawlStateManager";

	private CrawlProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.steps("crawlondemand.crawl-state-and-pose", 200, CrawlProbes::crawl),
			Probe.eyes("crawlondemand.keybind", "open Controls and find the Crawl key; press it in game: the player drops to the crawling pose and the camera lowers"));
	}

	private static String crawl(Probe.Ctx c, int tick) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("crawlondemand")) throw new Probe.Skip("Crawl on Demand is not installed");
		Class<?> manager = Class.forName(MANAGER, true, CrawlProbes.class.getClassLoader());
		Method set = manager.getMethod("setCrawling", Player.class, boolean.class);
		Method is = manager.getMethod("isCrawling", Player.class);
		ServerPlayer player = c.serverPlayer();
		if (tick == 1) {
			c.onServer(() -> set.invoke(null, player, true));
			return null;
		}
		if (tick == 20) {
			boolean server = c.onServer(() -> (boolean) is.invoke(null, player));
			Pose pose = c.onServer(player::getPose);
			boolean client = (boolean) is.invoke(null, c.mc.player);
			c.onServer(() -> set.invoke(null, player, false));
			if (!server) throw new Probe.Fail("the server player is not crawling after setCrawling(true)");
			if (!client) throw new Probe.Fail("the crawl state did not reach the client player");
			if (pose != Pose.SWIMMING) throw new Probe.Fail("a crawling player's pose is " + pose + ", want SWIMMING");
			return "server and client both report crawling, pose " + pose;
		}
		return null;
	}
}
