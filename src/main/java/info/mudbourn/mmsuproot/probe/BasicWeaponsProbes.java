package info.mudbourn.mmsuproot.probe;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/** Basic Weapons (1.21.11 build): its items, the loot tables it adds weapons to, the weapons its mixin gives monsters, and the enchantments it opens to them. */
final class BasicWeaponsProbes {
	private static final String NAMESPACE = "basicweapons";

	private BasicWeaponsProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("basicweapons.items-have-attack-damage", BasicWeaponsProbes::items),
			Probe.once("basicweapons.loot-tables-hold-weapons", BasicWeaponsProbes::loot),
			Probe.once("basicweapons.monsters-spawn-with-weapons", BasicWeaponsProbes::monsters),
			Probe.once("basicweapons.enchantment-and-data", BasicWeaponsProbes::enchantments));
	}

	private static void requireMod() {
		if (!FabricLoader.getInstance().isModLoaded(NAMESPACE)) throw new Probe.Skip("Basic Weapons is not installed");
	}

	/** Every weapon the mod registers is an item with an attack damage modifier in its default components. */
	private static String items(Probe.Ctx c) {
		requireMod();
		int weapons = 0;
		List<String> without = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (!id.getNamespace().equals(NAMESPACE)) continue;
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
			weapons++;
			boolean damage = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream()
				.anyMatch(e -> e.attribute().is(Attributes.ATTACK_DAMAGE) && e.modifier().amount() > 0);
			if (!damage) without.add(id.getPath());
		}
		if (weapons == 0) throw new Probe.Fail("Basic Weapons registered no items");
		if (!without.isEmpty()) throw new Probe.Fail(without.size() + " of " + weapons + " items have no attack damage modifier: " + without);
		return weapons + " items, each with an attack damage modifier";
	}

	/** The mod's loot table event adds a pool of its weapons to the vanilla chests it lists. */
	private static String loot(Probe.Ctx c) {
		requireMod();
		return c.onServer(() -> {
			var tables = c.server().reloadableRegistries();
			List<String> found = new ArrayList<>();
			for (ResourceKey<LootTable> key : List.of(BuiltInLootTables.BURIED_TREASURE, BuiltInLootTables.SHIPWRECK_SUPPLY)) {
				LootTable table = tables.getLootTable(key);
				String json = LootTable.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, table).getOrThrow(msg -> new Probe.Fail("could not encode " + key + ": " + msg)).toString();
				if (!json.contains(NAMESPACE + ":")) throw new Probe.Fail(key.identifier() + " holds no " + NAMESPACE + " item");
				found.add(key.identifier().getPath());
			}
			return "weapons are in the loot tables " + found;
		});
	}

	/** The mixin on zombies and piglins that hands out iron and golden weapons does so for a share of freshly equipped mobs. */
	private static String monsters(Probe.Ctx c) {
		requireMod();
		return c.onServer(() -> {
			var level = c.server().overworld();
			RandomSource random = RandomSource.create(1234L);
			var difficulty = level.getCurrentDifficultyAt(c.serverPlayer().blockPosition());
			int[] armed = {0, 0};
			var types = List.of(EntityTypes.ZOMBIE, EntityTypes.PIGLIN);
			for (int t = 0; t < types.size(); t++) {
				for (int i = 0; i < 300; i++) {
					Monster mob = (Monster) types.get(t).create(level, EntitySpawnReason.COMMAND);
					try {
						java.lang.reflect.Method populate = net.minecraft.world.entity.Mob.class.getDeclaredMethod("populateDefaultEquipmentSlots", RandomSource.class, net.minecraft.world.DifficultyInstance.class);
						populate.setAccessible(true);
						populate.invoke(mob, random, difficulty);
					} catch (ReflectiveOperationException e) {
						throw new IllegalStateException(e);
					}
					Identifier held = BuiltInRegistries.ITEM.getKey(mob.getItemBySlot(EquipmentSlot.MAINHAND).getItem());
					if (held.getNamespace().equals(NAMESPACE)) armed[t]++;
				}
			}
			if (armed[0] == 0) throw new Probe.Fail("none of 300 zombies got a Basic Weapons weapon");
			if (armed[1] == 0) throw new Probe.Fail("none of 300 piglins got a Basic Weapons weapon");
			return "of 300 zombies " + armed[0] + " and of 300 piglins " + armed[1] + " were handed a Basic Weapons weapon";
		});
	}

	/** The enchantment the mod ships loaded, and its mixin lets a vanilla damage enchantment apply to a dagger. */
	private static String enchantments(Probe.Ctx c) {
		requireMod();
		return c.onServer(() -> {
			var access = c.server().registryAccess();
			var enchantments = access.lookupOrThrow(Registries.ENCHANTMENT);
			Identifier might = Identifier.fromNamespaceAndPath(NAMESPACE, "might");
			if (enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, might)).isEmpty()) throw new Probe.Fail("enchantment " + might + " did not load");
			Item dagger = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(NAMESPACE, "iron_dagger"));
			ItemStack stack = new ItemStack(dagger);
			var sharpness = enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS).value();
			if (!sharpness.canEnchant(stack)) throw new Probe.Fail("sharpness cannot be put on an iron dagger");
			return "enchantment " + might + " loaded and sharpness applies to an iron dagger";
		});
	}
}
