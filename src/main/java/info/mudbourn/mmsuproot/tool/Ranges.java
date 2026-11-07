package info.mudbourn.mmsuproot.tool;

import java.util.List;

/**
 * A small evaluator for Fabric version ranges, enough to tell whether a {@code minecraft} or
 * {@code java} dependency admits one given version. Anything it cannot read counts as satisfied,
 * so the tool never rewrites a range it does not understand.
 */
public final class Ranges {
	private Ranges() { }

	/** {@code spec} is a range string or a list of them (any may match). */
	public static boolean admits(Object spec, int[] version) {
		if (spec instanceof List<?> list) {
			for (Object o : list) if (admits(o, version)) return true;
			return list.isEmpty();
		}
		if (!(spec instanceof String s)) return true;
		try {
			for (String token : s.trim().split("\\s+")) {
				if (!token.isEmpty() && !token(token, version)) return false;
			}
			return true;
		} catch (RuntimeException e) {
			return true;
		}
	}

	private static boolean token(String t, int[] v) {
		if (t.equals("*") || t.equals("x") || t.equals("X")) return true;
		String op = "=";
		for (String o : new String[] {">=", "<=", ">", "<", "=", "~", "^"}) {
			if (t.startsWith(o)) { op = o; t = t.substring(o.length()); break; }
		}
		int cut = t.indexOf('-');
		if (cut >= 0) t = t.substring(0, cut);
		cut = t.indexOf('+');
		if (cut >= 0) t = t.substring(0, cut);

		String[] parts = t.split("\\.");
		int[] spec = new int[3];
		int fixed = 0;
		for (int i = 0; i < parts.length && i < 3; i++) {
			if (parts[i].equals("x") || parts[i].equals("X") || parts[i].equals("*")) break;
			spec[i] = Integer.parseInt(parts[i]);
			fixed++;
		}

		int cmp = compare(v, spec, 3);
		return switch (op) {
		case ">=" -> cmp >= 0;
		case "<=" -> compare(v, spec, fixed == 0 ? 3 : fixed) <= 0;
		case ">" -> cmp > 0;
		case "<" -> cmp < 0;
		case "~" -> cmp >= 0 && v[0] == spec[0] && (fixed < 2 || v[1] == spec[1]);
		case "^" -> cmp >= 0 && v[0] == spec[0];
		default -> compare(v, spec, fixed == parts.length ? 3 : fixed) == 0;
		};
	}

	private static int compare(int[] a, int[] b, int n) {
		for (int i = 0; i < n; i++) {
			if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
		}
		return 0;
	}
}
