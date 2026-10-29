package info.mudbourn.mmsuproot.remap;

import java.util.ArrayList;
import java.util.List;

import info.mudbourn.mmsuproot.boot.Report;
import info.mudbourn.mmsuproot.map.GameClasses;

/** Moves an intermediary access widener to 26.3 names and drops entries whose target no longer exists. */
public final class AccessWideners {
	private AccessWideners() { }

	/** The namespace in the header, or null when there is none. */
	public static String namespace(String text) {
		for (String line : text.split("\n")) {
			String t = line.strip();
			if (t.isEmpty() || t.startsWith("#")) continue;
			String[] p = t.split("\\s+");
			boolean header = p[0].equals("accessWidener") || p[0].equals("classTweaker");
			return header && p.length >= 3 ? p[2] : null;
		}
		return null;
	}

	public static String remap(String text, String runtimeNs, Namer namer, Report.Entry report, String file) {
		GameClasses game = namer.game();
		List<String> out = new ArrayList<>();
		int kept = 0;
		boolean header = true;
		for (String line : text.split("\n")) {
			String t = line.strip();
			if (t.isEmpty() || t.startsWith("#")) {
				out.add(line);
				continue;
			}
			String[] p = t.split("\\s+");
			if (header) {
				header = false;
				out.add(p[0] + " " + p[1] + " " + runtimeNs);
				continue;
			}

			String reason = null;
			String mapped = null;
			if (p.length >= 3 && p[2] != null) {
				String owner = namer.cls(p[2]);
				if (p[1].equals("class") && p.length == 3) {
					if (game.indexed() && !game.has(owner)) reason = "class missing";
					mapped = p[0] + " class " + owner;
				} else if (p.length == 5) {
					boolean method = p[1].equals("method");
					String name = method ? namer.method(p[2], p[3], p[4]) : namer.field(p[2], p[3]);
					String desc = namer.desc(p[4]);
					if (game.indexed() && !game.has(owner)) {
						reason = "class missing";
					} else {
						var info = game.info(owner);
						if (info.isPresent() && !(method ? info.get().hasMethod(name, desc) : info.get().hasField(name, desc))) {
							reason = "member missing";
						}
					}
					mapped = p[0] + " " + p[1] + " " + owner + " " + name + " " + desc;
				}
			}

			if (mapped == null) {
				report.accessWidenerSkips.add(file + ": unreadable line '" + t + "'");
			} else if (reason != null) {
				report.accessWidenerSkips.add(file + ": " + reason + ": " + mapped);
			} else {
				out.add(mapped);
				kept++;
			}
		}
		report.accessWideners.add(file + ": " + kept + " entries moved to " + runtimeNs);
		return String.join("\n", out) + "\n";
	}
}
