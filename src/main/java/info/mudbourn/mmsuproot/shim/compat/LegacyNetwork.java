package info.mudbourn.mmsuproot.shim.compat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.world.phys.Vec3;

/** The {@code FriendlyByteBuf} helpers 26.3 dropped (it has codecs for them), written as 1.21.11 wrote them. */
public final class LegacyNetwork {
	private LegacyNetwork() { }

	/** {@code readCollection}: a var-int count, then that many elements. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Collection readCollection(FriendlyByteBuf buf, IntFunction factory, StreamDecoder decoder) {
		int count = buf.readVarInt();
		Collection out = (Collection) factory.apply(count);
		for (int i = 0; i < count; i++) out.add(decoder.decode(buf));
		return out;
	}

	/** {@code writeCollection}: a var-int count, then every element. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void writeCollection(FriendlyByteBuf buf, Collection values, StreamEncoder encoder) {
		buf.writeVarInt(values.size());
		for (Object value : values) encoder.encode(buf, value);
	}

	/** {@code readList}: a collection read into an {@link ArrayList}. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static List readList(FriendlyByteBuf buf, StreamDecoder decoder) {
		return (List) readCollection(buf, ArrayList::new, decoder);
	}

	/** {@code readMap}: a var-int count, then that many key and value pairs. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Map readMap(FriendlyByteBuf buf, IntFunction factory, StreamDecoder keys, StreamDecoder values) {
		int count = buf.readVarInt();
		Map out = (Map) factory.apply(count);
		for (int i = 0; i < count; i++) {
			Object key = keys.decode(buf);
			out.put(key, values.decode(buf));
		}
		return out;
	}

	/** {@code readMap} into a hash map. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Map readMap(FriendlyByteBuf buf, StreamDecoder keys, StreamDecoder values) {
		return readMap(buf, (IntFunction) Maps::newHashMapWithExpectedSize, keys, values);
	}

	/** {@code writeMap}: a var-int count, then every key and value. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void writeMap(FriendlyByteBuf buf, Map map, StreamEncoder keys, StreamEncoder values) {
		buf.writeVarInt(map.size());
		map.forEach((key, value) -> {
			keys.encode(buf, key);
			values.encode(buf, value);
		});
	}

	/** {@code readIntIdList}: a var-int count, then that many var-ints. */
	public static IntList readIntIdList(FriendlyByteBuf buf) {
		int count = buf.readVarInt();
		IntList out = new IntArrayList();
		for (int i = 0; i < count; i++) out.add(buf.readVarInt());
		return out;
	}

	/** {@code writeIntIdList}: a var-int count, then every id as a var-int. */
	public static void writeIntIdList(FriendlyByteBuf buf, IntList ids) {
		buf.writeVarInt(ids.size());
		ids.forEach(id -> buf.writeVarInt(id));
	}

	/** {@code readInstant}: epoch milliseconds as a long. */
	public static Instant readInstant(FriendlyByteBuf buf) {
		return Instant.ofEpochMilli(buf.readLong());
	}

	/** {@code writeInstant}: epoch milliseconds as a long. */
	public static void writeInstant(FriendlyByteBuf buf, Instant instant) {
		buf.writeLong(instant.toEpochMilli());
	}

	/** {@code readVec3}: three doubles. */
	public static Vec3 readVec3(FriendlyByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	/** {@code writeVec3}: three doubles. */
	public static void writeVec3(FriendlyByteBuf buf, Vec3 vec) {
		buf.writeDouble(vec.x());
		buf.writeDouble(vec.y());
		buf.writeDouble(vec.z());
	}
}
