package info.mudbourn.mmsuproot.shim.item;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Armor and tool materials of 1.21.1, which the 1.21.2 item rework replaced: an {@code ArmorMaterial} that a registry held and an
 * item took as a {@code Holder}, with its textures as layers and its repair ingredient as a supplier; a {@code Tier} that was an interface.
 * 26.3 has {@link ArmorMaterial} as a plain record (a durability multiplier, a repair tag and an equipment asset in place of the layers) and
 * {@link ToolMaterial} for tiers. Spell Engine's RPG series API already speaks those, so what a 1.21.1 mod passes to it is converted here:
 * the rules in {@code renames-1211.txt} send the constructor and the calls that carry a material to these methods.
 *
 * <p>Spell Engine is reached through method handles so Uproot is not built against it; a mod that calls these has it.
 */
public final class LegacyArmor {
	private LegacyArmor() { }

	/** {@code ArmorMaterial.Layer(ResourceLocation id, String suffix, boolean dyeable)}: the texture a material's armor wore. Only the id decides the equipment asset now. */
	public record Layer(Identifier id, String suffix, boolean dyeable) {
		public Layer(Identifier id) {
			this(id, "", false);
		}
	}

	/** {@code BuiltInRegistries.ARMOR_MATERIAL}: 26.3 has no registry of materials, so a mod's registrations land in one of Uproot's own, which nothing else reads. */
	public static final Registry<ArmorMaterial> MATERIALS = new MappedRegistry<>(
			ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("mms_uproot", "armor_material")), Lifecycle.stable());

	/**
	 * The arguments of the 26.3 {@code ArmorMaterial} constructor for a 1.21.1 one. A 1.21.1 material had no durability (the item took a multiplier
	 * from its set; {@link #create} puts it in), and its textures were layers, of which the first names the equipment asset.
	 */
	public static Object[] materialArgs(Map<?, ?> defense, int enchantability, Holder<SoundEvent> equipSound, Supplier<Ingredient> repair,
			List<?> layers, float toughness, float knockbackResistance) {
		Identifier asset = layers.isEmpty() ? Identifier.withDefaultNamespace("leather") : ((Layer) layers.get(0)).id();
		info.mudbourn.mmsuproot.data.Legacy1211Data.registerAsset(asset);
		// the ingredient is a supplier of items that may not be registered yet, and a tag is data: repair by what a vanilla armor of the same weight takes
		TagKey<Item> repairTag = ItemTags.REPAIRS_LEATHER_ARMOR;
		return new Object[] {1, defense, enchantability, equipSound, toughness, knockbackResistance, repairTag,
				ResourceKey.create(EquipmentAssets.ROOT_ID, asset)};
	}

	/** The arguments of the 26.3 {@code Armor.CustomItem} constructor for a 1.21.1 one, which took the material as a holder. */
	@SuppressWarnings("unchecked")
	public static Object[] customItemArgs(Holder<?> material, ArmorType type, Item.Properties properties) {
		return new Object[] {((Holder<ArmorMaterial>) material).value(), type, properties};
	}

	/** A material as the holder a 1.21.1 mod keeps it in, for the factory a set makes its pieces with. */
	public static Holder<ArmorMaterial> holder(ArmorMaterial material) {
		return Holder.direct(material);
	}

	// ---- Spell Engine

	private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
	private static MethodHandle entryCreate;
	private static MethodHandle customMatching;

	private static Class<?> spell(String name) {
		try {
			return Class.forName(name, false, LegacyArmor.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("Uproot needs Spell Engine for " + name, e);
		}
	}

	/**
	 * {@code Armor.Entry.create(Holder, Identifier, int, ItemFactory, ArmorSetConfig, LootProperties, ItemSettingsTweaker)}: Spell Engine takes the material
	 * itself, and the durability multiplier the old call passed belongs in it, because the item takes its durability from there.
	 */
	public static Object createEntry(Holder<?> material, Identifier id, int durability, Object factory, Object config, Object loot, Object tweaker) {
		try {
			if (entryCreate == null) {
				Class<?> entry = spell("net.spell_engine.rpg_series.item.Armor$Entry");
				entryCreate = LOOKUP.findStatic(entry, "create", MethodType.methodType(entry, ArmorMaterial.class, Identifier.class, int.class,
						spell("net.spell_engine.rpg_series.item.Armor$Set$ItemFactory"), spell("net.spell_engine.rpg_series.config.ArmorSetConfig"),
						spell("net.spell_engine.rpg_series.item.Equipment$LootProperties"), spell("net.spell_engine.rpg_series.item.Armor$ItemSettingsTweaker")));
			}
			ArmorMaterial m = (ArmorMaterial) material.value();
			ArmorMaterial sized = new ArmorMaterial(durability, m.defense(), m.enchantmentValue(), m.equipSound(), m.toughness(), m.knockbackResistance(), m.repairIngredient(), m.assetId());
			return entryCreate.invoke(sized, id, durability, factory, config, loot, tweaker);
		} catch (Throwable t) {
			throw new IllegalStateException("Could not register the armor set " + id, t);
		}
	}

	/** {@code Weapon.CustomMaterial.matching(Tier, Supplier<Ingredient>)}: Spell Engine repairs by the tool material's own tag. */
	public static Object matching(ToolMaterial tier, Supplier<?> repair) {
		try {
			if (customMatching == null) {
				Class<?> custom = spell("net.spell_engine.rpg_series.item.Weapon$CustomMaterial");
				customMatching = LOOKUP.findStatic(custom, "matching", MethodType.methodType(custom, ToolMaterial.class, TagKey.class));
			}
			return customMatching.invoke(tier, tier.repairItems());
		} catch (Throwable t) {
			throw new IllegalStateException("Could not make a weapon material", t);
		}
	}
}
