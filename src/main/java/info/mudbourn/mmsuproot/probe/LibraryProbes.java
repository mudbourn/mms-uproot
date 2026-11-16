package info.mudbourn.mmsuproot.probe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import info.mudbourn.mmsuproot.shim.client.LegacyClient;
import info.mudbourn.mmsuproot.shim.compat.LegacyStacks;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** The shims behind the gaps of four libraries that mods carry inside them (Kielson's API, Immersive Storms, Terraform, MCEF). */
final class LibraryProbes {
	private LibraryProbes() { }

	static List<Probe> all() {
		return List.of(
			Probe.once("libs.charged-projectiles", LibraryProbes::charged),
			Probe.once("libs.sky-color", LibraryProbes::sky),
			Probe.once("libs.terraform-leaves", LibraryProbes::leaves));
	}

	/** getItems() hands out copies of the stacks, and of(List) takes a list of stacks (an empty one is left out, which the lossy list says). */
	private static String charged(Probe.Ctx c) {
		ItemStack arrow = new ItemStack(Items.ARROW, 3);
		ChargedProjectiles projectiles = LegacyStacks.chargedProjectilesOf(List.of(arrow, ItemStack.EMPTY, new ItemStack(Items.FIREWORK_ROCKET)));
		List<ItemStack> items = LegacyStacks.chargedItems(projectiles);
		if (items.size() != 2) throw new Probe.Fail("two stacks went in (one empty), " + items.size() + " came out");
		if (!items.get(0).is(Items.ARROW) || items.get(0).getCount() != 3) throw new Probe.Fail("the first stack came out as " + items.get(0));
		items.get(0).setCount(1);
		if (LegacyStacks.chargedItems(projectiles).get(0).getCount() != 3) throw new Probe.Fail("a stack taken from the component changed it");
		return "of(List) and getItems() round-trip the stacks, as copies; an empty stack is left out";
	}

	/** The sky colour int and the colour vector of a sky render state turn into each other. */
	private static String sky(Probe.Ctx c) {
		SkyRenderState state = new SkyRenderState();
		LegacyClient.setSkyColor(state, 0x336699);
		if (Math.abs(state.skyColor.x() - 0x33 / 255.0F) > 1e-6 || Math.abs(state.skyColor.y() - 0x66 / 255.0F) > 1e-6 || Math.abs(state.skyColor.z() - 0x99 / 255.0F) > 1e-6) {
			throw new Probe.Fail("the vector is " + state.skyColor);
		}
		int back = LegacyClient.skyColor(state) & 0xFFFFFF;
		if (back != 0x336699) throw new Probe.Fail("the colour came back as " + Integer.toHexString(back));
		return "skyColor reads and writes the RGB int through the colour vector";
	}

	/** Terraform's leaf classes extend the game's falling-particle leaves, and their constructor gives the chance to it. */
	private static String leaves(Probe.Ctx c) throws Exception {
		if (!FabricLoader.getInstance().isModLoaded("terraform-wood-api-v1")) throw new Probe.Skip("Terraform is not installed");
		ClassLoader loader = LibraryProbes.class.getClassLoader();
		Class<?> extended = Class.forName("com.terraformersmc.terraform.leaves.api.block.ExtendedLeavesBlock", true, loader);
		Class<?> colored = Class.forName("com.terraformersmc.terraform.leaves.api.block.ColoredParticleLeavesBlock", true, loader);
		if (!FallingParticlesLeavesBlock.class.isAssignableFrom(extended)) throw new Probe.Fail("ExtendedLeavesBlock does not extend FallingParticlesLeavesBlock");
		if (!FallingParticlesLeavesBlock.class.isAssignableFrom(colored)) throw new Probe.Fail("ColoredParticleLeavesBlock does not extend FallingParticlesLeavesBlock");
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, Identifier.parse("uproot_probe:leaves")));
		Constructor<?> ctor = extended.getConstructor(float.class, Optional.class, boolean.class, boolean.class, BlockBehaviour.Properties.class);
		Object block;
		try {
			block = ctor.newInstance(0.25F, Optional.empty(), false, true, properties);
		} catch (java.lang.reflect.InvocationTargetException e) {
			// a block cannot be made once the registries are frozen (26.3 gives it an intrusive holder in Block's constructor); the trace
			// shows the constructor chain got that far, through the game's falling-particle constructor
			Throwable cause = e.getCause();
			boolean viaParent = cause != null && java.util.Arrays.stream(cause.getStackTrace())
				.anyMatch(f -> f.getClassName().equals(FallingParticlesLeavesBlock.class.getName()) && f.getMethodName().equals("<init>"));
			if (viaParent && cause instanceof IllegalStateException) return "Terraform's leaves extend FallingParticlesLeavesBlock and their constructor reaches its constructor (Block's constructor then refuses: \"" + cause.getMessage() + "\")";
			throw e;
		}
		Field chance = FallingParticlesLeavesBlock.class.getDeclaredField("leafParticleChance");
		chance.setAccessible(true);
		if (chance.getFloat(block) != 0.25F) throw new Probe.Fail("the particle chance is " + chance.getFloat(block));
		return "Terraform's leaves extend FallingParticlesLeavesBlock and pass it their particle chance";
	}
}
