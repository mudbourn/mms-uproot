package info.mudbourn.mmsuproot.map;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Intermediary names joined to 1.21.11 Mojang names. Intermediary names are unique across the whole
 * game, so classes, methods and fields can be looked up by name alone, with no class hierarchy.
 */
public final class NameTable {
	private static final String END = "E";

	public final Map<String, String> classes = new HashMap<>();
	public final Map<String, String> methods = new HashMap<>();
	public final Map<String, String> fields = new HashMap<>();

	/**
	 * Writes to a temporary file and moves it into place, so a crash never leaves a half-written
	 * cache. The last line records how many entries came before it; {@link #load} checks it.
	 */
	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			write(w, 'C', classes);
			write(w, 'M', methods);
			write(w, 'F', fields);
			w.write(END + "\t" + (classes.size() + methods.size() + fields.size()) + "\n");
		}
		try {
			Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void write(BufferedWriter w, char kind, Map<String, String> map) throws IOException {
		for (Map.Entry<String, String> e : map.entrySet()) {
			w.write(kind);
			w.write('\t');
			w.write(e.getKey());
			w.write('\t');
			w.write(e.getValue());
			w.write('\n');
		}
	}

	public static NameTable load(Path file) throws IOException {
		NameTable table = new NameTable();
		long declared = -1;
		try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			String line;
			while ((line = r.readLine()) != null) {
				String[] p = line.split("\t");
				if (p.length == 2 && p[0].equals(END)) {
					declared = Long.parseLong(p[1]);
					continue;
				}
				if (p.length != 3) continue;
				switch (p[0]) {
				case "C" -> table.classes.put(p[1], p[2]);
				case "M" -> table.methods.put(p[1], p[2]);
				case "F" -> table.fields.put(p[1], p[2]);
				default -> { }
				}
			}
		}
		long actual = (long) table.classes.size() + table.methods.size() + table.fields.size();
		if (declared != actual) throw new IOException("name table " + file + " is incomplete: " + actual + " entries, end marker says " + declared);
		if (table.classes.isEmpty()) throw new IOException("empty name table " + file);
		return table;
	}
}
