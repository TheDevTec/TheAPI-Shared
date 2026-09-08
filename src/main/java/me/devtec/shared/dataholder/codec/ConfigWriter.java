package me.devtec.shared.dataholder.codec;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.store.ConfigStore;
import me.devtec.shared.dataholder.store.ValueRef;
import me.devtec.shared.json.Json;

/**
 * Streams the canonical hierarchy directly. No second nested Map or full-path
 * JSON keys.
 */
public final class ConfigWriter {
	public static final ConfigWriter INSTANCE = new ConfigWriter();
	private static final String INDENT = "                                                                ";

	private ConfigWriter() {
	}

	public void write(ConfigDocument d, String format, Writer out) throws IOException {
		if (format == null)
			throw new IOException("Unknown Config output format: " + format);
		switch (format) {
		case "json":
			jsonObject(d.storage.store(), d.storage.store().firstChild(0), out, 0);
			break;
		case "yaml":
		case "empty":
			comments(d.header, out, 0);
			yaml(d.storage.store(), d.storage.store().firstChild(0), out, 0);
			comments(d.footer, out, 0);
			break;
		case "properties":
		case "toml": {
			comments(d.header, out, 0);
			ConfigStore s = d.storage.store();
			for (int n = s.firstEntry(); n != 0; n = s.nextEntry(n)) {
				comments(s.getComments(n), out, 0);
				out.write(s.path(n));
				out.write(" = ");
				s.writeJsonValue(n, out);
				after(s.getComment(n), out);
				out.write('\n');
			}
			comments(d.footer, out, 0);
			break;
		}
		default:
			throw new IOException("Unknown Config output format: " + format);
		}
	}

	private void jsonObject(ConfigStore s, int firstNode, Writer out, int depth) throws IOException {
		if (depth > 256)
			throw new IOException("Config nesting exceeds 256");
		out.write('{');
		boolean first = true;
		for (int n = firstNode; n != 0; n = s.nextSibling(n)) {
			if (Thread.currentThread().isInterrupted())
				throw new InterruptedIOException("Config save interrupted");
			if (!first)
				out.write(',');
			first = false;
			quoted(s.segment(n), out);
			out.write(':');
			int child = s.firstChild(n);
			if (child != 0)
				jsonObject(s, child, out, depth + 1);
			else
				s.writeJsonValue(n, out);
		}
		out.write('}');
	}

	private void yaml(ConfigStore s, int firstNode, Writer out, int depth) throws IOException {
		if (depth > 256)
			throw new IOException("Config nesting exceeds 256");
		for (int n = firstNode; n != 0; n = s.nextSibling(n)) {
			if (Thread.currentThread().isInterrupted())
				throw new InterruptedIOException("Config save interrupted");
			comments(s.getComments(n), out, depth);
			String comment = s.getComment(n);
			indent(out, depth);
			String key = s.segment(n);
			if (key.indexOf('#') >= 0 || key.indexOf(':') >= 0 || key.startsWith(" "))
				quoted(key, out);
			else
				out.write(key);
			out.write(':');
			int child = s.firstChild(n);
			if (child != 0) {
				after(comment, out);
				out.write('\n');
				yaml(s, child, out, depth + 1);
				continue;
			}
			ValueRef ref = s.value(n);
			if (ref.largeText()) {
				out.write(' ');
				ref.writeJson(out);
				after(comment, out);
				out.write('\n');
				continue;
			}
			Object value = ref.get();
			if (value instanceof String) {
				String text = (String) value;
				if (text.indexOf('\n') < 0) {
					out.write(' ');
					quoted(text, out);
					after(comment, out);
					out.write('\n');
					continue;
				}
				out.write(" |");
				after(comment, out);
				out.write('\n');
				for (int start = 0, end; start < text.length(); start = end) {
					end = start;
					while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r')
						end++;
					indent(out, depth + 1);
					out.write(text, start, end - start);
					out.write('\n');
					if (end < text.length() && text.charAt(end++) == '\r' && end < text.length()
							&& text.charAt(end) == '\n')
						end++;
				}
			} else {
				out.write(' ');
				// Memory values are already resolved above. Disk composites retain
				// their direct streaming writer instead of walking lazy collections.
				if (ref instanceof ValueRef.Memory)
					jsonValue(value, out, 0, null);
				else
					ref.writeJson(out);
				after(comment, out);
				out.write('\n');
			}
		}
	}

	private static void after(String comment, Writer out) throws IOException {
		if (comment != null) {
			out.write(' ');
			out.write(comment);
		}
	}

	private static void indent(Writer out, int depth) throws IOException {
		int remaining = depth * 2;
		while (remaining > 0) {
			int count = Math.min(remaining, INDENT.length());
			out.write(INDENT, 0, count);
			remaining -= count;
		}
	}

	private static void comments(Collection<String> c, Writer out, int depth) throws IOException {
		if (c != null)
			for (String s : c) {
				indent(out, depth);
				out.write(s);
				out.write('\n');
			}
	}

	public static void quoted(CharSequence value, Writer out) throws IOException {
		out.write(34);
		int start = 0, length = value.length();
		for (int i = 0; i < length; i++) {
			char c = value.charAt(i);
			if (c != 34 && c != 92 && c >= 32)
				continue;
			writeRange(value, start, i, out);
			out.write(92);
			switch (c) {
			case 34:
			case 92:
				out.write(c);
				break;
			case 8:
				out.write('b');
				break;
			case 12:
				out.write('f');
				break;
			case 10:
				out.write('n');
				break;
			case 13:
				out.write('r');
				break;
			case 9:
				out.write('t');
				break;
			default:
				out.write("u00");
				out.write(Character.forDigit(c >>> 4 & 15, 16));
				out.write(Character.forDigit(c & 15, 16));
			}
			start = i + 1;
		}
		writeRange(value, start, length, out);
		out.write(34);
	}

	private static void writeRange(CharSequence value, int start, int end, Writer out) throws IOException {
		if (start == end)
			return;
		if (value instanceof String)
			out.write((String) value, start, end - start);
		else if (value instanceof me.devtec.shared.dataholder.StringContainer)
			out.write(((me.devtec.shared.dataholder.StringContainer) value).getValueWithoutTrim(), start, end - start);
		else
			out.append(value, start, end);
	}

	public static void escaped(char[] buffer, int length, Writer out) throws IOException {
		int start = 0;
		for (int i = 0; i < length; i++) {
			char c = buffer[i];
			if (c == '"' || c == '\\' || c < 32) {
				if (i > start)
					out.write(buffer, start, i - start);
				switch (c) {
				case '"':
					out.write("\\\"");
					break;
				case '\\':
					out.write("\\\\");
					break;
				case '\n':
					out.write("\\n");
					break;
				case '\r':
					out.write("\\r");
					break;
				case '\t':
					out.write("\\t");
					break;
				default:
					out.write("\\u00");
					out.write(Character.forDigit(c >>> 4 & 15, 16));
					out.write(Character.forDigit(c & 15, 16));
				}
				start = i + 1;
			}
		}
		if (start < length)
			out.write(buffer, start, length - start);
	}

	public static void jsonValue(Object v, Writer out, int depth, IdentityHashMap<Object, Boolean> seen)
			throws IOException {
		if (depth > 256)
			throw new IOException("Config value nesting exceeds 256");

		if (v instanceof ValueRef) {
			((ValueRef) v).writeJson(out);
			return;
		}
		if (v == null) {
			out.write("null");
			return;
		}
		if (v instanceof CharSequence) {
			quoted((CharSequence) v, out);
			return;
		}
		if (v instanceof Character) {
			quoted(v.toString(), out);
			return;
		}
		if (v instanceof Boolean) {
			out.write(v.toString());
			return;
		}
		if (v instanceof Number) {
			if (v instanceof Double && !Double.isFinite((Double) v) || v instanceof Float && !Float.isFinite((Float) v))
				throw new IOException("Non-finite Config number");
			out.write(v.toString());
			return;
		}
		if (seen == null)
			seen = new IdentityHashMap<>();
		if (seen.put(v, Boolean.TRUE) != null)
			throw new IOException("Cyclic Config value");
		if (v instanceof Map) {
			out.write('{');
			boolean first = true;
			for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
				if (!first)
					out.write(',');
				first = false;
				quoted(String.valueOf(e.getKey()), out);
				out.write(':');
				jsonValue(e.getValue(), out, depth + 1, seen);
			}
			out.write('}');
		} else if (v instanceof Iterable) {
			out.write('[');
			boolean first = true;
			for (Object x : (Iterable<?>) v) {
				if (!first)
					out.write(',');
				first = false;
				jsonValue(x, out, depth + 1, seen);
			}
			out.write(']');
		} else if (v.getClass().isArray()) {
			out.write('[');
			for (int i = 0; i < java.lang.reflect.Array.getLength(v); i++) {
				if (i > 0)
					out.write(',');
				jsonValue(java.lang.reflect.Array.get(v, i), out, depth + 1, seen);
			}
			out.write(']');
		} else
			out.write(Json.writer().write(v));
		seen.remove(v);
	}
}
