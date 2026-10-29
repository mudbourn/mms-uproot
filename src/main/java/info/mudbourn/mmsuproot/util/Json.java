package info.mudbourn.mmsuproot.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer. Uproot's tool class runs without Loader or Gson on the class
 * path, so it cannot borrow either. Objects become {@link LinkedHashMap}, arrays {@link List},
 * numbers {@link Double} or {@link Long}.
 */
public final class Json {
	private final String src;
	private int pos;

	private Json(String src) {
		this.src = src;
	}

	public static Object parse(String text) {
		Json json = new Json(text);
		json.skip();
		Object value = json.value();
		json.skip();
		if (json.pos != text.length()) throw json.error("trailing data");
		return value;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> parseObject(String text) {
		return (Map<String, Object>) parse(text);
	}

	private RuntimeException error(String what) {
		return new IllegalArgumentException("JSON " + what + " at " + pos);
	}

	private void skip() {
		while (pos < src.length()) {
			char c = src.charAt(pos);
			if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '﻿') {
				pos++;
			} else if (c == '/' && src.startsWith("//", pos)) { // tolerated: some mod configs carry comments
				while (pos < src.length() && src.charAt(pos) != '\n') pos++;
			} else if (c == '/' && src.startsWith("/*", pos)) {
				int end = src.indexOf("*/", pos + 2);
				pos = end < 0 ? src.length() : end + 2;
			} else {
				break;
			}
		}
	}

	private Object value() {
		if (pos >= src.length()) throw error("unexpected end");
		char c = src.charAt(pos);
		switch (c) {
		case '{': return object();
		case '[': return array();
		case '"': return string();
		case 't': expect("true"); return Boolean.TRUE;
		case 'f': expect("false"); return Boolean.FALSE;
		case 'n': expect("null"); return null;
		default: return number();
		}
	}

	private void expect(String word) {
		if (!src.startsWith(word, pos)) throw error("expected " + word);
		pos += word.length();
	}

	private Map<String, Object> object() {
		Map<String, Object> map = new LinkedHashMap<>();
		pos++;
		skip();
		if (src.charAt(pos) == '}') { pos++; return map; }
		while (true) {
			skip();
			String key = string();
			skip();
			if (src.charAt(pos++) != ':') throw error("expected ':'");
			skip();
			map.put(key, value());
			skip();
			char c = src.charAt(pos++);
			if (c == '}') return map;
			if (c != ',') throw error("expected ',' or '}'");
		}
	}

	private List<Object> array() {
		List<Object> list = new ArrayList<>();
		pos++;
		skip();
		if (src.charAt(pos) == ']') { pos++; return list; }
		while (true) {
			skip();
			list.add(value());
			skip();
			char c = src.charAt(pos++);
			if (c == ']') return list;
			if (c != ',') throw error("expected ',' or ']'");
		}
	}

	private String string() {
		if (src.charAt(pos) != '"') throw error("expected string");
		pos++;
		StringBuilder sb = new StringBuilder();
		while (true) {
			char c = src.charAt(pos++);
			if (c == '"') return sb.toString();
			if (c != '\\') { sb.append(c); continue; }
			c = src.charAt(pos++);
			switch (c) {
			case 'n': sb.append('\n'); break;
			case 't': sb.append('\t'); break;
			case 'r': sb.append('\r'); break;
			case 'b': sb.append('\b'); break;
			case 'f': sb.append('\f'); break;
			case 'u': sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16)); pos += 4; break;
			default: sb.append(c);
			}
		}
	}

	private Object number() {
		int start = pos;
		while (pos < src.length() && "+-0123456789.eE".indexOf(src.charAt(pos)) >= 0) pos++;
		if (start == pos) throw error("unexpected character");
		String text = src.substring(start, pos);
		if (text.indexOf('.') < 0 && text.indexOf('e') < 0 && text.indexOf('E') < 0) return Long.parseLong(text);
		return Double.parseDouble(text);
	}

	public static String write(Object value) {
		StringBuilder sb = new StringBuilder();
		write(sb, value, 0);
		return sb.append('\n').toString();
	}

	private static void write(StringBuilder sb, Object value, int depth) {
		if (value == null) {
			sb.append("null");
		} else if (value instanceof String s) {
			quote(sb, s);
		} else if (value instanceof Map<?, ?> map) {
			if (map.isEmpty()) { sb.append("{}"); return; }
			sb.append("{\n");
			int i = 0;
			for (Map.Entry<?, ?> e : map.entrySet()) {
				indent(sb, depth + 1);
				quote(sb, String.valueOf(e.getKey()));
				sb.append(": ");
				write(sb, e.getValue(), depth + 1);
				sb.append(++i < map.size() ? ",\n" : "\n");
			}
			indent(sb, depth);
			sb.append('}');
		} else if (value instanceof List<?> list) {
			if (list.isEmpty()) { sb.append("[]"); return; }
			sb.append("[\n");
			for (int i = 0; i < list.size(); i++) {
				indent(sb, depth + 1);
				write(sb, list.get(i), depth + 1);
				sb.append(i + 1 < list.size() ? ",\n" : "\n");
			}
			indent(sb, depth);
			sb.append(']');
		} else if (value instanceof Double d && d == Math.rint(d) && !Double.isInfinite(d)) {
			sb.append(d.longValue());
		} else {
			sb.append(value);
		}
	}

	private static void indent(StringBuilder sb, int depth) {
		for (int i = 0; i < depth; i++) sb.append('\t');
	}

	private static void quote(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"': sb.append("\\\""); break;
			case '\\': sb.append("\\\\"); break;
			case '\n': sb.append("\\n"); break;
			case '\r': sb.append("\\r"); break;
			case '\t': sb.append("\\t"); break;
			default:
				if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
				else sb.append(c);
			}
		}
		sb.append('"');
	}
}
