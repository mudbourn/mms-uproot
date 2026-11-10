package info.mudbourn.mmsuproot.probe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Water Resistance Potion (mod id hwr). Read from its classes: the effect {@code hwr:water_resistance}; the potions
 * {@code hwr:water_resistance_potion} (3600 ticks) and {@code hwr:long_water_resistance_potion} (9600); two brewing mixes added through
 * Fabric's build event (awkward + the configured recipe item, default sponge; that + redstone); an enchanted golden apple gives 6000
 * ticks of the effect ({@code Consumable.onConsume} tail); {@code LivingEntity.aiStep} is cancelled where a water-sensitive mob would be
 * hurt by water when the effect is on; the water fog is scaled by 1.75.
 */
final class HwrProbes {
	private static final Identifier EFFECT = Identifier.fromNamespaceAndPath("hwr", "water_resistance");
	private static final Identifier POTION = Identifier.fromNamespaceAndPath("hwr", "water_resistance_potion");
	private static final Identifier LONG_POTION = Identifier.fromNamespaceAndPath("hwr", "long_water_resistance_potion");

	private HwrProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("hwr.registered", HwrProbes::registered),
			Probe.once("hwr.brewing-stand", HwrProbes::brewing),
			Probe.once("hwr.drink-potion", HwrProbes::drink),
			Probe.once("hwr.enchanted-golden-apple", HwrProbes::apple),
			Probe.steps("hwr.water-sensitive-mob-not-hurt", 200, HwrProbes::waterMob),
			Probe.once("hwr.water-fog-scaled", HwrProbes::fog),
			Probe.eyes("hwr.fog-colour-and-look", "swim underwater with the potion on and confirm the water looks clearer and the fog tint is unchanged"));
	}

	private static Holder<MobEffect> effect() {
		return BuiltInRegistries.MOB_EFFECT.get(EFFECT).orElseThrow(() -> new Probe.Fail("effect " + EFFECT + " is not registered"));
	}

	private static Holder<Potion> potion(Identifier id) {
		return BuiltInRegistries.POTION.get(id).orElseThrow(() -> new Probe.Fail("potion " + id + " is not registered"));
	}

	private static String registered(Probe.Ctx c) {
		Holder<MobEffect> effect = effect();
		StringBuilder sb = new StringBuilder("effect ").append(EFFECT);
		for (Identifier id : new Identifier[] {POTION, LONG_POTION}) {
			Potion p = potion(id).value();
			if (p.getEffects().size() != 1 || !p.getEffects().get(0).getEffect().equals(effect)) throw new Probe.Fail(id + " does not carry the effect: " + p.getEffects());
			sb.append(", ").append(id).append(" duration ").append(p.getEffects().get(0).getDuration());
		}
		if (potion(POTION).value().getEffects().get(0).getDuration() != 3600) throw new Probe.Fail("short potion is not 3600 ticks");
		if (potion(LONG_POTION).value().getEffects().get(0).getDuration() != 9600) throw new Probe.Fail("long potion is not 9600 ticks");
		return sb.toString();
	}

	private static Item reagent() throws Exception {
		Class<?> hwr = Class.forName("xyz.alycat.hwr.Hwr", true, HwrProbes.class.getClassLoader());
		Object config = hwr.getField("CONFIG").get(null);
		String id = (String) config.getClass().getMethod("potion_recipe").invoke(config);
		return BuiltInRegistries.ITEM.get(Identifier.parse(id)).orElseThrow(() -> new Probe.Fail("recipe item " + id + " is not an item")).value();
	}

	/** Puts a potion in a real brewing stand and ticks it until the bottle changes; returns the potion the bottle now holds. */
	private static Holder<Potion> brew(ServerLevel level, BrewingStandBlockEntity stand, BlockPos pos, BlockState state, Item ingredient) {
		stand.setItem(3, new ItemStack(ingredient));
		stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
		PotionContents before = stand.getItem(0).get(DataComponents.POTION_CONTENTS);
		for (int i = 0; i < 1000; i++) {
			BrewingStandBlockEntity.serverTick(level, pos, state, stand);
			PotionContents now = stand.getItem(0).get(DataComponents.POTION_CONTENTS);
			if (now != null && !now.equals(before)) return now.potion().orElseThrow(() -> new Probe.Fail("brewed bottle has no potion: " + now));
		}
		throw new Probe.Fail("nothing was brewed from " + before + " + " + ingredient + " in 1000 ticks");
	}

	private static String brewing(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerPlayer player = c.server().getPlayerList().getPlayers().get(0);
			ServerLevel level = player.level();
			BlockPos pos = player.blockPosition().offset(3, 0, 0);
			BlockState old = level.getBlockState(pos);
			try {
				level.setBlock(pos, Blocks.BREWING_STAND.defaultBlockState(), 3);
				BrewingStandBlockEntity stand = (BrewingStandBlockEntity) level.getBlockEntity(pos);
				if (stand == null) throw new Probe.Fail("brewing stand has no block entity");
				BlockState state = level.getBlockState(pos);
				for (int slot = 0; slot < 3; slot++) stand.setItem(slot, PotionContents.createItemStack(Items.POTION, Potions.AWKWARD));
				Item recipe = reagent();
				Holder<Potion> first = brew(level, stand, pos, state, recipe);
				if (!first.is(POTION)) throw new Probe.Fail("awkward + " + recipe + " brewed " + first.unwrapKey().orElse(null) + ", want " + POTION);
				Holder<Potion> second = brew(level, stand, pos, state, Items.REDSTONE);
				if (!second.is(LONG_POTION)) throw new Probe.Fail(POTION + " + redstone brewed " + second.unwrapKey().orElse(null) + ", want " + LONG_POTION);
				return "brewing stand: awkward + " + BuiltInRegistries.ITEM.getKey(recipe) + " -> " + POTION + "; + redstone -> " + LONG_POTION;
			} finally {
				level.removeBlockEntity(pos);
				level.setBlock(pos, old, 3);
			}
		});
	}

	private static String drink(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerPlayer player = c.server().getPlayerList().getPlayers().get(0);
			Holder<MobEffect> effect = effect();
			player.removeEffect(effect);
			try {
				ItemStack bottle = PotionContents.createItemStack(Items.POTION, potion(POTION));
				bottle.finishUsingItem(player.level(), player);
				MobEffectInstance on = player.getEffect(effect);
				if (on == null) throw new Probe.Fail("drinking the potion did not give the effect; effects: " + player.getActiveEffects());
				if (on.getDuration() > 3600 || on.getDuration() < 3500) throw new Probe.Fail("effect duration " + on.getDuration() + ", want about 3600");
				return "drinking " + POTION + " gives the effect for " + on.getDuration() + " ticks";
			} finally {
				player.removeEffect(effect);
			}
		});
	}

	private static String apple(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerPlayer player = c.server().getPlayerList().getPlayers().get(0);
			Holder<MobEffect> effect = effect();
			player.removeEffect(effect);
			try {
				new ItemStack(Items.ENCHANTED_GOLDEN_APPLE).finishUsingItem(player.level(), player);
				MobEffectInstance on = player.getEffect(effect);
				if (on == null) throw new Probe.Fail("eating an enchanted golden apple did not give the effect");
				if (on.getDuration() > 6000 || on.getDuration() < 5900) throw new Probe.Fail("effect duration " + on.getDuration() + ", want about 6000");
				return "enchanted golden apple gives the effect for " + on.getDuration() + " ticks (Consumable.onConsume mixin)";
			} finally {
				player.removeEffect(effect);
				player.removeAllEffects();
			}
		});
	}

	/**
	 * Two blazes (water-sensitive, no AI) stand in the same water, 30 ticks apart from nothing: the one with the effect must keep its
	 * health, the other must lose some, or the test says nothing about the effect.
	 */
	private static String waterMob(Probe.Ctx c, int tick) {
		if (tick == 1) {
			c.onServer(() -> {
				ServerPlayer player = c.server().getPlayerList().getPlayers().get(0);
				ServerLevel level = player.level();
				BlockPos base = new BlockPos(player.blockPosition().getX() + 4, Math.min(player.blockPosition().getY() + 10, level.getMaxY() - 12), player.blockPosition().getZ() + 4);
				c.state.put("level", level);
				List<BlockPos> touched = new ArrayList<>();
				List<BlockState> olds = new ArrayList<>();
				for (int x = -1; x <= 4; x++) {
					for (int z = -1; z <= 3; z++) {
						for (int y = 0; y <= 4; y++) {
							BlockPos p = base.offset(x, y, z);
							touched.add(p);
							olds.add(level.getBlockState(p));
							boolean wall = x == -1 || x == 4 || z == -1 || z == 3;
							BlockState s = y == 0 ? Blocks.STONE.defaultBlockState() : wall ? (y <= 3 ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState())
								: y <= 3 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
							level.setBlock(p, s, 3);
						}
					}
				}
				c.state.put("touched", touched);
				c.state.put("olds", olds);
				Mob treated = spawn(level, base.offset(0, 1, 0));
				Mob control = spawn(level, base.offset(2, 1, 0));
				treated.addEffect(new MobEffectInstance(effect(), 1200));
				c.state.put("treated", treated);
				c.state.put("control", control);
				return null;
			});
			return null;
		}
		if (tick < 40) return null;
		Mob treated = (Mob) c.state.get("treated");
		Mob control = (Mob) c.state.get("control");
		try {
			float tHealth = treated.getHealth();
			float cHealth = control.getHealth();
			boolean cGone = control.isRemoved() || !control.isAlive();
			if (treated.isRemoved() || tHealth < treated.getMaxHealth()) throw new Probe.Fail("the blaze with the effect lost health in water: " + tHealth + "/" + treated.getMaxHealth());
			if (!cGone && cHealth >= control.getMaxHealth()) throw new Probe.Fail("the control blaze without the effect was not hurt by water either (" + cHealth + "), so the setup proves nothing");
			if (!treated.isInWater() || !treated.hasEffect(effect())) throw new Probe.Fail("treated blaze is not in water with the effect (inWater=" + treated.isInWater() + ")");
			return "after 40 ticks in water: blaze with the effect " + tHealth + "/" + treated.getMaxHealth() + ", control " + (cGone ? "dead" : String.valueOf(cHealth));
		} finally {
			cleanup(c);
		}
	}

	private static Mob spawn(ServerLevel level, BlockPos pos) {
		Mob mob = EntityTypes.BLAZE.spawn(level, pos, EntitySpawnReason.COMMAND);
		if (mob == null) throw new Probe.Fail("could not spawn a blaze at " + pos);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		return mob;
	}

	@SuppressWarnings("unchecked")
	private static void cleanup(Probe.Ctx c) {
		ServerLevel level = (ServerLevel) c.state.get("level");
		if (level == null) return;
		c.onServer(() -> {
			for (String key : new String[] {"treated", "control"}) {
				Mob mob = (Mob) c.state.get(key);
				if (mob != null) mob.discard();
			}
			List<BlockPos> touched = (List<BlockPos>) c.state.get("touched");
			List<BlockState> olds = (List<BlockState>) c.state.get("olds");
			// last placed first, so water gone before the floor
			for (int i = touched.size() - 1; i >= 0; i--) level.setBlock(touched.get(i), olds.get(i), 3);
			return null;
		});
	}

	private static String fog(Probe.Ctx c) {
		var mc = c.mc;
		Holder<MobEffect> effect = effect();
		mc.player.removeEffect(effect);
		WaterFogEnvironment env = new WaterFogEnvironment();
		FogData plain = new FogData();
		env.setupFog(plain, mc.gameRenderer.mainCamera(), mc.level, 16.0F, mc.getDeltaTracker());
		mc.player.addEffect(new MobEffectInstance(effect, 1200));
		try {
			if (mc.gameRenderer.mainCamera().entity() != mc.player) throw new Probe.Fail("the main camera does not follow the player: " + mc.gameRenderer.mainCamera().entity());
			FogData with = new FogData();
			env.setupFog(with, mc.gameRenderer.mainCamera(), mc.level, 16.0F, mc.getDeltaTracker());
			if (plain.environmentalEnd <= 0) throw new Probe.Fail("vanilla water fog end is " + plain.environmentalEnd);
			float ratio = with.environmentalEnd / plain.environmentalEnd;
			float startRatio = plain.environmentalStart == 0 ? 1.75F : with.environmentalStart / plain.environmentalStart;
			if (Math.abs(ratio - 1.75F) > 0.001F || Math.abs(startRatio - 1.75F) > 0.001F) throw new Probe.Fail("fog end scaled " + ratio + ", start " + startRatio + ", want 1.75");
			if (with.skyEnd != with.environmentalEnd || with.cloudEnd != with.environmentalEnd) throw new Probe.Fail("sky/cloud fog end do not follow the environmental end");
			return "water fog with the effect: end " + plain.environmentalEnd + " -> " + with.environmentalEnd + " (x" + ratio + ")";
		} finally {
			mc.player.removeEffect(effect);
		}
	}
}
