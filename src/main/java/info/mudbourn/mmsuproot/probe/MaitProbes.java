package info.mudbourn.mmsuproot.probe;

import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Mob AI Tweaks (1.21.11 build) on 26.3's slimes. */
final class MaitProbes {
	private MaitProbes() { }

	static List<Probe> all() {
		return List.of(Probe.steps("mait.slime-split-copies-offhand", 200, MaitProbes::split));
	}

	/** A slime holding an item in its off hand splits into slimes that hold it too. */
	private static String split(Probe.Ctx c, int tick) {
		if (!FabricLoader.getInstance().isModLoaded("mob-ai-tweaks")) throw new Probe.Skip("Mob AI Tweaks is not installed");
		if (tick == 1) {
			c.onServer(() -> {
				ServerLevel level = c.server().getPlayerList().getPlayers().get(0).level();
				Slime slime = EntityTypes.SLIME.create(level, EntitySpawnReason.COMMAND);
				slime.setPos(c.server().getPlayerList().getPlayers().get(0).getX() + 3.0, c.server().getPlayerList().getPlayers().get(0).getY(), c.server().getPlayerList().getPlayers().get(0).getZ());
				slime.setNoAi(true);
				slime.setSize(2, true);
				slime.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
				level.addFreshEntity(slime);
				c.state.put("slime", slime);
				slime.kill(level);
				return null;
			});
			return null;
		}
		if (tick < 60) return null;
		return c.onServer(() -> {
			Slime parent = (Slime) c.state.get("slime");
			ServerLevel level = c.server().getPlayerList().getPlayers().get(0).level();
			List<Slime> children = level.getEntitiesOfClass(Slime.class, new AABB(parent.blockPosition()).inflate(6.0), s -> s != parent && s.getSize() < 2);
			if (children.isEmpty()) throw new Probe.Fail("the slime did not split into smaller ones (parent removed: " + parent.isRemoved() + ")");
			int holding = 0;
			for (Slime child : children) {
				if (child.getOffhandItem().is(Items.STICK)) holding++;
				child.discard();
			}
			if (holding != children.size()) throw new Probe.Fail(holding + " of " + children.size() + " split slimes hold the stick");
			return "all " + children.size() + " slimes that the dying slime split into hold its off-hand stick";
		});
	}
}
