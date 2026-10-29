package info.mudbourn.mmsuproot.tool;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import info.mudbourn.mmsuproot.util.Json;

/**
 * Writes {@code fabric_loader_dependencies.json} for a mods folder, so mods built for older
 * Minecraft versions pass Loader's dependency check on 26.3.
 *
 * <pre>java -cp mms-uproot.jar info.mudbourn.mmsuproot.tool.OverridesTool &lt;modsDir&gt; &lt;outFile&gt;</pre>
 *
 * <p>Per mod it replaces {@code fabric} with {@code fabric-api}, widens a {@code minecraft} or
 * {@code java} range that excludes 26.3 or Java 25, and swaps removed Fabric API module ids for
 * the ones that replaced them. Jar-in-jar mods are read too.
 */
public final class OverridesTool {
	private static final int[] MINECRAFT = {26, 3, 0};
	private static final int[] JAVA = {25, 0, 0};
	private static final String[] KINDS = {"depends", "recommends", "suggests"};

	private OverridesTool() { }

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("usage: OverridesTool <modsDir> <outFile>");
			System.exit(2);
		}

		Map<String, String> aliases = loadAliases();
		Map<String, Map<String, Map<String, Object>>> overrides = new TreeMap<>();

		try (DirectoryStream<Path> jars = Files.newDirectoryStream(Path.of(args[0]), "*.jar")) {
			for (Path jar : jars) {
				try (InputStream in = Files.newInputStream(jar)) {
					scan(in, jar.getFileName().toString(), aliases, overrides);
				} catch (IOException | RuntimeException e) {
					System.err.println("skipped " + jar.getFileName() + ": " + e);
				}
			}
		}

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("version", 1L);
		Map<String, Object> mods = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Map<String, Object>>> e : overrides.entrySet()) {
			mods.put(e.getKey(), new LinkedHashMap<String, Object>(e.getValue()));
		}
		root.put("overrides", mods);

		Path out = Path.of(args[1]);
		if (out.getParent() != null) Files.createDirectories(out.getParent());
		Files.writeString(out, Json.write(root));
		System.out.println("wrote overrides for " + mods.size() + " mods to " + out);
	}

	private static Map<String, String> loadAliases() throws IOException {
		Map<String, String> aliases = new HashMap<>();
		try (InputStream in = OverridesTool.class.getResourceAsStream("/uproot/fabric-api-aliases.txt")) {
			if (in == null) return aliases;
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = r.readLine()) != null) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) continue;
				String[] p = line.split("\\s+");
				if (p.length == 2) aliases.put(p[0], p[1]);
			}
		}
		return aliases;
	}

	/** Reads one jar (and the jars nested inside it) from a stream. */
	@SuppressWarnings("unchecked")
	private static void scan(InputStream stream, String label, Map<String, String> aliases,
			Map<String, Map<String, Map<String, Object>>> overrides) throws IOException {
		Map<String, byte[]> nested = new HashMap<>();
		Map<String, Object> fmj = null;
		try (ZipInputStream zip = new ZipInputStream(stream)) {
			ZipEntry e;
			while ((e = zip.getNextEntry()) != null) {
				if (e.getName().equals("fabric.mod.json")) {
					fmj = Json.parseObject(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
				} else if (e.getName().startsWith("META-INF/jars/") && e.getName().endsWith(".jar")) {
					nested.put(e.getName(), zip.readAllBytes());
				}
			}
		}
		if (fmj == null) return;

		if (fmj.get("id") instanceof String id) examine(id, fmj, aliases, overrides);

		if (fmj.get("jars") instanceof List<?> jars) {
			for (Object j : jars) {
				if (j instanceof Map<?, ?> m && nested.get(String.valueOf(m.get("file"))) != null) {
					String file = String.valueOf(m.get("file"));
					scan(new ByteArrayInputStream(nested.get(file)), label + "!" + file, aliases, overrides);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static void examine(String id, Map<String, Object> fmj, Map<String, String> aliases,
			Map<String, Map<String, Map<String, Object>>> overrides) {
		for (String kind : KINDS) {
			if (!(fmj.get(kind) instanceof Map<?, ?> deps)) continue;
			Map<String, Object> remove = new LinkedHashMap<>();
			Map<String, Object> add = new LinkedHashMap<>();

			for (Map.Entry<?, ?> d : ((Map<Object, Object>) deps).entrySet()) {
				String dep = String.valueOf(d.getKey());
				Object range = d.getValue();
				if (dep.equals("fabric")) {
					remove.put(dep, "*");
					add.put("fabric-api", "*");
				} else if (aliases.containsKey(dep)) {
					remove.put(dep, "*");
					add.put(aliases.get(dep), "*");
				} else if (dep.equals("minecraft") && !Ranges.admits(range, MINECRAFT)) {
					remove.put(dep, "*");
					add.put(dep, "*");
				} else if (dep.equals("java") && !Ranges.admits(range, JAVA)) {
					remove.put(dep, "*");
					add.put(dep, "*");
				}
			}

			if (remove.isEmpty()) continue;
			Map<String, Map<String, Object>> mod = overrides.computeIfAbsent(id, k -> new LinkedHashMap<>());
			mod.computeIfAbsent("-" + kind, k -> new LinkedHashMap<>()).putAll(remove);
			mod.computeIfAbsent("+" + kind, k -> new LinkedHashMap<>()).putAll(add);
		}
	}
}
