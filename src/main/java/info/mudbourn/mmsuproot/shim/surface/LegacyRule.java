package info.mudbourn.mmsuproot.shim.surface;

import com.mojang.serialization.MapCodec;
import info.mudbourn.mmsuproot.mixin.MaterialRuleContextAccess;
import info.mudbourn.mmsuproot.shim.UprootIds;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

/**
 * A rule source that a mod wrote itself, as a material rule. Compiling it builds the stand-in
 * context around the 26.3 one and lets the source compile there, so what it asks of the surface
 * system (its seeded random) is answered and the rules it holds compile against the real context.
 * It exists in memory only; the codec is registered so the registry holds an entry for the type.
 */
public record LegacyRule(SurfaceRules.RuleSource source, HolderGetter<Biome> biomes) implements MaterialRule {
	private static final Identifier SURFACE = Identifier.withDefaultNamespace("surface");

	/** Never reads or writes data: the rule is made by a mod's code, not by a data pack. */
	public static final MapCodec<LegacyRule> CODEC = SurfaceRules.Codecs.map("surface rule source");

	/** Registers the codec so the type has an id. Call while registries are still open. */
	public static void register() {
		Registry.register(BuiltInRegistries.MATERIAL_RULE_TYPE, UprootIds.id("legacy_surface_rule"), CODEC);
	}

	@Override
	public RuleEvaluator compile(MaterialRuleContext context) {
		RandomState random = ((MaterialRuleContextAccess) (Object) context).mms_uproot$randomState();
		SurfaceSystem system = new SurfaceSystem(random.getOrCreateRandomFactory(SURFACE));
		SurfaceRules.SurfaceRule rule = source.apply(new SurfaceRules.Context(system, context, biomes));
		return rule::tryApply;
	}

	@Override
	public MapCodec<? extends MaterialRule> codec() {
		return CODEC;
	}
}
