package info.mudbourn.mmsuproot.shim.surface;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

/**
 * Stand-in for 1.21.11's {@code SurfaceRules}, which 26.3 replaced with material rules. The builders
 * keep their names and descriptors and build a small tree that is converted to
 * {@link MaterialRule}s once a biome lookup exists ({@link #toMaterial(RuleSource, HolderGetter)}),
 * because {@code isBiome} needs one and a mod builds its rules long before the registries do.
 *
 * <p>A mod's own rule source (an implementation of {@link RuleSource}) is carried through as it is:
 * it is called on a stand-in {@link Context} at compile time, and the tree nodes it holds answer the
 * same call by compiling themselves against the same context.
 */
public final class SurfaceRules {
	/** One block rule. Nodes of the stand-in tree and a mod's own sources both implement it. */
	public interface RuleSource extends Function<Context, SurfaceRule> {
		Codec<RuleSource> CODEC = Codecs.unsupported("surface rule source");

		KeyDispatchDataCodec<? extends RuleSource> codec();
	}

	/** One condition. */
	public interface ConditionSource extends Function<Context, Condition> {
		Codec<ConditionSource> CODEC = Codecs.unsupported("surface condition source");

		KeyDispatchDataCodec<? extends ConditionSource> codec();
	}

	/** A compiled rule: the block for a position, or null when the rule does not apply. */
	@FunctionalInterface
	public interface SurfaceRule {
		BlockState tryApply(int x, int y, int z);
	}

	/** A compiled condition. */
	@FunctionalInterface
	public interface Condition {
		boolean test();
	}

	/**
	 * What a rule source sees while it compiles. 1.21.11's context carried much more; a source gets the
	 * surface system (for its seeded random) and, for the nodes it holds, the 26.3 context to compile against.
	 */
	public static final class Context {
		public final SurfaceSystem system;
		final MaterialRuleContext material;
		final HolderGetter<Biome> biomes;

		Context(SurfaceSystem system, MaterialRuleContext material, HolderGetter<Biome> biomes) {
			this.system = system;
			this.material = material;
			this.biomes = biomes;
		}
	}

	private SurfaceRules() { }

	/** The 26.3 rule for a source. A mod's own source is wrapped so that it compiles on a stand-in context. */
	public static MaterialRule toMaterial(RuleSource source, HolderGetter<Biome> biomes) {
		if (source instanceof LazyRule node) return node.material(biomes);
		return new LegacyRule(source, biomes);
	}

	static MaterialCondition toMaterial(ConditionSource source, HolderGetter<Biome> biomes) {
		if (source instanceof LazyCondition node) return node.material(biomes);
		throw new UnsupportedOperationException("A condition source of a mod's own cannot be converted: " + source.getClass().getName());
	}

	// --- builders, same names and descriptors as 1.21.11 ---

	public static final ConditionSource ON_FLOOR = stoneDepthCheck(0, false, CaveSurface.FLOOR);
	public static final ConditionSource UNDER_FLOOR = stoneDepthCheck(0, true, CaveSurface.FLOOR);
	public static final ConditionSource DEEP_UNDER_FLOOR = stoneDepthCheck(0, true, 6, CaveSurface.FLOOR);
	public static final ConditionSource VERY_DEEP_UNDER_FLOOR = stoneDepthCheck(0, true, 30, CaveSurface.FLOOR);
	public static final ConditionSource ON_CEILING = stoneDepthCheck(0, false, CaveSurface.CEILING);
	public static final ConditionSource UNDER_CEILING = stoneDepthCheck(0, true, CaveSurface.CEILING);

	public static RuleSource sequence(RuleSource... rules) {
		List<RuleSource> list = List.of(rules);
		return new LazyRule(g -> MaterialRules.sequence(list.stream().map(r -> toMaterial(r, g)).toList()));
	}

	public static RuleSource ifTrue(ConditionSource condition, RuleSource then) {
		return new LazyRule(g -> MaterialRules.ifTrue(toMaterial(condition, g), toMaterial(then, g)));
	}

	public static RuleSource state(BlockState state) {
		return new LazyRule(g -> MaterialRules.state(state));
	}

	public static RuleSource bandlands() {
		return new LazyRule(g -> MaterialRules.bandlands());
	}

	public static ConditionSource not(ConditionSource condition) {
		return new LazyCondition(g -> MaterialRules.not(toMaterial(condition, g)));
	}

	@SafeVarargs
	public static ConditionSource isBiome(ResourceKey<Biome>... biomes) {
		return new LazyCondition(g -> MaterialRules.isBiome(g, biomes));
	}

	public static ConditionSource isBiome(List<ResourceKey<Biome>> biomes) {
		@SuppressWarnings("unchecked")
		ResourceKey<Biome>[] keys = biomes.toArray(new ResourceKey[0]);
		return new LazyCondition(g -> MaterialRules.isBiome(g, keys));
	}

	/** 1.21.11's noise condition always sampled in two dimensions; the upper bound defaults to unbounded, as in vanilla's data. */
	public static ConditionSource noiseCondition(ResourceKey<?> noise, double min) {
		return noiseCondition(noise, min, Double.MAX_VALUE);
	}

	@SuppressWarnings("unchecked")
	public static ConditionSource noiseCondition(ResourceKey<?> noise, double min, double max) {
		ResourceKey<net.minecraft.world.level.levelgen.synth.NormalNoise> key = (ResourceKey<net.minecraft.world.level.levelgen.synth.NormalNoise>) noise;
		return new LazyCondition(g -> MaterialRules.noiseCondition2d(key, min, max));
	}

	public static ConditionSource verticalGradient(String name, VerticalAnchor trueAtAndBelow, VerticalAnchor falseAtAndAbove) {
		return new LazyCondition(g -> MaterialRules.verticalGradient(name, trueAtAndBelow, falseAtAndAbove));
	}

	public static ConditionSource yBlockCheck(VerticalAnchor anchor, int surfaceDepthMultiplier) {
		return new LazyCondition(g -> MaterialRules.yBlockCheck(anchor, surfaceDepthMultiplier));
	}

	public static ConditionSource yStartCheck(VerticalAnchor anchor, int surfaceDepthMultiplier) {
		return new LazyCondition(g -> MaterialRules.yStartCheck(anchor, surfaceDepthMultiplier));
	}

	public static ConditionSource waterBlockCheck(int offset, int surfaceDepthMultiplier) {
		return new LazyCondition(g -> MaterialRules.waterBlockCheck(offset, surfaceDepthMultiplier));
	}

	public static ConditionSource waterStartCheck(int offset, int surfaceDepthMultiplier) {
		return new LazyCondition(g -> MaterialRules.waterStartCheck(offset, surfaceDepthMultiplier));
	}

	public static ConditionSource stoneDepthCheck(int offset, boolean addSurfaceDepth, CaveSurface surface) {
		return new LazyCondition(g -> MaterialRules.stoneDepthCheck(offset, addSurfaceDepth, surface));
	}

	public static ConditionSource stoneDepthCheck(int offset, boolean addSurfaceDepth, int secondaryDepthRange, CaveSurface surface) {
		return new LazyCondition(g -> MaterialRules.stoneDepthCheck(offset, addSurfaceDepth, secondaryDepthRange, surface));
	}

	public static ConditionSource steep() {
		return new LazyCondition(g -> MaterialRules.steep());
	}

	public static ConditionSource hole() {
		return new LazyCondition(g -> MaterialRules.hole());
	}

	public static ConditionSource abovePreliminarySurface() {
		return new LazyCondition(g -> MaterialRules.abovePreliminarySurface());
	}

	public static ConditionSource temperature() {
		return new LazyCondition(g -> MaterialRules.temperature());
	}

	// --- the tree ---

	/** A node of the tree: builds its 26.3 rule once per biome lookup, and compiles itself when a source calls it. */
	static final class LazyRule implements RuleSource {
		private final Function<HolderGetter<Biome>, MaterialRule> build;
		private HolderGetter<Biome> builtFor;
		private MaterialRule built;

		LazyRule(Function<HolderGetter<Biome>, MaterialRule> build) {
			this.build = build;
		}

		synchronized MaterialRule material(HolderGetter<Biome> biomes) {
			if (built == null || builtFor != biomes) {
				built = build.apply(biomes);
				builtFor = biomes;
			}
			return built;
		}

		@Override
		public SurfaceRule apply(Context context) {
			RuleEvaluator evaluator = material(context.biomes).compile(context.material);
			return evaluator::tryApply;
		}

		@Override
		public KeyDispatchDataCodec<? extends RuleSource> codec() {
			return Codecs.UNSUPPORTED_RULE;
		}
	}

	/** The same for conditions. */
	static final class LazyCondition implements ConditionSource {
		private final Function<HolderGetter<Biome>, MaterialCondition> build;
		private HolderGetter<Biome> builtFor;
		private MaterialCondition built;

		LazyCondition(Function<HolderGetter<Biome>, MaterialCondition> build) {
			this.build = build;
		}

		synchronized MaterialCondition material(HolderGetter<Biome> biomes) {
			if (built == null || builtFor != biomes) {
				built = build.apply(biomes);
				builtFor = biomes;
			}
			return built;
		}

		@Override
		public Condition apply(Context context) {
			ConditionEvaluator evaluator = material(context.biomes).compile(context.material);
			return evaluator::test;
		}

		@Override
		public KeyDispatchDataCodec<? extends ConditionSource> codec() {
			return Codecs.UNSUPPORTED_CONDITION;
		}
	}

	/**
	 * Codecs for things that exist only in memory. They fail on use instead of on construction, because a
	 * mod builds codecs for its own sources in static initialisers and nothing here is ever read from data.
	 */
	static final class Codecs {
		static final KeyDispatchDataCodec<RuleSource> UNSUPPORTED_RULE = KeyDispatchDataCodec.of(map("surface rule source"));
		static final KeyDispatchDataCodec<ConditionSource> UNSUPPORTED_CONDITION = KeyDispatchDataCodec.of(map("surface condition source"));

		private Codecs() { }

		static <A> Codec<A> unsupported(String what) {
			return Codecs.<A>map(what).codec();
		}

		static <A> MapCodec<A> map(String what) {
			return new MapCodec<>() {
				@Override
				public <T> Stream<T> keys(DynamicOps<T> ops) {
					return Stream.empty();
				}

				@Override
				public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
					return DataResult.error(() -> "A " + what + " of a 1.21.11 mod cannot be read from data");
				}

				@Override
				public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
					return prefix.withErrorsFrom(DataResult.error(() -> "A " + what + " of a 1.21.11 mod cannot be written to data"));
				}
			};
		}
	}
}
