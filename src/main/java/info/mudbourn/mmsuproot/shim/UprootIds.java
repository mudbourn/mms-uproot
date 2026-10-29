package info.mudbourn.mmsuproot.shim;

import net.minecraft.resources.Identifier;

/** Ids in Uproot's own namespace, for the stand-ins' registries and keys. */
public final class UprootIds {
	private UprootIds() { }

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("mms_uproot", path);
	}
}
