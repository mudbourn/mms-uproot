package info.mudbourn.mmsuproot.map;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import info.mudbourn.mmsuproot.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the {@link NameTable} from the published intermediary file and Mojang's own mappings.
 * Mojang's licence forbids shipping those mappings, so each user downloads them once and only the
 * joined table is kept, in their game directory.
 */
public final class MappingFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");
	public static final String SOURCE_VERSION = "1.21.11";
	private static final String INTERMEDIARY = "https://maven.fabricmc.net/net/fabricmc/intermediary/" + SOURCE_VERSION + "/intermediary-" + SOURCE_VERSION + "-v2.jar";
	private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

	private static final Map<String, String> PRIMITIVES = Map.of("int", "I", "long", "J", "float", "F", "double", "D",
			"boolean", "Z", "byte", "B", "char", "C", "short", "S", "void", "V");

	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

	/** Loads the cached table, or builds and caches it. Returns null when it cannot be had. */
	public static NameTable obtain(Path cacheDir) {
		Path file = cacheDir.resolve("names-" + SOURCE_VERSION + ".v2.tsv");
		try {
			java.nio.file.Files.deleteIfExists(cacheDir.resolve("names-" + SOURCE_VERSION + ".v1.tsv")); // written in place, may be cut short
		} catch (IOException e) {
			LOGGER.debug("Could not remove the old name table", e);
		}
		try {
			if (java.nio.file.Files.isRegularFile(file)) return NameTable.load(file);
		} catch (IOException e) {
			LOGGER.warn("Cached name table {} is unreadable, rebuilding", file, e);
		}

		try {
			LOGGER.info("Downloading 1.21.11 intermediary and Mojang mappings (first launch only)");
			NameTable table = new MappingFetcher().build();
			table.save(file);
			LOGGER.info("Cached the joined name table at {}", file);
			return table;
		} catch (Exception e) {
			LOGGER.error("Could not build the name table; mods built for 1.21.11 cannot be remapped", e);
			return null;
		}
	}

	private InputStream open(String url) throws IOException, InterruptedException {
		HttpResponse<InputStream> res = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofInputStream());
		if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + " for " + url);
		return res.body();
	}

	private String text(String url) throws IOException, InterruptedException {
		try (InputStream in = open(url)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** Downloads a file and checks it against the sha1 the version JSON lists for it. */
	private String verifiedText(String url, String sha1) throws IOException, InterruptedException {
		byte[] data;
		try (InputStream in = open(url)) {
			data = in.readAllBytes();
		}
		if (sha1 != null) {
			try {
				String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(data));
				if (!actual.equalsIgnoreCase(sha1)) throw new IOException("sha1 mismatch for " + url + ": expected " + sha1 + ", got " + actual);
			} catch (java.security.NoSuchAlgorithmException e) {
				throw new IOException("no SHA-1 available to verify " + url, e);
			}
		}
		return new String(data, StandardCharsets.UTF_8);
	}

	@SuppressWarnings("unchecked")
	private NameTable build() throws IOException, InterruptedException {
		Map<String, Object> manifest = Json.parseObject(text(MANIFEST));
		String versionUrl = null;
		for (Object v : (List<Object>) manifest.get("versions")) {
			Map<String, Object> entry = (Map<String, Object>) v;
			if (SOURCE_VERSION.equals(entry.get("id"))) versionUrl = (String) entry.get("url");
		}
		if (versionUrl == null) throw new IOException("version " + SOURCE_VERSION + " missing from the manifest");

		Map<String, Object> downloads = (Map<String, Object>) Json.parseObject(text(versionUrl)).get("downloads");
		List<String> mojangTexts = new ArrayList<>();
		for (String key : new String[] {"client_mappings", "server_mappings"}) {
			Map<String, Object> d = (Map<String, Object>) downloads.get(key);
			if (d != null) mojangTexts.add(verifiedText((String) d.get("url"), (String) d.get("sha1")));
		}

		return join(readIntermediary(), mojangTexts);
	}

	/** One tiny v2 entry: {@code official -> intermediary}. */
	private record Tiny(List<String[]> classes, List<String[]> members) { }

	private Tiny readIntermediary() throws IOException, InterruptedException {
		List<String[]> classes = new ArrayList<>();
		List<String[]> members = new ArrayList<>(); // kind, officialClass, officialName, officialDesc, intermediaryName
		try (ZipInputStream zip = new ZipInputStream(open(INTERMEDIARY))) {
			ZipEntry e;
			while ((e = zip.getNextEntry()) != null) {
				if (!e.getName().equals("mappings/mappings.tiny")) continue;
				BufferedReader r = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
				r.readLine();
				String cur = null;
				String line;
				while ((line = r.readLine()) != null) {
					String[] p = line.split("\t");
					if (p[0].equals("c")) {
						cur = p[1];
						classes.add(new String[] {p[1], p[2]});
					} else if (p.length >= 5 && p[0].isEmpty() && (p[1].equals("m") || p[1].equals("f"))) {
						members.add(new String[] {p[1], cur, p[3], p[2], p[4]});
					}
				}
			}
		}
		return new Tiny(classes, members);
	}

	private NameTable join(Tiny tiny, List<String> mojangTexts) {
		Map<String, String> obfToMojang = new HashMap<>();
		Map<String, String> mojangToObf = new HashMap<>();
		List<String[]> rawMembers = new ArrayList<>(); // obf class, member line
		for (String text : mojangTexts) {
			String cur = null;
			for (String line : text.split("\n")) {
				if (line.isEmpty() || line.charAt(0) == '#') continue;
				if (line.charAt(0) != ' ') {
					int arrow = line.indexOf(" -> ");
					String mojang = line.substring(0, arrow).replace('.', '/');
					cur = line.substring(arrow + 4, line.length() - 1).replace('.', '/');
					obfToMojang.put(cur, mojang);
					mojangToObf.put(mojang, cur);
				} else {
					rawMembers.add(new String[] {cur, line.strip()});
				}
			}
		}

		Map<String, String> pgFields = new HashMap<>();
		Map<String, String> pgMethods = new HashMap<>();
		Set<String> pgObfNames = new HashSet<>(); // "obf class obf member": every obfuscated member name the ProGuard blocks list
		for (String[] m : rawMembers) {
			String body = m[1];
			int arrow = body.lastIndexOf(" -> ");
			String obf = body.substring(arrow + 4);
			pgObfNames.add(m[0] + " " + obf);
			body = body.substring(0, arrow).replaceFirst("^\\d+:\\d+:", "").replaceFirst(":\\d+:\\d+$", "");
			int paren = body.indexOf('(');
			if (paren >= 0) {
				String head = body.substring(0, paren);
				String args = body.substring(paren + 1, body.lastIndexOf(')'));
				int sp = head.lastIndexOf(' ');
				StringBuilder desc = new StringBuilder("(");
				if (!args.isEmpty()) for (String a : args.split(",")) desc.append(descOf(a, mojangToObf));
				desc.append(')').append(descOf(head.substring(0, sp), mojangToObf));
				pgMethods.put(m[0] + " " + obf + " " + desc, head.substring(sp + 1));
			} else {
				String name = body.substring(body.lastIndexOf(' ') + 1);
				pgFields.put(m[0] + " " + obf, name.substring(name.lastIndexOf('.') + 1));
			}
		}

		NameTable table = new NameTable();
		int obfuscated = 0;
		for (String[] c : tiny.classes) {
			String mojang = obfToMojang.get(c[0]);
			if (mojang != null) table.classes.put(c[1], mojang);
		}
		for (String[] m : tiny.members) {
			boolean method = m[0].equals("m");
			String name = method ? pgMethods.get(m[1] + " " + m[2] + " " + m[3]) : pgFields.get(m[1] + " " + m[2]);
			if (name == null) {
				// Not listed with this descriptor. A name the ProGuard block lists under any other
				// descriptor is obfuscated, so storing it as a Mojang name would be wrong.
				if (pgObfNames.contains(m[1] + " " + m[2])) {
					obfuscated++;
					continue;
				}
				name = m[2]; // never obfuscated
			}
			(method ? table.methods : table.fields).putIfAbsent(m[4], name);
		}
		if (obfuscated > 0) LOGGER.info("{} members have no Mojang name and stay unmapped", obfuscated);
		return table;
	}

	private static String descOf(String type, Map<String, String> mojangToObf) {
		int dims = 0;
		while (type.endsWith("[]")) { dims++; type = type.substring(0, type.length() - 2); }
		String base = PRIMITIVES.get(type);
		if (base == null) {
			String slash = type.replace('.', '/');
			base = "L" + mojangToObf.getOrDefault(slash, slash) + ";";
		}
		return "[".repeat(dims) + base;
	}
}
