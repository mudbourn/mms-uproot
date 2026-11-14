package info.mudbourn.mmsuproot.shim.feature;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The shape of 1.21.11's {@code Feature}: a class with its own settings type, placing through a
 * context. 26.3 made {@code Feature} an interface that is also its own settings, so a mod's feature
 * classes extend this instead, and {@link LegacyRegistries} wraps them for the real registry.
 *
 * <p>The constants stand for vanilla types a mod names only to build settings that the game never
 * reads (it reads the mod's JSON). They exist so that class initialisation finds the fields; they place nothing.
 */
public abstract class LegacyFeature<FC extends FeatureConfiguration> {
	public static final LegacyFeature<?> RANDOM_PATCH = inert();
	public static final LegacyFeature<?> FLOWER = inert();
	public static final LegacyFeature<?> SIMPLE_BLOCK = inert();
	public static final LegacyFeature<?> RANDOM_SELECTOR = inert();
	public static final LegacyFeature<?> SIMPLE_RANDOM_SELECTOR = inert();
	public static final LegacyFeature<?> FOREST_ROCK = inert();
	public static final LegacyFeature<?> DISK = inert();
	public static final LegacyFeature<?> DELTA_FEATURE = inert();
	public static final LegacyFeature<?> BASALT_COLUMNS = inert();
	public static final LegacyFeature<?> FALLEN_TREE = inert();

	private final Codec<FC> configCodec;

	public LegacyFeature(Codec<FC> configCodec) {
		this.configCodec = configCodec;
	}

	public abstract boolean place(FeaturePlaceContext<FC> context);

	Codec<FC> configCodec() {
		return configCodec;
	}

	protected void setBlock(LevelWriter level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 3);
	}

	// 1.21.11's Feature helpers, which 26.3 dropped. Each body follows the 1.21.11 bytecode.

	protected void safeSetBlock(WorldGenLevel level, BlockPos pos, BlockState state, Predicate<BlockState> canReplace) {
		if (canReplace.test(level.getBlockState(pos))) {
			level.setBlock(pos, state, 2);
		}
	}

	protected void markAboveForPostProcessing(WorldGenLevel level, BlockPos pos) {
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int i = 0; i < 2; i++) {
			cursor.move(Direction.UP);
			if (level.getBlockState(cursor).isAir()) {
				return;
			}
			level.getChunk(cursor).markPosForPostProcessing(cursor);
		}
	}

	public static Predicate<BlockState> isReplaceable(TagKey<Block> cannotReplace) {
		return state -> !state.is(cannotReplace);
	}

	protected static boolean isStone(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD);
	}

	public static boolean isDirt(BlockState state) {
		return state.is(BlockTags.DIRT);
	}

	public static boolean isGrassOrDirt(LevelSimulatedReader level, BlockPos pos) {
		return level.isStateAtPosition(pos, LegacyFeature::isDirt);
	}

	public static boolean checkNeighbors(Function<BlockPos, BlockState> getState, BlockPos pos, Predicate<BlockState> test) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (Direction direction : Direction.values()) {
			cursor.setWithOffset(pos, direction);
			if (test.test(getState.apply(cursor))) {
				return true;
			}
		}
		return false;
	}

	public static boolean isAdjacentToAir(Function<BlockPos, BlockState> getState, BlockPos pos) {
		return checkNeighbors(getState, pos, BlockState::isAir);
	}

	private static LegacyFeature<?> inert() {
		return new LegacyFeature<NoneFeatureConfiguration>(NoneFeatureConfiguration.CODEC) {
			@Override
			public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
				return false;
			}
		};
	}
}
