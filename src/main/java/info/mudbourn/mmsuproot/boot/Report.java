package info.mudbourn.mmsuproot.boot;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What Uproot did to each target mod. Classes are remapped lazily as the game loads them, so the
 * file is rewritten in the background while anything changes, and once more when the JVM exits.
 */
public final class Report {
	private static final Logger LOGGER = LoggerFactory.getLogger("mms-uproot");

	/** Numbers and lists for one mod. */
	public static final class Entry {
		public final String id;
		public final AtomicInteger classesRemapped = new AtomicInteger();
		public final AtomicInteger classesFailed = new AtomicInteger();
		public final AtomicInteger mixinConfigs = new AtomicInteger();
		public final Set<String> unmapped = ConcurrentHashMap.newKeySet();
		/** Calls and field reads of Minecraft members that 26.3 does not have, after every rule ran: {@code owner.member desc  <- user class}. */
		public final Set<String> unresolved = ConcurrentHashMap.newKeySet();
		public final Map<String, String> moved = new ConcurrentHashMap<>();
		public final List<String> mixinIssues = new CopyOnWriteArrayList<>();
		public final List<String> accessWideners = new CopyOnWriteArrayList<>();
		public final List<String> accessWidenerSkips = new CopyOnWriteArrayList<>();
		public final List<String> notes = new CopyOnWriteArrayList<>();
		public final Map<String, AtomicInteger> dataConverted = new ConcurrentHashMap<>();
		public final AtomicInteger dataConvertedFiles = new AtomicInteger();
		public final List<String> dataFailures = new CopyOnWriteArrayList<>();

		Entry(String id) {
			this.id = id;
		}
	}

	private final Path file;
	private final Map<String, Entry> entries = new ConcurrentHashMap<>();
	private final List<String> general = new CopyOnWriteArrayList<>();
	private volatile int lastHash;
	private volatile Supplier<List<String>> disabled = List::of;

	public Report(Path file) {
		this.file = file;
		Runtime.getRuntime().addShutdownHook(new Thread(this::write, "mms-uproot-report"));
		Thread flusher = new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					return;
				}
				int hash = fingerprint();
				if (hash != lastHash) write();
			}
		}, "mms-uproot-report-flush");
		flusher.setDaemon(true);
		flusher.start();
	}

	public Entry mod(String id) {
		return entries.computeIfAbsent(id, Entry::new);
	}

	/** Parts of mods Uproot switched off on purpose; the lines are asked for at every write. */
	public void disabledSource(Supplier<List<String>> lines) {
		this.disabled = lines;
	}

	public void note(String text) {
		general.add(text);
	}

	private int fingerprint() {
		int h = general.size();
		for (String l : disabled.get()) h = h * 31 + l.hashCode();
		for (Entry e : entries.values()) {
			h = h * 31 + e.classesRemapped.get() + 7 * e.classesFailed.get() + 13 * e.unmapped.size() + 17 * e.moved.size() + 43 * e.unresolved.size()
					+ 19 * e.mixinIssues.size() + 23 * e.accessWideners.size() + 29 * e.accessWidenerSkips.size() + 31 * e.notes.size() + 37 * e.dataConvertedFiles.get() + 41 * e.dataFailures.size();
		}
		return h;
	}

	public synchronized void write() {
		lastHash = fingerprint();
		try {
			Files.createDirectories(file.getParent());
			try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
				w.println("MMS Uproot report");
				for (String g : general) w.println(g);
				List<String> off = disabled.get();
				if (!off.isEmpty()) {
					w.println();
					w.println("== disabled parts (" + off.size() + " rules)");
					for (String l : off) w.println("  " + l);
				}
				for (Entry e : new TreeMap<>(entries).values()) {
					w.println();
					w.println("== " + e.id);
					w.println("classes remapped: " + e.classesRemapped.get() + ", failed: " + e.classesFailed.get());
					w.println("mixin configs served soft: " + e.mixinConfigs.get());
					section(w, "notes", e.notes);
					section(w, "names with no mapping (" + e.unmapped.size() + ")", new TreeSet<>(e.unmapped));
					section(w, "references to Minecraft members that 26.3 lacks (" + e.unresolved.size() + ")", new TreeSet<>(e.unresolved));
					section(w, "classes found by name in another package (" + e.moved.size() + ")",
							new TreeMap<>(e.moved).entrySet().stream().map(x -> x.getKey() + " -> " + x.getValue()).toList());
					section(w, "mixins that will not apply or did not (" + e.mixinIssues.size() + ")", e.mixinIssues);
					section(w, "access wideners handled (" + e.accessWideners.size() + ")", e.accessWideners);
					section(w, "access widener entries skipped (" + e.accessWidenerSkips.size() + ")", e.accessWidenerSkips);
					if (e.dataConvertedFiles.get() > 0 || !e.dataFailures.isEmpty()) {
						w.println("worldgen data files converted: " + e.dataConvertedFiles.get() + ", failed: " + e.dataFailures.size());
						section(w, "conversions", new TreeMap<>(e.dataConverted).entrySet().stream().map(x -> x.getKey() + ": " + x.getValue()).toList());
						section(w, "first conversion failures", e.dataFailures.stream().limit(10).toList());
					}
				}
			}
		} catch (IOException ex) {
			LOGGER.warn("Could not write the report {}", file, ex);
		}
	}

	private static void section(PrintWriter w, String title, java.util.Collection<String> lines) {
		if (lines.isEmpty()) return;
		w.println("-- " + title);
		for (String l : lines) w.println("  " + l);
	}
}
