package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import info.mudbourn.mmsuproot.shim.compat.LegacyLoot;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/** Loot tables changed by mods through Fabric's loot event, which Uproot reroutes where 26.3's builders differ. */
final class LootProbes {
	private static final Identifier INJECT_TABLE = Identifier.withDefaultNamespace("chests/simple_dungeon");
	private static final String MARK = "uproot-probe";

	private LootProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("loot.hwr-potion-in-trial-chamber-supply", LootProbes::hwrPotion),
			Probe.once("loot.legacy-setComponents-applies", LootProbes::setComponents),
			Probe.steps("loot.owo-injectItemStack", 600, LootProbes::owoInject));
	}

	private static LootParams chest(ServerLevel level) {
		return new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
	}

	private static LootTable table(Probe.Ctx c, ResourceKey<LootTable> key) {
		LootTable t = c.server().reloadableRegistries().getLootTable(key);
		if (t == LootTable.EMPTY) throw new Probe.Fail("loot table " + key.identifier() + " is empty or missing");
		return t;
	}

	/** hwr appends a one pool with a 12 % chance of its long potion to this table (LootTableEvents.MODIFY, ConstantValue rolls, SetPotionFunction). */
	private static String hwrPotion(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			LootTable table = table(c, BuiltInLootTables.TRIAL_CHAMBERS_SUPPLY);
			Holder<?> longPotion = BuiltInRegistries.POTION.get(Identifier.fromNamespaceAndPath("hwr", "long_water_resistance_potion")).orElseThrow(() -> new Probe.Fail("hwr long potion is not registered"));
			int rolls = 3000;
			int hits = 0;
			LootParams params = chest(level);
			for (long seed = 0; seed < rolls; seed++) {
				for (ItemStack stack : table.getRandomItems(params, seed)) {
					PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
					if (stack.is(Items.POTION) && contents != null && contents.potion().isPresent() && contents.potion().get().equals(longPotion)) hits++;
				}
			}
			double rate = hits / (double) rolls;
			if (hits == 0) throw new Probe.Fail("no hwr long potion in " + rolls + " rolls of " + BuiltInLootTables.TRIAL_CHAMBERS_SUPPLY.identifier() + " (the mod's pool is missing)");
			if (rate < 0.07 || rate > 0.17) throw new Probe.Fail("hwr long potion rate " + rate + " in " + rolls + " rolls, want about 0.12");
			return hits + " hwr long potions in " + rolls + " fixed-seed rolls (" + String.format("%.3f", rate) + ", configured 0.12)";
		});
	}

	/** The Uproot helper that owo's {@code LootOps.injectItemStack} reaches for a stack with components. */
	private static String setComponents(Probe.Ctx c) {
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			DataComponentPatch patch = DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME, Component.literal(MARK)).build();
			SetComponentsFunction fn = LegacyLoot.setComponents(List.of(), patch);
			LootContext context = new LootContext.Builder(chest(level)).withOptionalRandomSeed(1L).create(Optional.empty());
			ItemStack out = fn.apply(new ItemStack(Items.STICK, 3), context);
			if (out.getCount() != 3 || out.get(DataComponents.CUSTOM_NAME) == null || !MARK.equals(out.get(DataComponents.CUSTOM_NAME).getString())) throw new Probe.Fail("the function gave " + out + " with name " + out.get(DataComponents.CUSTOM_NAME));
			return "LegacyLoot.setComponents(patch) applied: " + out + " named '" + MARK + "'";
		});
	}

	/**
	 * owo registers an addition to a loot table and applies it when tables load, so the probe injects at runtime and reloads the data
	 * packs; a 100 % chance makes the stack certain. It runs last among the loot probes because the reload replaces the tables.
	 */
	@SuppressWarnings("unchecked")
	private static String owoInject(Probe.Ctx c, int tick) throws Exception {
		if (tick == 1) {
			Class<?> ops = Class.forName("io.wispforest.owo.ops.LootOps", true, LootProbes.class.getClassLoader());
			Method inject = ops.getMethod("injectItemStack", ItemStack.class, float.class, Identifier[].class);
			ItemStack stack = new ItemStack(Items.STICK, 3);
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(MARK));
			inject.invoke(null, stack, 1.0F, new Identifier[] {INJECT_TABLE});
			c.state.put("reload", c.onServer(() -> c.server().reloadResources(c.server().getPackRepository().getSelectedIds())));
			return null;
		}
		CompletableFuture<Void> reload = (CompletableFuture<Void>) c.state.get("reload");
		if (!reload.isDone()) return null;
		reload.get();
		return c.onServer(() -> {
			ServerLevel level = c.server().overworld();
			LootTable table = table(c, ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, INJECT_TABLE));
			int found = 0;
			int rolls = 20;
			LootParams params = chest(level);
			for (long seed = 0; seed < rolls; seed++) {
				for (ItemStack stack : table.getRandomItems(params, seed)) {
					if (stack.is(Items.STICK) && stack.getCount() == 3 && stack.get(DataComponents.CUSTOM_NAME) != null && MARK.equals(stack.get(DataComponents.CUSTOM_NAME).getString())) {
						found++;
						break;
					}
				}
			}
			if (found != rolls) throw new Probe.Fail("the injected stack (3 x stick named '" + MARK + "', chance 1.0) appeared in " + found + " of " + rolls + " rolls of " + INJECT_TABLE);
			return "LootOps.injectItemStack(chance 1.0) into " + INJECT_TABLE + ": the named stack of 3 appeared in all " + rolls + " fixed-seed rolls after a data reload";
		});
	}
}
