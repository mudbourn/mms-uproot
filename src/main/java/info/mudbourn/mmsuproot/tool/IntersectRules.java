package info.mudbourn.mmsuproot.tool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The data files of {@link IntersectTool}: the library list ({@code intersect-libraries.txt}), the owner routing
 * ({@code intersect-owners.txt}) and the names Uproot's own rule files mention (to tell a node that Uproot has a rule for).
 */
final class IntersectRules {
	/** A suggested repo and the one-line reason. */
	record Owner(String repo, String reason) { }

	record Domain(String repo, Pattern pattern) { }

	private final List<String> libExact = new ArrayList<>();
	private final List<String> libPrefix = new ArrayList<>();
	final Map<String, String> modRepo = new LinkedHashMap<>();
	private final List<Domain> domains = new ArrayList<>();
	final List<String> coverageMods = new ArrayList<>();
	final List<Domain> coverageRoutes = new ArrayList<>();
	final List<Pattern> domainTags = new ArrayList<>();
	final List<Pattern> domainItems = new ArrayList<>();
	private String uproot = "mms-uproot";
	private String compat = "mms-mod-compat-support";
	private String data = "mms-pack";
	/** The classes Uproot renames or stands in for (class, package, typesonly, mixintarget and super rules), and "OWNER|NAME" of every member rule. */
	private final Set<String> ruleClasses = new HashSet<>();
	private final Set<String> ruleMembers = new HashSet<>();

	IntersectRules() {
		for (String line : lines("/uproot/intersect-libraries.txt")) {
			if (line.endsWith("*")) libPrefix.add(line.substring(0, line.length() - 1));
			else libExact.add(line);
		}
		for (String line : lines("/uproot/intersect-owners.txt")) {
			String[] p = line.split("\\s+", 3);
			switch (p[0]) {
			case "mod-repo" -> modRepo.put(p[1], p[2].strip());
			case "default-uproot" -> uproot = p[1];
			case "default-compat" -> compat = p[1];
			case "default-data" -> data = p[1];
			case "domain-node" -> domains.add(new Domain(p[1], Pattern.compile(p[2].strip())));
			case "coverage-mods" -> coverageMods.addAll(List.of(line.split("\\s+")).subList(1, line.split("\\s+").length));
			case "coverage-route" -> coverageRoutes.add(new Domain(p[1], Pattern.compile(p[2].strip())));
			case "domain-tag" -> domainTags.add(Pattern.compile(line.split("\\s+", 2)[1].strip()));
			case "domain-item" -> domainItems.add(Pattern.compile(line.split("\\s+", 2)[1].strip()));
			default -> { }
			}
		}
		for (String res : new String[] {"/uproot/renames.txt", "/uproot/renames-26x.txt", "/uproot/dead-hooks.txt", "/uproot/disable.txt"}) {
			for (String line : lines(res)) {
				String[] t = line.split("\\s+");
				if (t.length < 2) continue;
				switch (t[0]) {
				case "class", "typesonly", "mixintarget", "super" -> {
					for (int i = 1; i < t.length && i < 3; i++) ruleClasses.add(t[i]);
				}
				case "package" -> { }
				default -> {
					if (t.length >= 3) {
						ruleMembers.add(t[1] + "|" + t[2]);
						if (t.length >= 4 && (t[0].equals("method") || t[0].equals("field"))) ruleMembers.add(t[1] + "|" + t[3]);
					}
					if (res.endsWith("dead-hooks.txt") && t.length >= 2) ruleMembers.add(t[0] + "|" + t[1]);
				}
				}
			}
		}
	}

	private static List<String> lines(String resource) {
		List<String> out = new ArrayList<>();
		try (InputStream in = IntersectRules.class.getResourceAsStream(resource)) {
			if (in == null) return out;
			for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
				String t = l.strip();
				if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
			}
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
		return out;
	}

	boolean isLibrary(String id) {
		if (libExact.contains(id)) return true;
		for (String p : libPrefix) if (id.startsWith(p)) return true;
		return false;
	}

	/** Whether Uproot renames or stands in for this 26.3 class, so a mod's mixin into it needed a rule or shim. */
	boolean hitsClass(String cls) {
		return ruleClasses.contains(cls);
	}

	/** Whether a rule of Uproot names this member of the class (or the member of any owner). */
	boolean hitsMember(String cls, String name) {
		return ruleMembers.contains(cls + "|" + name) || ruleMembers.contains("*|" + name);
	}

	String routeCoverage(String item) {
		for (Domain d : coverageRoutes) if (d.pattern().matcher(item).find()) return d.repo();
		return "mms-weapons";
	}

	boolean isDomainItem(String path) {
		for (Pattern p : domainItems) if (p.matcher(path).find()) return true;
		return false;
	}

	boolean isDomainTag(String id) {
		for (Pattern p : domainTags) if (p.matcher(id).find()) return true;
		return false;
	}

	/** The owner of a node, by the rules listed in {@code intersect-owners.txt}. */
	Owner owner(String key, List<Touch> touches, String[] worst) {
		// an mms mod owns the node when it is one of the worst pair, or when so few mods meet there that the node is its own
		Set<String> mods = new HashSet<>();
		for (Touch t : touches) if (!isLibrary(t.mod()) && !t.mod().equals("minecraft")) mods.add(t.mod());
		boolean small = mods.size() <= 6;
		Map<String, Double> mms = new LinkedHashMap<>();
		for (Touch t : touches) {
			if (!modRepo.containsKey(t.mod())) continue;
			if (small || t.mod().equals(worst[0]) || t.mod().equals(worst[1])) mms.merge(t.mod(), t.weight(), Double::sum);
		}
		if (!mms.isEmpty()) {
			String best = mms.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
			return new Owner(modRepo.get(best), best + (small ? " is one of the few parties of the node" : " is in the node's worst pair"));
		}
		StringBuilder text = new StringBuilder(key);
		int n = 0;
		for (Touch t : touches) {
			if (n++ == 8) break;
			text.append(' ').append(t.detail());
		}
		for (Domain d : domains) {
			if (d.pattern().matcher(text).find()) return new Owner(d.repo(), "node is in the weapons, tools and combat domain (" + d.pattern().pattern().substring(0, Math.min(40, d.pattern().pattern().length())) + "...)");
		}
		boolean code = key.startsWith("method:") || key.startsWith("site:") || key.startsWith("class:") || key.startsWith("access:");
		for (Touch t : touches) {
			if (t.legacy() && t.rule()) return new Owner(uproot, t.mod() + " was remapped by Uproot and the target has a rule or shim in Uproot's files");
		}
		if (code) return new Owner(compat, "third-party mods collide in code");
		return new Owner(data, "data collision between mods: a datapack or config override");
	}
}
