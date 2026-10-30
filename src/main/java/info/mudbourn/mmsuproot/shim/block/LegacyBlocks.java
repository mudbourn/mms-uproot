package info.mudbourn.mmsuproot.shim.block;

import java.util.function.Function;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

/**
 * Small pieces of block API that 1.21.11 mods call and 26.3 removed or made private: the codec
 * factory, the grass spreading checks, constants and the bone meal call.
 */
public final class LegacyBlocks {
	/** What 26.3's leaf blocks use for "no ambient sound"; its own field is private. */
	public static final AmbientLeavesBlockSoundPlayer NO_AMBIENT_SOUND = AmbientLeavesBlockSoundPlayer.noAmbientSound();

	/** 26.3 keeps terracotta in a per-colour collection; 1.21.11 had one constant each. */
	public static final Block ORANGE_TERRACOTTA = Blocks.DYED_TERRACOTTA.orange();
	public static final Block WHITE_TERRACOTTA = Blocks.DYED_TERRACOTTA.white();
	public static final Block LIGHT_GRAY_TERRACOTTA = Blocks.DYED_TERRACOTTA.lightGray();

	/** Likewise wool: 26.3 keeps it in a per-colour collection. */
	public static final Block WHITE_WOOL = Blocks.WOOL.white();
	public static final Block ORANGE_WOOL = Blocks.WOOL.orange();
	public static final Block MAGENTA_WOOL = Blocks.WOOL.magenta();
	public static final Block LIGHT_BLUE_WOOL = Blocks.WOOL.lightBlue();
	public static final Block YELLOW_WOOL = Blocks.WOOL.yellow();
	public static final Block LIME_WOOL = Blocks.WOOL.lime();
	public static final Block PINK_WOOL = Blocks.WOOL.pink();
	public static final Block GRAY_WOOL = Blocks.WOOL.gray();
	public static final Block LIGHT_GRAY_WOOL = Blocks.WOOL.lightGray();
	public static final Block CYAN_WOOL = Blocks.WOOL.cyan();
	public static final Block PURPLE_WOOL = Blocks.WOOL.purple();
	public static final Block BLUE_WOOL = Blocks.WOOL.blue();
	public static final Block BROWN_WOOL = Blocks.WOOL.brown();
	public static final Block GREEN_WOOL = Blocks.WOOL.green();
	public static final Block RED_WOOL = Blocks.WOOL.red();
	public static final Block BLACK_WOOL = Blocks.WOOL.black();

	private LegacyBlocks() { }

	/**
	 * 1.21.11 blocks carried a codec and built it with {@code simpleCodec}. 26.3 blocks have none, so
	 * nothing reads the codec a mod keeps; this returns a codec that decodes to nothing so the field can be set.
	 */
	public static <B extends Block> MapCodec<B> simpleCodec(Function<BlockBehaviour.Properties, B> factory) {
		return MapCodec.unit(() -> null);
	}

	/**
	 * {@code BlockBehaviour.propertiesCodec()}: the codec field a mod's {@code simpleCodec} replacement
	 * builds. Nothing reads it, so it gives plain properties and decodes nothing.
	 */
	public static <B extends Block> RecordCodecBuilder<B, BlockBehaviour.Properties> propertiesCodec() {
		return RecordCodecBuilder.point(BlockBehaviour.Properties.of());
	}

	/** {@code SpreadingSnowyDirtBlock.canBeGrass}: 26.3 made the same check private as {@code canStayAlive}. */
	public static boolean canBeGrass(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos above = pos.above();
		BlockState aboveState = level.getBlockState(above);
		if (aboveState.is(Blocks.SNOW) && aboveState.getValue(SnowLayerBlock.LAYERS) == 1) {
			return true;
		}
		if (aboveState.getFluidState().isFull()) {
			return false;
		}
		return LightEngine.getLightDampeningInto(state, aboveState, Direction.UP, aboveState.getLightDampening()) < 15;
	}

	/** {@code SpreadingSnowyDirtBlock.canPropagate}, likewise private in 26.3. */
	public static boolean canPropagate(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos above = pos.above();
		return canBeGrass(state, level, pos) && !level.getFluidState(above).is(FluidTags.WATER);
	}

	/**
	 * 1.21.11's four-argument bone meal call. 26.3 asks who is using the bone meal; a mod calling
	 * the old form is a plain use, so this reports an interaction.
	 */
	public static void performBonemeal(BonemealableBlock block, ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
		block.performBonemeal(level, random, pos, state, BonemealSource.INTERACTION);
	}

	public static boolean isValidBonemealTarget(BonemealableBlock block, LevelReader level, BlockPos pos, BlockState state) {
		return block.isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION);
	}

	public static boolean isBonemealSuccess(BonemealableBlock block, net.minecraft.world.level.Level level, RandomSource random, BlockPos pos, BlockState state) {
		return block.isBonemealSuccess(level, random, pos, state, BonemealSource.INTERACTION);
	}
}
