package info.mudbourn.mmsuproot.shim.feature;

import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import info.mudbourn.mmsuproot.shim.UprootIds;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Stand-ins for the registries 1.21.11 mods register features and state providers in. A mod's
 * registration lands here, and each entry is also registered in the 26.3 registry as a codec under
 * the same id, so the mod's JSON ({@code "type"} naming it) decodes.
 */
public final class LegacyRegistries {
	/** Where 1.21.11's {@code BuiltInRegistries.FEATURE} reads now point. */
	public static final Registry<LegacyFeature<?>> FEATURE = new MappedRegistry<>(
			ResourceKey.createRegistryKey(UprootIds.id("legacy_feature")), Lifecycle.stable()) {
		@Override
		public Holder.Reference<LegacyFeature<?>> register(ResourceKey<LegacyFeature<?>> key, LegacyFeature<?> value, RegistrationInfo info) {
			Registry.register(BuiltInRegistries.FEATURE_TYPE, key.identifier(), adapter(value));
			return super.register(key, value, info);
		}
	};

	/** Where 1.21.11's {@code BuiltInRegistries.BLOCKSTATE_PROVIDER_TYPE} reads now point. */
	public static final Registry<BlockStateProviderType<?>> STATE_PROVIDER_TYPE = new MappedRegistry<>(
			ResourceKey.createRegistryKey(UprootIds.id("legacy_state_provider_type")), Lifecycle.stable()) {
		@Override
		public Holder.Reference<BlockStateProviderType<?>> register(ResourceKey<BlockStateProviderType<?>> key, BlockStateProviderType<?> value, RegistrationInfo info) {
			Registry.register(BuiltInRegistries.BLOCK_STATE_PROVIDER_TYPE, key.identifier(), (MapCodec<? extends BlockStateProvider>) value.codec());
			return super.register(key, value, info);
		}
	};

	/**
	 * Where 1.21.11's {@code BuiltInRegistries.STRUCTURE_PROCESSOR} reads now point. A mod's processor
	 * class cannot load on 26.3 (see {@link NoopStructureProcessor}), so its type is registered with the
	 * do-nothing codec and the mod's own codec is never asked for.
	 */
	public static final Registry<StructureProcessorType<?>> STRUCTURE_PROCESSOR = new MappedRegistry<>(
			ResourceKey.createRegistryKey(UprootIds.id("legacy_structure_processor")), Lifecycle.stable()) {
		@Override
		public Holder.Reference<StructureProcessorType<?>> register(ResourceKey<StructureProcessorType<?>> key, StructureProcessorType<?> value, RegistrationInfo info) {
			Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, key.identifier(), NoopStructureProcessor.newCodec());
			return super.register(key, value, info);
		}
	};

	private LegacyRegistries() { }

	/** The old settings sat under {@code config}, so the codec reads that field and wraps what it finds. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static MapCodec<LegacyConfiguredFeature<?>> adapter(LegacyFeature<?> feature) {
		LegacyFeature raw = feature;
		MapCodec config = raw.configCodec() == NoneFeatureConfiguration.CODEC
				? MapCodec.unit(NoneFeatureConfiguration.INSTANCE)
				: raw.configCodec().fieldOf("config");
		MapCodec[] self = new MapCodec[1];
		self[0] = config.xmap(c -> new LegacyConfiguredFeature(raw, (FeatureConfiguration) c, self[0]), c -> ((LegacyConfiguredFeature) c).config());
		return self[0];
	}
}
