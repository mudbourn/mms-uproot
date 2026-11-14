package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;

/** Scorchful (1.21.11 build): its data on Thermoo's registries, the heat on a hot player, the sand cauldron and the enchantments it edits. */
final class ScorchfulProbes {
	private static final String NAMESPACE = "scorchful";
	private static final String AWARE = "com.github.thedeathlycow.thermoo.api.temperature.TemperatureAware";

	private ScorchfulProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("scorchful.data-registries", ScorchfulProbes::registries),
			Probe.once("scorchful.sand-cauldron-fills", ScorchfulProbes::sandCauldron),
			Probe.once("scorchful.enchantment-events", ScorchfulProbes::enchantments),
			Probe.steps("scorchful.heat-weakens-player", 300, ScorchfulProbes::heat));
	}

	private static void requireMod() {
		if (!FabricLoader.getInstance().isModLoaded(NAMESPACE)) throw new Probe.Skip("Scorchful is not installed");
	}

	/** The world loaded Scorchful's predicates and, in Thermoo's registries, its temperature effects, environments and providers. */
	private static String registries(Probe.Ctx c) {
		requireMod();
		return c.onServer(() -> {
			var access = c.server().registryAccess();
			List<String> found = new ArrayList<>();
			access.registries().forEach(entry -> {
				if (!entry.key().identifier().getNamespace().equals("thermoo")) return;
				long own = entry.value().keySet().stream().filter(id -> id.getNamespace().equals(NAMESPACE)).count();
				if (own > 0) found.add(entry.key().identifier().getPath() + "=" + own);
			});
			for (String needed : List.of("temperature_effect", "environment", "environment_provider")) {
				if (found.stream().noneMatch(f -> f.startsWith(needed + "="))) throw new Probe.Fail("Thermoo's " + needed + " registry holds nothing of Scorchful (found " + found + ")");
			}
			var predicates = c.server().reloadableRegistries().lookup().lookupOrThrow(Registries.PREDICATE);
			for (String name : List.of("can_heat", "benefits_from_heat", "is_heat_stroke_affected")) {
				if (predicates.get(ResourceKey.create(Registries.PREDICATE, Identifier.fromNamespaceAndPath(NAMESPACE, name))).isEmpty()) throw new Probe.Fail("predicate " + name + " did not load");
			}
			return "Thermoo registries hold Scorchful entries " + found + "; its three predicates loaded";
		});
	}

	/**
	 * Sand used on an empty cauldron fills Scorchful's sand cauldron (its interaction goes in the vanilla table of empty cauldrons, which 26.3
	 * keeps as a dispatcher), and the new cauldron takes the sand out again with an empty hand's bucket-less use as vanilla's does.
	 */
	private static String sandCauldron(Probe.Ctx c) {
		requireMod();
		return c.onServer(() -> {
			ServerPlayer player = c.serverPlayer();
			var level = player.level();
			BlockPos pos = player.blockPosition().above(4);
			BlockState before = level.getBlockState(pos);
			try {
				level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
				ItemStack sand = new ItemStack(Items.SAND, 4);
				player.setItemInHand(InteractionHand.MAIN_HAND, sand);
				var result = level.getBlockState(pos).useItemOn(sand, level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
				Identifier now = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
				Identifier expected = Identifier.fromNamespaceAndPath(NAMESPACE, "sand_cauldron");
				if (!now.equals(expected)) throw new Probe.Fail("using sand on an empty cauldron left " + now + " (result " + result + "), not " + expected);
				return "sand on an empty cauldron made " + now + " (" + level.getBlockState(pos) + "), " + sand.getCount() + " of 4 sand left";
			} finally {
				level.setBlockAndUpdate(pos, before);
			}
		});
	}

	/** Scorchful's listeners on Fabric's enchantment modify event ran: impaling and fire protection gained effects they do not have in vanilla. */
	private static String enchantments(Probe.Ctx c) {
		requireMod();
		return c.onServer(() -> {
			var enchantments = c.server().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			var impaling = enchantments.getOrThrow(Enchantments.IMPALING).value();
			var fireProtection = enchantments.getOrThrow(Enchantments.FIRE_PROTECTION).value();
			var damage = impaling.getEffects(EnchantmentEffectComponents.DAMAGE);
			var attributes = fireProtection.getEffects(EnchantmentEffectComponents.ATTRIBUTES);
			if (damage.size() < 2) throw new Probe.Fail("impaling has " + damage.size() + " damage effect(s), no more than vanilla's one");
			if (attributes.isEmpty()) throw new Probe.Fail("fire protection has no attribute effect (the heat resistance Scorchful adds)");
			return "impaling has " + damage.size() + " damage effects and fire protection " + attributes.size() + " attribute effect(s)";
		});
	}

	/**
	 * With Thermoo's temperature at its maximum, Scorchful's temperature effect for a hot player (weakness from half way up the scale) runs on the
	 * server and gives the player the effect; at a neutral temperature it does not.
	 */
	private static String heat(Probe.Ctx c, int tick) throws Exception {
		requireMod();
		Class<?> aware = Class.forName(AWARE, true, ScorchfulProbes.class.getClassLoader());
		Method set = aware.getMethod("thermoo$setTemperature", int.class);
		Method max = aware.getMethod("thermoo$getMaxTemperature");
		ServerPlayer player = c.serverPlayer();
		if (tick == 1) {
			c.onServer(() -> {
				player.removeAllEffects();
				try {
					set.invoke(player, 0);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
				c.server().getCommands().performPrefixedCommand(c.server().createCommandSourceStack(), "gamemode survival @p");
			});
			return null;
		}
		if (tick == 40) {
			boolean weak = c.onServer(() -> player.hasEffect(MobEffects.WEAKNESS));
			if (weak) throw new Probe.Fail("the player is weak at a neutral temperature");
			c.onServer(() -> {
				try {
					set.invoke(player, max.invoke(player));
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			});
			return null;
		}
		if (tick > 40 && c.onServer(() -> player.hasEffect(MobEffects.WEAKNESS))) {
			c.onServer(() -> {
				player.removeAllEffects();
				try {
					set.invoke(player, 0);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			});
			return "at maximum temperature the player got weakness " + (tick - 40) + " ticks later; at a neutral one it had none";
		}
		return null;
	}
}
