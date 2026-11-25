package info.mudbourn.mmsuproot.shim.compat;

import java.util.UUID;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The compound tag of 1.21.1, which answered every read with a value (zero when the key was missing) and kept a UUID as an int array, and the
 * way an entity saved itself into one: 1.21.6 turned that into a value view ({@code ValueInput}, {@code ValueOutput}) that wraps a tag.
 */
public final class LegacyNbt {
	private LegacyNbt() { }

	public static int getInt(CompoundTag tag, String key) {
		return tag.getIntOr(key, 0);
	}

	public static long getLong(CompoundTag tag, String key) {
		return tag.getLongOr(key, 0L);
	}

	public static float getFloat(CompoundTag tag, String key) {
		return tag.getFloatOr(key, 0.0F);
	}

	public static double getDouble(CompoundTag tag, String key) {
		return tag.getDoubleOr(key, 0.0);
	}

	public static String getString(CompoundTag tag, String key) {
		return tag.getStringOr(key, "");
	}

	public static boolean getBoolean(CompoundTag tag, String key) {
		return tag.getBooleanOr(key, false);
	}

	public static boolean hasUUID(CompoundTag tag, String key) {
		return tag.getIntArray(key).filter(a -> a.length == 4).isPresent();
	}

	/** A missing or malformed UUID read as the nil UUID rather than a crash, which 1.21.1 only did for the key being absent. */
	public static UUID getUUID(CompoundTag tag, String key) {
		return tag.getIntArray(key).filter(a -> a.length == 4).map(UUIDUtil::uuidFromIntArray).orElseGet(() -> new UUID(0L, 0L));
	}

	public static void putUUID(CompoundTag tag, String key, UUID value) {
		tag.putIntArray(key, UUIDUtil.uuidToIntArray(value));
	}

	/** The tag a value view reads from, for a save method written for a tag ({@code argbridge}). */
	public static CompoundTag tagOf(ValueInput input) {
		if (input instanceof TagValueInput view) return view.input;
		return input.read(MapCodec.assumeMapUnsafe(CompoundTag.CODEC)).orElseGet(CompoundTag::new);
	}

	/** A fresh tag for a save method written for a tag to fill: {@link #writeBack} copies it into the view afterwards. */
	public static CompoundTag emptyTag(ValueOutput output) {
		return new CompoundTag();
	}

	/** Puts what a save method wrote into its tag into the value view it was called for. */
	public static void writeBack(ValueOutput output, CompoundTag written) {
		if (written.isEmpty()) return;
		if (output instanceof TagValueOutput view) view.output.merge(written);
		else output.store(MapCodec.assumeMapUnsafe(CompoundTag.CODEC), written);
	}
}
