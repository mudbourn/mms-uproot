package info.mudbourn.mmsuproot.disable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads {@code uproot/disable.txt}. One rule per line, {@code #} starts a comment, and a
 * {@code |} separates the rule from its reason (the text the report shows):
 * <pre>
 * stub  CLASS                 | reason
 * noop  CLASS METHOD DESC     | reason      (DESC may be * for every overload)
 * skip  REGISTRY ID           | reason      (REGISTRY is the path, e.g. entity_type)
 * mixin CLASS                 | reason      (a mixin class of a mod, left out of its mixin config)
 * </pre>
 * Class names take slashes or dots. The names are those the class has when the rule runs, so a
 * descriptor that mentions Minecraft uses the 26.3 names; only mod types and {@code *} are safe
 * to write before that is checked.
 */
final class RuleFile {
	private RuleFile() { }

	static List<Rule> load(String resource) {
		List<Rule> rules = new ArrayList<>();
		try (InputStream in = RuleFile.class.getResourceAsStream(resource)) {
			if (in == null) return rules;
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			int n = 0;
			while ((line = r.readLine()) != null) {
				n++;
				int hash = line.indexOf('#');
				if (hash >= 0) line = line.substring(0, hash);
				line = line.strip();
				if (line.isEmpty()) continue;
				int bar = line.indexOf('|');
				String reason = bar < 0 ? "no reason given" : line.substring(bar + 1).strip();
				String[] p = (bar < 0 ? line : line.substring(0, bar)).strip().split("\\s+");
				Rule rule = parse(p, reason, n);
				if (rule != null) rules.add(rule);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + resource, e);
		}
		return rules;
	}

	private static Rule parse(String[] p, String reason, int line) {
		AtomicInteger hits = new AtomicInteger();
		switch (p[0]) {
			case "stub":
				if (p.length == 2) return new Rule(Rule.Kind.STUB, internal(p[1]), "", "", reason, line, hits);
				break;
			case "noop":
				if (p.length == 4 && !p[2].startsWith("<")) return new Rule(Rule.Kind.NOOP, internal(p[1]), p[2], p[3], reason, line, hits);
				break;
			case "skip":
				if (p.length == 3) return new Rule(Rule.Kind.SKIP, p[1], p[2], "", reason, line, hits);
				break;
			case "mixin":
				if (p.length == 2) return new Rule(Rule.Kind.MIXIN, internal(p[1]), "", "", reason, line, hits);
				break;
			default:
		}
		throw new IllegalStateException("disable.txt line " + line + ": cannot read the rule");
	}

	private static String internal(String name) {
		return name.replace('.', '/');
	}
}
