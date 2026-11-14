package info.mudbourn.mmsuproot.shim.compat;

import java.util.stream.Stream;

import net.minecraft.server.packs.PackResources;

/** What the {@code implbridge} rules for {@code Pack.ResourcesSupplier} call: 1.21.11's {@code openFull} made one pack, 26.3's {@code openResources} a stream of them. */
public final class LegacyPacks {
	private LegacyPacks() { }

	public static Stream<PackResources> single(PackResources pack) {
		return Stream.of(pack);
	}
}
