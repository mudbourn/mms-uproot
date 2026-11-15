package info.mudbourn.mmsuproot.shim.compat;

import info.mudbourn.mmsuproot.shim.Approximate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Server-side and world calls that 26.3 moved, split or changed the arguments of. */
public final class LegacyServerCalls {
	private static HolderLookup.Provider vanillaLookup;

	private LegacyServerCalls() { }

	/**
	 * {@code ServerLevel.setDayTime(long)}. 26.3 keeps time in world clocks, and {@link LegacyWorld#getDayTime} reads the
	 * overworld's, so this sets that clock whichever level asks.
	 */
	public static void setDayTime(ServerLevel level, long time) {
		Holder<WorldClock> clock = level.registryAccess().get(WorldClocks.OVERWORLD).orElseThrow();
		level.getServer().clockManager().setTotalTicks(clock, time);
	}

	/** {@code ServerLevel.setWeatherParameters(clear, rain, raining, thundering)}: the weather is the server's one {@code WeatherData} now. */
	public static void setWeatherParameters(ServerLevel level, int clearTime, int weatherTime, boolean raining, boolean thundering) {
		level.getServer().setWeatherParameters(clearTime, weatherTime, raining, thundering);
	}

	/**
	 * {@code LivingEntity.randomTeleport(x, y, z, particles)}: 1.21.11 settled on the first block below that blocks motion, and 26.3 does
	 * the same with the {@code blocks_motion} tag (through {@code entities_can_teleport_to}); its overload takes a test for blocks that
	 * rule a spot out, which 1.21.11 had none of.
	 */
	@Approximate("the target is also pulled inside the world border, as 26.3's own overload does")
	public static boolean randomTeleport(LivingEntity entity, double x, double y, double z, boolean particles) {
		return entity.randomTeleport(x, y, z, particles, state -> false);
	}

	/** {@code Minecraft.isSingleplayer()}: a local server that has not been opened to a LAN. */
	public static boolean isSingleplayer(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		return server != null && !server.isPublished();
	}

	/**
	 * {@code VanillaRegistries.createLookup()}: 1.21.11 built one lookup of the vanilla registries; 26.3 builds the world ones and then the
	 * reloadable ones on top of them. It is made once, as the game's own data generators do with theirs.
	 */
	public static synchronized HolderLookup.Provider createLookup() {
		if (vanillaLookup == null) vanillaLookup = VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup());
		return vanillaLookup;
	}

	/** {@code GossipContainer.transferFrom(other, random, max)}: 26.3 also says how many gossips moved. */
	public static void transferFrom(GossipContainer target, GossipContainer source, RandomSource random, int max) {
		target.transferFrom(source, random, max);
	}

	/**
	 * {@code Projectile.deflect(deflection, entity, owner, byAttack)}: 26.3 also takes the strength to deflect with, which it gives 1 when
	 * a hit makes the projectile deflect itself.
	 */
	public static boolean deflect(Projectile projectile, ProjectileDeflection deflection, Entity entity, EntityReference<Entity> owner, boolean byAttack) {
		return projectile.deflect(deflection, entity, owner, byAttack, 1.0D);
	}

	/**
	 * The arguments of the private {@code CommandSourceStack} constructor for 1.21.11's one with a text name and a display name: 26.3 asks
	 * a names provider for both, which here answers the same two values whichever entity it is asked about.
	 */
	public static Object[] commandSourceArgs(CommandSource source, Vec3 position, Vec2 rotation, ServerLevel level, PermissionSet permissions,
			String textName, Component displayName, MinecraftServer server, Entity entity) {
		CommandSourceStack.NamesProvider names = new CommandSourceStack.NamesProvider() {
			@Override
			public Component displayName(Entity ignored) {
				return displayName;
			}

			@Override
			public String textName(Entity ignored) {
				return textName;
			}
		};
		return new Object[] {source, position, rotation, level, permissions, names, server, entity};
	}

	/** The arguments of {@code new HoverEvent.ShowItem(stack)}: 26.3 shows an item template. */
	public static Object[] showItemArgs(ItemStack stack) {
		return new Object[] {ItemStackTemplate.fromNonEmptyStack(stack)};
	}
}
