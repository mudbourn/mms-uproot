package info.mudbourn.mmsuproot.data.gen;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackMetadataResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * A data pack that exists only in memory. Its files are generated from what mods asked for (see
 * {@link Gen#build()}) the first time the game reads them, so every mod has registered by then.
 * Nothing is written to disk.
 */
public final class GenPack extends AbstractPackMetadataResources implements PackResources {
	private static final String ID = "mms_uproot/generated";
	private Map<String, byte[]> files;

	private GenPack(PackLocationInfo location) {
		super(location);
	}

	/** The pack as the repository takes it; the pack-source hook offers it to the data pack repository. */
	public static Pack create() {
		PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Uproot generated data"), PackSource.BUILT_IN, Optional.empty());
		Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
			@Override
			public PackMetadataResources openMetadata(PackLocationInfo location) {
				return new GenPack(location);
			}

			@Override
			public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
				return Stream.of(new GenPack(location));
			}
		};
		return Pack.readMetaAndCreate(info, supplier, PackType.SERVER_DATA, new PackSelectionConfig(true, Pack.Position.TOP, false));
	}

	private synchronized Map<String, byte[]> files() {
		if (files == null) files = Gen.build();
		return files;
	}

	@Override
	public IoSupplier<java.io.InputStream> getRootResource(String... path) {
		if (path.length != 1 || !path[0].equals(PACK_META)) return null;
		PackFormat v = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
		String format = "[" + v.major() + ", " + v.minor() + "]";
		String meta = "{\"pack\": {\"description\": \"Data Uproot generated from the mods' own calls\", \"min_format\": [" + v.major() + ", 0], \"max_format\": " + format + "}}";
		byte[] bytes = meta.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		return () -> new ByteArrayInputStream(bytes);
	}

	@Override
	public IoSupplier<java.io.InputStream> getResource(PackType type, Identifier id) {
		if (type != PackType.SERVER_DATA) return null;
		byte[] bytes = files().get("data/" + id.getNamespace() + "/" + id.getPath());
		return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
	}

	@Override
	public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {
		if (type != PackType.SERVER_DATA) return;
		String start = "data/" + namespace + "/" + prefix + "/";
		files().forEach((path, bytes) -> {
			if (path.startsWith(start)) output.accept(Identifier.fromNamespaceAndPath(namespace, path.substring(("data/" + namespace + "/").length())), () -> new ByteArrayInputStream(bytes));
		});
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		Set<String> out = new HashSet<>();
		if (type != PackType.SERVER_DATA) return out;
		for (String path : files().keySet()) out.add(path.split("/")[1]);
		return out;
	}

	@Override
	public void close() { }
}
