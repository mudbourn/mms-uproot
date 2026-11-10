package info.mudbourn.mmsuproot.probe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;

/**
 * The seven Expanded * Enchanting mods. Each hooks {@code Enchantment.canEnchant(ItemStack)} (which item an enchantment takes) and the
 * static {@code Enchantment.areCompatible(Holder, Holder)} (which pairs may share an item); what they change, with the default config
 * that sits in run/config, was read from their mixin classes. "Vanilla" below is the answer the 26.3 data alone gives: the
 * enchantment's {@code supported_items} set and its {@code exclusive_set}.
 */
final class EnchantProbes {
	private EnchantProbes() { }

	/** What one mod adds: items it unlocks enchantments on, and pairs it allows to be mixed. */
	private record Mod(String id, Item item, String[] unlocked, String[][] pairs) { }

	private static final Mod[] MODS = {
		new Mod("armor", Items.IRON_CHESTPLATE, new String[0], new String[][] {
			{"protection", "fire_protection"}, {"protection", "blast_protection"}, {"protection", "projectile_protection"}, {"fire_protection", "blast_protection"}}),
		new Mod("axe", Items.IRON_AXE, new String[] {"fire_aspect", "knockback", "looting", "impaling", "density", "breach", "wind_burst"}, new String[0][]),
		new Mod("bow", Items.BOW, new String[] {"looting", "piercing", "multishot"}, new String[][] {{"infinity", "mending"}}),
		new Mod("crossbow", Items.CROSSBOW, new String[] {"looting", "infinity", "flame", "power", "punch"}, new String[][] {{"piercing", "multishot"}}),
		new Mod("mace", Items.MACE, new String[] {"knockback", "looting", "sharpness", "impaling"}, new String[][] {{"density", "breach"}, {"density", "smite"}, {"breach", "sharpness"}}),
		new Mod("trident", Items.TRIDENT, new String[] {"fire_aspect", "knockback", "looting", "density", "breach", "wind_burst", "sharpness", "smite", "bane_of_arthropods"}, new String[][] {{"impaling", "sharpness"}, {"impaling", "smite"}}),
		new Mod("weapon", Items.IRON_SWORD, new String[] {"impaling", "density", "breach", "wind_burst"}, new String[][] {{"sharpness", "smite"}, {"sharpness", "bane_of_arthropods"}}),
	};

	static List<Probe> all() {
		List<Probe> list = new ArrayList<>();
		for (Mod mod : MODS) {
			list.add(Probe.once("enchant." + mod.id() + ".canEnchant-and-compatible", c -> check(c, mod)));
		}
		list.add(Probe.once("enchant.negative-control", EnchantProbes::control));
		for (Mod mod : MODS) {
			if (mod.unlocked().length > 0) list.add(Probe.once("enchant." + mod.id() + ".anvil-book", c -> anvil(c, mod)));
		}
		return list;
	}

	private static Holder<Enchantment> holder(Probe.Ctx c, String path) {
		return c.server().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace(path)));
	}

	private static boolean vanillaSupports(Holder<Enchantment> e, ItemStack stack) {
		return e.value().definition().supportedItems().contains(stack.typeHolder());
	}

	private static boolean vanillaCompatible(Holder<Enchantment> a, Holder<Enchantment> b) {
		return !a.equals(b) && !a.value().exclusiveSet().contains(b) && !b.value().exclusiveSet().contains(a);
	}

	private static String check(Probe.Ctx c, Mod mod) {
		ItemStack stack = new ItemStack(mod.item());
		List<String> flipped = new ArrayList<>();
		for (String path : mod.unlocked()) {
			Holder<Enchantment> e = holder(c, path);
			if (!e.value().canEnchant(stack)) throw new Probe.Fail(path + " cannot be put on " + mod.item() + " (Enchantment.canEnchant is false)");
			if (!vanillaSupports(e, stack)) flipped.add(path);
		}
		if (mod.unlocked().length > 0 && flipped.isEmpty()) throw new Probe.Fail("every expected enchantment was already supported by the vanilla data on " + mod.item() + ", so the mod changed nothing observable");
		List<String> mixed = new ArrayList<>();
		for (String[] pair : mod.pairs()) {
			Holder<Enchantment> a = holder(c, pair[0]);
			Holder<Enchantment> b = holder(c, pair[1]);
			if (!Enchantment.areCompatible(a, b) || !Enchantment.areCompatible(b, a)) throw new Probe.Fail(pair[0] + " and " + pair[1] + " are not compatible (Enchantment.areCompatible)");
			if (!vanillaCompatible(a, b)) mixed.add(pair[0] + "+" + pair[1]);
		}
		if (mod.pairs().length > 0 && mixed.isEmpty()) throw new Probe.Fail("every expected pair was already compatible in the vanilla data, so the mod changed nothing observable");
		return mod.item() + ": canEnchant true for " + mod.unlocked().length + ", flipped from vanilla false: " + flipped + "; compatible pairs flipped: " + mixed;
	}

	private static String control(Probe.Ctx c) {
		// the same two checks must stay false where no mod unlocks anything, or "true" above would prove nothing
		if (holder(c, "impaling").value().canEnchant(new ItemStack(Items.IRON_PICKAXE))) throw new Probe.Fail("impaling is allowed on a pickaxe");
		if (holder(c, "multishot").value().canEnchant(new ItemStack(Items.IRON_PICKAXE))) throw new Probe.Fail("multishot is allowed on a pickaxe");
		if (holder(c, "fire_aspect").value().canEnchant(new ItemStack(Items.BOW))) throw new Probe.Fail("fire aspect is allowed on a bow");
		if (Enchantment.areCompatible(holder(c, "sharpness"), holder(c, "mending")) != vanillaCompatible(holder(c, "sharpness"), holder(c, "mending"))) throw new Probe.Fail("sharpness/mending differs from vanilla");
		if (Enchantment.areCompatible(holder(c, "infinity"), holder(c, "sharpness")) != vanillaCompatible(holder(c, "infinity"), holder(c, "sharpness"))) throw new Probe.Fail("infinity/sharpness differs from vanilla");
		return "pickaxe takes no impaling, bow takes no fire aspect, unrelated pairs match vanilla";
	}

	/** An anvil combine of an item with a book of the first enchantment the mod unlocks. */
	private static String anvil(Probe.Ctx c, Mod mod) {
		String path = mod.unlocked()[0];
		return c.onServer(() -> {
			ServerPlayer player = c.server().getPlayerList().getPlayers().get(0);
			GameType before = player.gameMode();
			// creative mode lets an anvil put anything on anything
			player.setGameMode(GameType.SURVIVAL);
			try {
				Holder<Enchantment> e = holder(c, path);
				ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
				ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
				stored.set(e, 1);
				book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
				ItemStack result = combine(player, new ItemStack(mod.item()), book);
				int level = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(e);
				if (result.isEmpty() || level != 1) throw new Probe.Fail("anvil: " + mod.item() + " + book of " + path + " gave " + result + " with level " + level);
				// and the same book on a pickaxe, which no mod unlocks
				ItemStack none = combine(player, new ItemStack(Items.IRON_PICKAXE), book);
				if (none.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(e) != 0) throw new Probe.Fail("anvil put " + path + " on a pickaxe");
				return "anvil: " + mod.item() + " + book of " + path + " I -> level 1; the same book on a pickaxe adds nothing";
			} finally {
				player.setGameMode(before);
			}
		});
	}

	private static ItemStack combine(ServerPlayer player, ItemStack left, ItemStack right) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(left);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(right);
		menu.createResult();
		ItemStack out = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().copy();
		// hand the inputs back so nothing lands in the player's inventory
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(ItemStack.EMPTY);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(ItemStack.EMPTY);
		return out;
	}
}
