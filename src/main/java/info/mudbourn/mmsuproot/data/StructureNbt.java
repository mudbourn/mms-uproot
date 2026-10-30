package info.mudbourn.mmsuproot.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * Structure templates saved before block-attached entities (paintings, item frames, wreaths and the like)
 * kept their position in {@code block_pos}. Vanilla's data fixer moves {@code TileX/TileY/TileZ} there, but
 * only for its own entity ids, so a mod's own hanging entity reaches the game without a position and
 * is dropped as "invalid position: null". Any other template entity with all three old fields gets the same move.
 */
final class StructureNbt {
	private StructureNbt() { }

	/** The template re-encoded with the move applied, or null when nothing in it needed one. */
	static byte[] fix(byte[] gzipped, FeatureJson.Stats stats) throws IOException {
		CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(gzipped), NbtAccounter.unlimitedHeap());
		boolean changed = false;
		for (Tag entity : root.getListOrEmpty("entities")) {
			if (!(entity instanceof CompoundTag e)) continue;
			CompoundTag nbt = e.getCompoundOrEmpty("nbt");
			if (nbt.getStringOr("id", "minecraft:").startsWith("minecraft:")) continue;
			if (nbt.contains("block_pos") || !nbt.contains("TileX") || !nbt.contains("TileY") || !nbt.contains("TileZ")) continue;
			int x = nbt.getIntOr("TileX", 0);
			int y = nbt.getIntOr("TileY", 0);
			int z = nbt.getIntOr("TileZ", 0);
			nbt.remove("TileX");
			nbt.remove("TileY");
			nbt.remove("TileZ");
			nbt.putIntArray("block_pos", new int[] {x, y, z});
			stats.count("template entity positions");
			changed = true;
		}
		if (!changed) return null;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, out);
		return out.toByteArray();
	}
}
