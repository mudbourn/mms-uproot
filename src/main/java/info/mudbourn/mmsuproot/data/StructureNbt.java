package info.mudbourn.mmsuproot.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * Structure templates saved before block-attached entities (paintings, item frames, wreaths and the like)
 * kept their position in {@code block_pos}. Vanilla's data fixer moves {@code TileX/TileY/TileZ} there, but
 * only for its own entity ids, so a mod's own hanging entity reaches the game without a position and
 * is dropped as "invalid position: null". Any other template entity with all three old fields gets the same move.
 *
 * <p>Templates saved on Forge also list that loader's own attributes on their mobs ({@code forge:entity_gravity},
 * {@code forge:step_height_addition}). 26.3 has no such attribute and would log each one as unknown when the mob
 * loads; they are left out. The saved values are the loader's defaults (the vanilla gravity, no added step height),
 * so the mob keeps the stats it would have had.
 */
final class StructureNbt {
	private StructureNbt() { }

	/** Removes the attributes of another loader's namespace from a mob's saved attribute list. */
	private static boolean dropLoaderAttributes(CompoundTag nbt, FeatureJson.Stats stats) {
		boolean removed = false;
		for (String key : new String[] {"Attributes", "attributes"}) {
			ListTag list = nbt.getList(key).orElse(null);
			if (list == null) continue;
			for (int i = list.size() - 1; i >= 0; i--) {
				if (!(list.get(i) instanceof CompoundTag attribute)) continue;
				String name = attribute.getStringOr("Name", attribute.getStringOr("id", ""));
				if (!name.startsWith("forge:") && !name.startsWith("neoforge:")) continue;
				list.remove(i);
				stats.count("loader attributes dropped from template entities");
				removed = true;
			}
		}
		return removed;
	}

	/** The template re-encoded with the move applied, or null when nothing in it needed one. */
	static byte[] fix(byte[] gzipped, FeatureJson.Stats stats) throws IOException {
		CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(gzipped), NbtAccounter.unlimitedHeap());
		boolean changed = false;
		for (Tag entity : root.getListOrEmpty("entities")) {
			if (!(entity instanceof CompoundTag e)) continue;
			CompoundTag nbt = e.getCompoundOrEmpty("nbt");
			changed |= dropLoaderAttributes(nbt, stats);
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
