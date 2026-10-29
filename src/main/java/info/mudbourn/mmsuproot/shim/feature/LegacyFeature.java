package info.mudbourn.mmsuproot.shim.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelWriter;
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

	private static LegacyFeature<?> inert() {
		return new LegacyFeature<NoneFeatureConfiguration>(NoneFeatureConfiguration.CODEC) {
			@Override
			public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
				return false;
			}
		};
	}
}
