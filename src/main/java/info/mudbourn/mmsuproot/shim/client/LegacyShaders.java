package info.mudbourn.mmsuproot.shim.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brings the GLSL of a mod built for 26.2 or older to what 26.3's shader compiler takes. 26.3 compiles to SPIR-V: a source is
 * {@code #version 330} with {@code GL_ARB_separate_shader_objects}, every stage input and output has an explicit location,
 * {@code #moj_import} is {@code #include}, and the shared uniform blocks come from Minecraft's own includes (a mod's hand copy
 * of {@code DynamicTransforms} lists its members in 26.2's order, which is not the layout 26.3 uploads). A source that is
 * already in the 26.3 shape is returned as it is.
 */
public final class LegacyShaders {
	private static final Pattern VERSION = Pattern.compile("(?m)^[ \\t]*#version[ \\t]+(1\\d\\d|[23]\\d\\d)(?:[ \\t]+core)?[ \\t]*$");
	private static final Pattern IMPORT = Pattern.compile("#moj_import");
	private static final Pattern INTERFACE = Pattern.compile(
			"(?m)^([ \\t]*)((?:(?:flat|smooth|noperspective)[ \\t]+)?)(in|out)([ \\t]+[A-Za-z_][A-Za-z0-9_]*[ \\t]+[A-Za-z_][A-Za-z0-9_]*(?:\\[[^\\]]*\\])?[ \\t]*;)");
	private static final Pattern EXPLICIT = Pattern.compile("layout[ \\t]*\\([^)]*\\blocation[ \\t]*=[ \\t]*(\\d+)[^)]*\\)[ \\t]*(?:(?:flat|smooth|noperspective)[ \\t]+)?(in|out)\\b");
	private static final Pattern LAYOUT_ALONE = Pattern.compile("[ \\t]*layout[ \\t]*\\([^)]*\\)[ \\t]*\\r?\\n?");
	private static final Pattern DYNAMIC_TRANSFORMS = block("DynamicTransforms");
	private static final Pattern PROJECTION = block("Projection");

	private LegacyShaders() { }

	private static Pattern block(String name) {
		return Pattern.compile("layout[ \\t]*\\([ \\t]*std140[ \\t]*\\)[ \\t]*uniform[ \\t]+" + name + "[ \\t\\r\\n]*\\{[^}]*\\}[ \\t]*;");
	}

	/** A shader file of a mod's {@code shaders/} folder, in 26.3 form. */
	public static String shader(String source) {
		Matcher version = VERSION.matcher(source);
		boolean old = IMPORT.matcher(source).find();
		if (version.find()) {
			int v = Integer.parseInt(version.group(1));
			if (v >= 330 && !old) return source;
			old = true;
		}
		if (!old) return source;
		String out = source;
		if (version.reset().find()) {
			out = version.replaceFirst("#version 330\n#extension GL_ARB_separate_shader_objects : require");
		}
		out = include(out);
		out = DYNAMIC_TRANSFORMS.matcher(out).replaceAll("#include <minecraft:dynamictransforms.glsl>");
		out = PROJECTION.matcher(out).replaceAll("#include <minecraft:projection.glsl>");
		return locations(out);
	}

	/** An include file of a mod's {@code shaders/include/} folder. */
	public static String include(String source) {
		return IMPORT.matcher(source).replaceAll("#include");
	}

	/** Gives each input and output declared at file level, and without a location, the next free one of its kind in declaration order. */
	private static String locations(String source) {
		Matcher m = INTERFACE.matcher(source);
		StringBuilder sb = new StringBuilder();
		int in = nextLocation(source, "in"), out = nextLocation(source, "out");
		while (m.find()) {
			if (depthAt(source, m.start()) != 0 || hasLayout(source, m.start())) continue;
			int location = m.group(3).equals("in") ? in++ : out++;
			m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + "layout(location = " + location + ") " + m.group(2) + m.group(3) + m.group(4)));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/** The first location past every explicit one a file declares for {@code in} or {@code out} at file level. */
	private static int nextLocation(String source, String kind) {
		Matcher m = EXPLICIT.matcher(source);
		int next = 0;
		while (m.find()) {
			if (m.group(2).equals(kind) && depthAt(source, m.start()) == 0) next = Math.max(next, Integer.parseInt(m.group(1)) + 1);
		}
		return next;
	}

	private static int depthAt(String s, int end) {
		int depth = 0;
		for (int i = 0; i < end; i++) {
			char c = s.charAt(i);
			if (c == '{') depth++;
			else if (c == '}') depth--;
		}
		return depth;
	}

	/**
	 * Whether the declaration that starts at {@code at} already has a layout qualifier: one in front of it on its own line, or one that is
	 * alone on the line before it (the pattern only sees whole declarations). A layout that ends another declaration does not count.
	 */
	private static boolean hasLayout(String s, int at) {
		int from = s.lastIndexOf('\n', Math.max(0, at - 1)) + 1;
		if (s.substring(from, at).contains("layout")) return true;
		if (from == 0) return false;
		int before = s.lastIndexOf('\n', from - 2) + 1;
		return LAYOUT_ALONE.matcher(s.substring(before, from)).matches();
	}
}
