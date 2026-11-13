package info.mudbourn.mmsuproot.shim.compat;

import java.util.stream.Stream;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * 1.21.11's {@code LootItemConditionType}: a record around the codec of one loot condition, which a mod registered in the loot
 * condition type registry. That registry holds the codecs themselves now, so this stand-in is a codec that forwards to the one
 * it was given, and registering it puts a working codec in the registry. The record's accessor is not kept: a codec already has a method of that name with another return type.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class LegacyLootConditionType extends MapCodec<LootItemCondition> {
	private final MapCodec<? extends LootItemCondition> codec;

	public LegacyLootConditionType(MapCodec<? extends LootItemCondition> codec) {
		this.codec = codec;
	}

	@Override
	public <T> Stream<T> keys(DynamicOps<T> ops) {
		return codec.keys(ops);
	}

	@Override
	public <T> DataResult<LootItemCondition> decode(DynamicOps<T> ops, MapLike<T> input) {
		return (DataResult) codec.decode(ops, input);
	}

	@Override
	public <T> RecordBuilder<T> encode(LootItemCondition input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
		return ((MapCodec) codec).encode(input, ops, prefix);
	}

	@Override
	public String toString() {
		return "LootItemConditionType[codec=" + codec + "]";
	}
}
