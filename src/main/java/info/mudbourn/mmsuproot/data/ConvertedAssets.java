package info.mudbourn.mmsuproot.data;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import info.mudbourn.mmsuproot.boot.Report;
import info.mudbourn.mmsuproot.util.Json;
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * The converted client assets of one mod pack. Built on the first request for assets, once per pack.
 * Today that is the sign bodies: 1.21.11 drew them from entity sheets, 26.3 draws them as block
 * models over a 32x32 block texture, and a mod whose sign models only carry a particle texture
 * would show text without a board. And the farmland models, whose template 26.3 renamed.
 */
final class ConvertedAssets {
	/** Blockstate name endings, longest first, with the vanilla model family each one uses. */
	private static final String[][] KINDS = {
			{"_wall_hanging_sign", "wall_hanging"}, {"_hanging_sign", "hanging"}, {"_wall_sign", "wall"}, {"_sign", "standing"}};

	/** A rectangle of the old sheet and where 26.3 expects it: sx, sy, w, h, dx, dy. */
	private static final int[][] SIGN = {
			{2, 0, 24, 2, 0, 0}, {26, 0, 24, 2, 0, 28}, {2, 2, 24, 12, 0, 2}, {28, 2, 24, 12, 0, 16},
			{26, 2, 2, 12, 24, 2}, {0, 2, 2, 12, 24, 16},
			{2, 16, 2, 14, 28, 0}, {6, 16, 2, 14, 28, 16}, {4, 16, 2, 14, 30, 0}, {0, 16, 2, 14, 30, 16}, {4, 14, 2, 2, 28, 30}};
	private static final int[][] HANGING = {
			{2, 12, 14, 2, 2, 14}, {16, 12, 14, 2, 2, 26}, {0, 14, 2, 10, 0, 16}, {2, 14, 14, 10, 2, 16},
			{16, 14, 2, 10, 16, 16}, {18, 14, 14, 10, 18, 16},
			{4, 0, 16, 4, 0, 0}, {20, 0, 16, 4, 0, 9}, {0, 4, 4, 2, 16, 7}, {4, 4, 16, 2, 0, 4},
			{20, 4, 4, 2, 16, 4}, {24, 4, 16, 2, 0, 7},
			{0, 6, 3, 6, 22, 7}, {6, 6, 3, 6, 28, 7}, {14, 6, 12, 6, 20, 0}};

	private final ModPackResources pack;
	private final Report.Entry report;
	private volatile Built built;

	ConvertedAssets(ModPackResources pack, Report.Entry report) {
		this.pack = pack;
		this.report = report;
	}

	/** Files by "namespace:path". */
	private record Built(Map<String, byte[]> served, Set<String> namespaces) { }

	/** Handles the three calls that see assets; everything else is {@link ConvertedView#PASS}. */
	Object intercept(Method method, Object[] args) {
		switch (method.getName()) {
		case "getResource" -> {
			if (args.length != 2 || args[0] != PackType.CLIENT_RESOURCES) return ConvertedView.PASS;
			Identifier id = (Identifier) args[1];
			byte[] bytes = built().served.get(id.getNamespace() + ":" + id.getPath());
			return bytes == null ? ConvertedView.PASS : (IoSupplier<InputStream>) () -> new ByteArrayInputStream(bytes);
		}
		case "listResources" -> {
			if (args.length != 4 || args[0] != PackType.CLIENT_RESOURCES) return ConvertedView.PASS;
			String namespace = (String) args[1];
			String path = (String) args[2];
			PackResources.ResourceOutput out = (PackResources.ResourceOutput) args[3];
			Built b = built();
			Set<String> seen = new HashSet<>();
			pack.listResources(PackType.CLIENT_RESOURCES, namespace, path, (id, supplier) -> {
				String key = namespace + ":" + id.getPath();
				seen.add(key);
				byte[] bytes = b.served.get(key);
				out.accept(id, bytes != null ? () -> new ByteArrayInputStream(bytes) : supplier);
			});
			String prefix = namespace + ":" + path + "/";
			for (Map.Entry<String, byte[]> e : b.served.entrySet()) {
				if (!e.getKey().startsWith(prefix) || seen.contains(e.getKey())) continue;
				byte[] bytes = e.getValue();
				out.accept(Identifier.fromNamespaceAndPath(namespace, e.getKey().substring(namespace.length() + 1)), () -> new ByteArrayInputStream(bytes));
			}
			return null;
		}
		case "getNamespaces" -> {
			if (args.length != 1 || args[0] != PackType.CLIENT_RESOURCES) return ConvertedView.PASS;
			Set<String> out = new HashSet<>(pack.getNamespaces(PackType.CLIENT_RESOURCES));
			out.addAll(built().namespaces);
			return out;
		}
		default -> {
			return ConvertedView.PASS;
		}
		}
	}

	private Built built() {
		Built b = built;
		if (b != null) return b;
		synchronized (this) {
			if (built == null) built = build();
			return built;
		}
	}

	private Built build() {
		Map<String, byte[]> served = new LinkedHashMap<>();
		Set<String> namespaces = new HashSet<>();
		try {
			signs(served);
			farmlands(served);
		} catch (RuntimeException e) {
			report.dataFailures.add("client assets: " + e);
		}
		return new Built(served, namespaces);
	}

	private byte[] read(String namespace, String path) {
		IoSupplier<InputStream> supplier = pack.getResource(PackType.CLIENT_RESOURCES, Identifier.fromNamespaceAndPath(namespace, path));
		if (supplier == null) return null;
		try (InputStream in = supplier.get()) {
			return in.readAllBytes();
		} catch (IOException e) {
			return null;
		}
	}

	private void count(String what) {
		report.dataConverted.computeIfAbsent(what, k -> new AtomicInteger()).incrementAndGet();
	}

	/**
	 * Every sign blockstate whose single model only names a particle texture gets the 26.3 sign models over a
	 * converted copy of the wood's entity sheet. The wood is the blockstate name without its sign ending,
	 * which is also how the mod names the sheet; the vanilla models are used as parents, not copied.
	 */
	private void signs(Map<String, byte[]> served) {
		for (String ns : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
			List<String> stems = new java.util.ArrayList<>();
			pack.listResources(PackType.CLIENT_RESOURCES, ns, "blockstates", (id, supplier) -> {
				String p = id.getPath();
				if (p.endsWith(".json")) stems.add(p.substring("blockstates/".length(), p.length() - ".json".length()));
			});
			for (String stem : stems) {
				String kind = null;
				String wood = null;
				for (String[] k : KINDS) {
					if (stem.endsWith(k[0])) {
						kind = k[1];
						wood = stem.substring(0, stem.length() - k[0].length());
						break;
					}
				}
				if (kind == null || wood.isEmpty()) continue;
				try {
					sign(served, ns, stem, wood, kind);
				} catch (IOException | RuntimeException e) {
					report.dataFailures.add(ns + ":blockstates/" + stem + ".json: " + e);
				}
			}
		}
	}

	/**
	 * A block model whose parent is the farmland template 26.3 no longer has is moved onto the indented cube template, which has the
	 * same shape: the dirt texture is its bottom and sides, and the top stays the top.
	 */
	private void farmlands(Map<String, byte[]> served) {
		for (String ns : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
			pack.listResources(PackType.CLIENT_RESOURCES, ns, "models/block", (id, supplier) -> {
				if (!id.getPath().endsWith(".json")) return;
				try (InputStream in = supplier.get()) {
					Map<String, Object> model = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
					if (!"minecraft:block/template_farmland".equals(model.get("parent")) || !(model.get("textures") instanceof Map<?, ?> old)) return;
					Map<String, Object> textures = new LinkedHashMap<>();
					textures.put("bottom", old.get("dirt"));
					textures.put("side", old.get("dirt"));
					textures.put("top", old.get("top"));
					model.put("parent", "minecraft:block/template_cube_bottom_top_indented");
					model.put("textures", textures);
					served.put(ns + ":" + id.getPath(), Json.write(model).getBytes(StandardCharsets.UTF_8));
					count("farmland models moved");
				} catch (IOException | RuntimeException e) {
					report.dataFailures.add(ns + ":" + id.getPath() + ": " + e);
				}
			});
		}
	}

	@SuppressWarnings("unchecked")
	private void sign(Map<String, byte[]> served, String ns, String stem, String wood, String kind) throws IOException {
		byte[] state = read(ns, "blockstates/" + stem + ".json");
		if (state == null) return;
		Map<String, Object> variants = (Map<String, Object>) Json.parseObject(new String(state, StandardCharsets.UTF_8)).get("variants");
		if (variants == null || variants.size() != 1 || !(variants.get("") instanceof Map<?, ?> only) || !(only.get("model") instanceof String model)) return;
		Identifier modelId = Identifier.tryParse(model);
		if (modelId == null) return;
		byte[] modelBytes = read(modelId.getNamespace(), "models/" + modelId.getPath() + ".json");
		if (modelBytes == null) return;
		Map<String, Object> old = Json.parseObject(new String(modelBytes, StandardCharsets.UTF_8));
		if (old.containsKey("parent") || old.containsKey("elements") || !(old.get("textures") instanceof Map<?, ?> oldTextures)
				|| !oldTextures.containsKey("particle") || oldTextures.size() != 1) return;

		boolean hanging = kind.endsWith("hanging");
		String sheet = hanging ? "textures/entity/signs/hanging/" + wood + ".png" : "textures/entity/signs/" + wood + ".png";
		String textureName = wood + (hanging ? "_hanging_sign" : "_sign");
		String texturePath = "textures/block/" + textureName + ".png";
		String key = ns + ":" + texturePath;
		if (!served.containsKey(key)) {
			byte[] sheetBytes = read(ns, sheet);
			if (sheetBytes == null) return;
			byte[] converted = convert(sheetBytes, hanging ? HANGING : SIGN);
			if (converted == null) return;
			served.put(key, converted);
			count("sign textures converted");
		}

		Map<String, String> textures = new LinkedHashMap<>();
		textures.put("all", ns + ":block/" + textureName);
		textures.put("particle", String.valueOf(oldTextures.get("particle")));
		String vanilla = switch (kind) {
		case "standing" -> "template_sign_rot_";
		case "wall" -> "template_wall_sign";
		case "hanging" -> "template_hanging_sign_rot_";
		default -> "template_wall_hanging_sign";
		};
		Map<String, Object> newVariants = new LinkedHashMap<>();
		if (kind.startsWith("wall")) {
			model(served, ns, stem, vanilla, textures);
			for (String[] f : new String[][] {{"east", "270"}, {"north", "180"}, {"south", "0"}, {"west", "90"}}) {
				newVariants.put("facing=" + f[0], variant(ns + ":block/" + stem, f[1]));
			}
		} else {
			for (int quarter = 0; quarter < 4; quarter++) {
				model(served, ns, stem + "_rot_" + quarter, vanilla + quarter, textures);
				if (hanging) model(served, ns, stem + "_attached_rot_" + quarter, "template_attached_hanging_sign_rot_" + quarter, textures);
			}
			for (int rotation = 0; rotation < 16; rotation++) {
				for (boolean attached : hanging ? new boolean[] {false, true} : new boolean[] {false}) {
					String name = stem + (attached ? "_attached" : "") + "_rot_" + rotation % 4;
					String props = (hanging ? "attached=" + attached + "," : "") + "rotation=" + rotation;
					newVariants.put(props, variant(ns + ":block/" + name, String.valueOf(rotation / 4 * 90)));
				}
			}
		}
		served.put(ns + ":blockstates/" + stem + ".json", Json.write(Map.of("variants", newVariants)).getBytes(StandardCharsets.UTF_8));
		count("sign blockstates rebuilt");
	}

	private static Map<String, Object> variant(String model, String y) {
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("model", model);
		if (!y.equals("0")) v.put("y", Long.parseLong(y));
		return v;
	}

	private static void model(Map<String, byte[]> served, String ns, String name, String template, Map<String, String> textures) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("parent", "minecraft:block/" + template);
		m.put("textures", textures);
		served.put(ns + ":models/block/" + name + ".json", Json.write(m).getBytes(StandardCharsets.UTF_8));
	}

	/** Repacks the old 64x32 entity sheet into the 32x32 block texture 26.3's sign models read. */
	private static byte[] convert(byte[] sheet, int[][] rects) {
		try {
			BufferedImage src = ImageIO.read(new ByteArrayInputStream(sheet));
			if (src == null) return null;
			// sheets are drawn for 64x32; a larger one is the same layout at a multiple
			int scale = Math.max(1, src.getWidth() / 64);
			BufferedImage dst = new BufferedImage(32 * scale, 32 * scale, BufferedImage.TYPE_INT_ARGB);
			for (int[] r : rects) {
				for (int y = 0; y < r[3] * scale; y++) {
					for (int x = 0; x < r[2] * scale; x++) {
						dst.setRGB(r[4] * scale + x, r[5] * scale + y, src.getRGB(r[0] * scale + x, r[1] * scale + y));
					}
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(dst, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			return null;
		}
	}
}
