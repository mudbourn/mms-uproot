package info.mudbourn.mmsuproot.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import info.mudbourn.mmsuproot.shim.client.LegacyView;
import info.mudbourn.mmsuproot.shim.compat.LegacyContainerListener;
import info.mudbourn.mmsuproot.shim.compat.LegacyContainers;
import info.mudbourn.mmsuproot.shim.client.LegacyClient;
import info.mudbourn.mmsuproot.shim.compat.LegacyServerCalls;
import info.mudbourn.mmsuproot.shim.compat.LegacySounds;
import info.mudbourn.mmsuproot.shim.compat.LegacyStacks;
import info.mudbourn.mmsuproot.shim.compat.LegacyWorld;
import info.mudbourn.mmsuproot.shim.compat.PotionBrewing;
import info.mudbourn.mmsuproot.shim.entity.LegacyVillagers;
import info.mudbourn.mmsuproot.shim.item.LegacyItemFields;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.math.Fraction;

/**
 * The shims behind the rules for the 1.21.11 mods with no 26.3 build (the "gaps" round): each one is called with the arguments a mod's call
 * had and checked against what 1.21.11's method did, read from its bytecode. They need no mod, so they run in every probe run. The
 * creative tab probe at the end rebuilds the tabs, which runs the tab callbacks of whichever of those mods is installed.
 */
final class GapProbes {
	private GapProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("gaps.stacks-and-formats", GapProbes::stacks),
			Probe.once("gaps.item-fields", GapProbes::itemFields),
			Probe.once("gaps.set-day-time", GapProbes::dayTime),
			Probe.once("gaps.set-weather", GapProbes::weather),
			Probe.once("gaps.random-teleport", GapProbes::randomTeleport),
			Probe.once("gaps.is-singleplayer", GapProbes::singleplayer),
			Probe.once("gaps.create-lookup", GapProbes::lookup),
			Probe.once("gaps.container-listener", GapProbes::containerListener),
			Probe.once("gaps.bundle-mutable", GapProbes::bundle),
			Probe.once("gaps.command-source-names", GapProbes::commandSource),
			Probe.once("gaps.deflect", GapProbes::deflect),
			Probe.once("gaps.brewing-mix", GapProbes::brewing),
			Probe.once("gaps.attack-arm", GapProbes::attackArm),
			Probe.once("gaps.view-calls", GapProbes::view),
			Probe.once("gaps.option-listener", GapProbes::options),
			Probe.steps("gaps.villager-structure-flag", 200, GapProbes::villagerFlag),
			Probe.once("gaps.creative-tabs-rebuild", GapProbes::creativeTabs),
			Probe.eyes("gaps.first-person-pike", "hold a Basic Weapons pike in first person: it is held with the vanilla pose, since the pike hook is off"),
			Probe.eyes("gaps.scroll-panels", "open an Arc or Jobs+ screen with a scrolling list: the scroll bar is drawn and drags"));
	}

	private static String stacks(Probe.Ctx c) {
		List<String> tags = LegacyStacks.getTags(new ItemStack(Items.OAK_LOG)).map(t -> t.location().toString()).toList();
		if (!tags.contains(ItemTags.LOGS.location().toString())) throw new Probe.Fail("oak log is not in minecraft:logs: " + tags);
		Component name = LegacyStacks.getName(Items.STICK);
		if (!(name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) || !t.getKey().equals("item.minecraft.stick")) throw new Probe.Fail("stick name is " + name);
		if (!LegacyStacks.formattingName(ChatFormatting.DARK_BLUE).equals("dark_blue")) throw new Probe.Fail("name of dark blue is " + LegacyStacks.formattingName(ChatFormatting.DARK_BLUE));
		for (ChatFormatting f : ChatFormatting.values()) {
			boolean want = !List.of(ChatFormatting.RESET, ChatFormatting.OBFUSCATED, ChatFormatting.BOLD, ChatFormatting.STRIKETHROUGH, ChatFormatting.UNDERLINE, ChatFormatting.ITALIC).contains(f); // 1.21.11's isFormat constants and reset
			if (LegacyStacks.formattingIsColor(f) != want) throw new Probe.Fail("isColor(" + f + ") is " + LegacyStacks.formattingIsColor(f) + ", want " + want);
		}
		return "tags of an oak log, the stick's name component, the lower-case format names and the colour test agree with 1.21.11's";
	}

	private static String itemFields(Probe.Ctx c) {
		if (LegacyItemFields.GRAY_STAINED_GLASS_PANE != Items.STAINED_GLASS_PANE.gray()) throw new Probe.Fail("gray pane differs");
		if (LegacySounds.HOE_TILL != net.minecraft.sounds.SoundEvents.HOE_TILL.value()) throw new Probe.Fail("hoe till sound differs");
		return "the gray pane constant and the hoe sound are the 26.3 entries' values";
	}

	private static String dayTime(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			long before = LegacyWorld.getDayTime(level);
			try {
				LegacyServerCalls.setDayTime(level, 6000L);
				long now = LegacyWorld.getDayTime(level);
				if (now != 6000L) throw new Probe.Fail("day time is " + now + " after setting 6000");
			} finally {
				LegacyServerCalls.setDayTime(level, before);
			}
			return "setDayTime sets the overworld clock that getDayTime reads";
		});
	}

	private static String weather(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			var data = c.server().getWeatherData();
			int clear = data.getClearWeatherTime(), rain = data.getRainTime(), thunder = data.getThunderTime();
			boolean raining = data.isRaining(), thundering = data.isThundering();
			try {
				LegacyServerCalls.setWeatherParameters(level, 0, 4000, true, true);
				if (!data.isRaining() || !data.isThundering() || data.getRainTime() != 4000 || data.getThunderTime() != 4000 || data.getClearWeatherTime() != 0) {
					throw new Probe.Fail("weather after setWeatherParameters: raining=" + data.isRaining() + " thundering=" + data.isThundering() + " rain=" + data.getRainTime() + " thunder=" + data.getThunderTime());
				}
			} finally {
				data.setClearWeatherTime(clear);
				data.setRainTime(rain);
				data.setThunderTime(thunder);
				data.setRaining(raining);
				data.setThundering(thundering);
			}
			return "setWeatherParameters sets clear, rain and thunder times and both flags, as 1.21.11's did";
		});
	}

	/** A stone floor and clear air high above the player, so the answer does not depend on the biome, trees or water under the spawn. */
	private static String randomTeleport(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerPlayer player = c.serverPlayer();
			ServerLevel level = player.level();
			LivingEntity pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
			if (pig == null) throw new Probe.Fail("could not make a pig");
			int bx = player.blockPosition().getX() + 40, bz = player.blockPosition().getZ(), floor = Math.min(level.getMaxY() - 20, player.blockPosition().getY() + 30);
			List<BlockPos> changed = new ArrayList<>();
			try {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						level.setBlockAndUpdate(new BlockPos(bx + dx, floor, bz + dz), Blocks.STONE.defaultBlockState());
						changed.add(new BlockPos(bx + dx, floor, bz + dz));
						for (int dy = 1; dy <= 10; dy++) {
							level.setBlockAndUpdate(new BlockPos(bx + dx, floor + dy, bz + dz), Blocks.AIR.defaultBlockState());
							changed.add(new BlockPos(bx + dx, floor + dy, bz + dz));
						}
					}
				}
				double x = bx + 0.5, z = bz + 0.5, y = floor + 8;
				pig.setPos(x, y, z);
				boolean ok = LegacyServerCalls.randomTeleport(pig, x, y, z, false);
				if (!ok) throw new Probe.Fail("randomTeleport returned false 7 blocks over a stone floor at y " + floor);
				if (Math.abs(pig.getY() - (floor + 1)) > 1.0E-6) throw new Probe.Fail("the pig ended at y " + pig.getY() + ", not on the floor at " + (floor + 1));
				return "from 7 blocks over a stone floor the pig lands on it, at y " + pig.getY();
			} finally {
				for (BlockPos pos : changed) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
			}
		});
	}

	private static String singleplayer(Probe.Ctx c) {
		if (!LegacyClient.isSingleplayer(c.mc)) throw new Probe.Fail("an unpublished integrated server is not singleplayer");
		return "an integrated server that is not on LAN is singleplayer";
	}

	private static String lookup(Probe.Ctx c) {
		HolderLookup.Provider provider = LegacyServerCalls.createLookup();
		if (provider.lookup(Registries.BIOME).isEmpty() || provider.lookup(Registries.ENCHANTMENT).isEmpty()) throw new Probe.Fail("the lookup lacks the biome or enchantment registry");
		if (LegacyServerCalls.createLookup() != provider) throw new Probe.Fail("the lookup is not kept");
		return "the vanilla lookup holds the world and reloadable registries and is made once";
	}

	private static String containerListener(Probe.Ctx c) {
		SimpleContainer container = new SimpleContainer(3);
		AtomicInteger told = new AtomicInteger();
		List<Object> sources = new ArrayList<>();
		LegacyContainerListener listener = changed -> {
			told.incrementAndGet();
			sources.add(changed);
		};
		LegacyContainers.addListener(container, listener);
		container.setItem(0, new ItemStack(Items.STONE));
		if (told.get() != 1 || sources.get(0) != container) throw new Probe.Fail("a change told the listener " + told.get() + " times");
		LegacyContainers.removeListener(container, listener);
		container.setItem(1, new ItemStack(Items.DIRT));
		if (told.get() != 1) throw new Probe.Fail("a removed listener was told");
		return "a listener is told once per change, with the container, and not after it is removed";
	}

	private static String bundle(Probe.Ctx c) {
		BundleContents contents = new BundleContents(List.of(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.STONE, 3)),
			net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.DIRT, 5))));
		Object[] args = LegacyContainers.bundleMutableArgs(contents);
		@SuppressWarnings("unchecked")
		BundleContents.Mutable mutable = new BundleContents.Mutable((List<ItemStack>) args[0], (Fraction) args[1], (Integer) args[2]);
		if (!mutable.toImmutable().equals(contents)) throw new Probe.Fail("the editor built from the arguments gives " + mutable.toImmutable() + ", not " + contents);
		return "new BundleContents.Mutable(contents) arguments rebuild the same contents";
	}

	private static String commandSource(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			Object[] args = LegacyServerCalls.commandSourceArgs(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, level, c.server().operatorUserPermissions(), "Rcon",
				Component.literal("Console"), c.server(), null);
			CommandSourceStack stack = new CommandSourceStack((CommandSource) args[0], (Vec3) args[1], (Vec2) args[2], (ServerLevel) args[3],
				(net.minecraft.server.permissions.PermissionSet) args[4], (CommandSourceStack.NamesProvider) args[5], (net.minecraft.server.MinecraftServer) args[6],
				(net.minecraft.world.entity.Entity) args[7]);
			if (!"Rcon".equals(stack.getTextName()) || !"Console".equals(stack.getDisplayName().getString())) {
				throw new Probe.Fail("names are " + stack.getTextName() + " / " + stack.getDisplayName().getString());
			}
			return "the nine-argument source keeps its text name and display name";
		});
	}

	private static String deflect(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerPlayer player = c.serverPlayer();
			ServerLevel level = player.level();
			Arrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
			arrow.setPos(player.getX(), player.getY() + 3, player.getZ());
			arrow.setDeltaMovement(new Vec3(0, 0, 1));
			boolean ok = LegacyServerCalls.deflect(arrow, ProjectileDeflection.REVERSE, player, EntityReference.of(player), true);
			if (!ok) throw new Probe.Fail("deflect returned false");
			if (arrow.getOwner() != player) throw new Probe.Fail("the deflected arrow's owner is " + arrow.getOwner());
			return "deflect returns true, sets the owner and moves the arrow to " + arrow.getDeltaMovement();
		});
	}

	private static String brewing(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
			ItemStack awkward = PotionBrewing.of(level).mix(new ItemStack(Items.NETHER_WART), water);
			var potion = awkward.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion();
			if (potion.isEmpty() || !potion.get().is(Potions.AWKWARD)) throw new Probe.Fail("water and nether wart gave " + awkward + " (" + potion + ")");
			ItemStack unchanged = PotionBrewing.of(level).mix(new ItemStack(Items.STICK), water);
			if (unchanged != water) throw new Probe.Fail("a stick changed the bottle");
			return "nether wart turns a water bottle into an awkward potion and a stick leaves it alone";
		});
	}

	private static String attackArm(Probe.Ctx c) throws Exception {
		ArmedEntityRenderState state = new ArmedEntityRenderState();
		state.mainArm = HumanoidArm.RIGHT;
		java.lang.reflect.Method m = HumanoidRenderState.class.getMethod("mms_uproot$attackArm");
		java.lang.reflect.Field hand = ArmedEntityRenderState.class.getDeclaredField("mms_uproot$swingingArm");
		hand.setAccessible(true);
		if (m.invoke(state) != HumanoidArm.LEFT) throw new Probe.Fail("before the first swing gave " + m.invoke(state));
		hand.set(state, InteractionHand.MAIN_HAND);
		if (m.invoke(state) != HumanoidArm.RIGHT) throw new Probe.Fail("main hand gave " + m.invoke(state));
		hand.set(state, InteractionHand.OFF_HAND);
		if (m.invoke(state) != HumanoidArm.LEFT) throw new Probe.Fail("off hand gave " + m.invoke(state));
		state.mainArm = HumanoidArm.LEFT;
		if (m.invoke(state) != HumanoidArm.RIGHT) throw new Probe.Fail("off hand of a left-handed player gave " + m.invoke(state));
		// the hand of a real entity: unset until its first swing, kept once it has swung
		LivingEntity fresh = (LivingEntity) EntityTypes.ZOMBIE.create(c.mc.level, EntitySpawnReason.COMMAND);
		var holder = (info.mudbourn.mmsuproot.shim.entity.SwingingArmHolder) fresh;
		if (holder.mms_uproot$lastSwingHand() != null) throw new Probe.Fail("an entity that never swung had " + holder.mms_uproot$lastSwingHand());
		fresh.swing(InteractionHand.OFF_HAND, ItemStack.EMPTY.getAttackAnimation(), false);
		if (holder.mms_uproot$lastSwingHand() != InteractionHand.OFF_HAND) throw new Probe.Fail("the swing hand was " + holder.mms_uproot$lastSwingHand() + " after an off-hand swing");
		return "the attack arm follows the last swing's hand, the other arm before any swing; an entity remembers the hand it swung";
	}

	private static String view(Probe.Ctx c) {
		BlockPos pos = c.mc.player.blockPosition().below();
		var stone = Blocks.STONE.defaultBlockState();
		if (!LegacyView.isViewBlocking(stone, c.mc.level, pos)) throw new Probe.Fail("stone does not block the view");
		if (LegacyView.isViewBlocking(Blocks.AIR.defaultBlockState(), c.mc.level, pos)) throw new Probe.Fail("air blocks the view");
		LegacyView.needsUpdate(c.mc.levelRenderer);
		LegacyView.pick(c.mc.gameRenderer, 0.0F);
		if (c.mc.hitResult == null) throw new Probe.Fail("the pick left no hit result");
		return "stone blocks the view and air does not, needsUpdate and pick run, the pick sets the hit result";
	}

	private static String options(Probe.Ctx c) {
		AtomicInteger changes = new AtomicInteger();
		OptionInstance<Boolean> option = LegacyView.createBoolean("gaps.option", false, v -> changes.incrementAndGet());
		option.set(true);
		if (!option.get() || changes.get() != 1) throw new Probe.Fail("the option is " + option.get() + " after " + changes.get() + " change events");
		OptionInstance<Boolean> tipped = LegacyView.createBoolean("gaps.option2", v -> null, true, v -> changes.incrementAndGet());
		tipped.set(false);
		if (tipped.get() || changes.get() != 2) throw new Probe.Fail("the tooltip option is " + tipped.get() + " after " + changes.get() + " change events");
		return "createBoolean with a Consumer sets the value and calls the listener once per change";
	}

	/** A villager a structure spawned has the flag until its first tick, and saves it under 1.21.11's key meanwhile. */
	private static String villagerFlag(Probe.Ctx c, int tick) {
		if (tick == 1) {
			c.onServer(() -> {
				ServerPlayer player = c.serverPlayer();
				ServerLevel level = player.level();
				Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
				villager.setPos(player.getX() + 2, player.getY(), player.getZ() + 2);
				if (LegacyVillagers.assignProfessionWhenSpawned(villager)) throw new Probe.Fail("a command-spawned villager has the flag");
				villager.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), EntitySpawnReason.STRUCTURE, null);
				if (!LegacyVillagers.assignProfessionWhenSpawned(villager)) throw new Probe.Fail("a structure-spawned villager has no flag");
				TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
				villager.saveWithoutId(out);
				if (!out.buildResult().getBooleanOr("AssignProfessionWhenSpawned", false)) throw new Probe.Fail("the flag is not saved");
				level.addFreshEntity(villager);
				c.state.put("villager", villager);
			});
			return null;
		}
		if (tick < 8) return null;
		Villager villager = (Villager) c.state.get("villager");
		boolean still = c.onServer(() -> {
			boolean flag = LegacyVillagers.assignProfessionWhenSpawned(villager);
			villager.discard();
			return flag;
		});
		if (still) throw new Probe.Fail("the flag was still set after the villager ticked");
		return "set by a structure spawn, saved as AssignProfessionWhenSpawned, clear after the first server tick";
	}

	/**
	 * Rebuilds every creative tab, which runs the tab callbacks of the mods installed (expanded weaponry adds items with insertAfter and
	 * insertBefore, which 1.21.11 called addAfter and addBefore), and checks that a mod's items are in some tab.
	 */
	private static String creativeTabs(Probe.Ctx c) {
		boolean weaponry = FabricLoader.getInstance().isModLoaded("expanded_weaponry");
		var level = c.mc.level;
		CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
		int items = 0;
		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) items += tab.getDisplayItems().size();
		if (items == 0) throw new Probe.Fail("no tab has an item after the rebuild");
		if (!weaponry) throw new Probe.Skip("Expanded Weaponry is not installed; tabs rebuilt with " + items + " items and no mod callback to check");
		int ours = 0;
		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
			for (ItemStack stack : tab.getDisplayItems()) {
				if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("expanded_weaponry")) ours++;
			}
		}
		if (ours == 0) throw new Probe.Fail("no Expanded Weaponry item is in a creative tab");
		return ours + " Expanded Weaponry stacks are in the creative tabs";
	}
}
