package me.devtec.shared.dataholder.codec;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.store.ConfigStore;
import me.devtec.shared.dataholder.store.ValueRef;
import me.devtec.shared.json.Json;

/**
 * Streams the canonical hierarchy directly. No second nested Map or full-path
 * JSON keys.
 */
public final class ConfigWriter {

	public static final ConfigWriter INSTANCE = new ConfigWriter();

	private static final int MAX_DEPTH = 256;

	private static final char[] INDENT = new char[MAX_DEPTH * 2];

	private static final char[] HEX = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
			'f' };

	private static final String NULL = "null";
	private static final String TRUE = "true";
	private static final String FALSE = "false";

	static {
		java.util.Arrays.fill(INDENT, ' ');
	}

	private ConfigWriter() {
	}

	public void write(ConfigDocument document, String format, Writer out) throws IOException {
		if (format == null)
			throw new IOException("Unknown Config output format: null");

		ConfigStore store = document.storage.store();

		switch (format) {
		case "json":
			jsonObject(store, store.firstChild(0), out, 0);
			return;

		case "yaml":
		case "empty":
			comments(document.header, out, 0);
			yaml(store, store.firstChild(0), out, 0);
			comments(document.footer, out, 0);
			return;

		case "properties":
		case "toml":
			comments(document.header, out, 0);

			for (int node = store.firstEntry(); node != 0; node = store.nextEntry(node)) {
				checkInterrupted();

				comments(store.getComments(node), out, 0);

				out.write(store.path(node));
				out.write(" = ");

				store.writeJsonValue(node, out);

				after(store.getComment(node), out);
				out.write('\n');
			}

			comments(document.footer, out, 0);
			return;

		default:
			throw new IOException("Unknown Config output format: " + format);
		}
	}

	private void jsonObject(ConfigStore store, int firstNode, Writer out, int depth) throws IOException {
		if (depth > MAX_DEPTH)
			throw new IOException("Config nesting exceeds " + MAX_DEPTH);

		out.write('{');

		boolean first = true;

		for (int node = firstNode; node != 0; node = store.nextSibling(node)) {
			checkInterrupted();

			if (first)
				first = false;
			else
				out.write(',');

			quoted(store.segment(node), out);
			out.write(':');

			int child = store.firstChild(node);

			if (child != 0)
				jsonObject(store, child, out, depth + 1);
			else
				store.writeJsonValue(node, out);
		}

		out.write('}');
	}

	private void yaml(ConfigStore store, int firstNode, Writer out, int depth) throws IOException {
		if (depth > MAX_DEPTH)
			throw new IOException("Config nesting exceeds " + MAX_DEPTH);

		for (int node = firstNode; node != 0; node = store.nextSibling(node)) {
			checkInterrupted();

			comments(store.getComments(node), out, depth);

			String comment = store.getComment(node);

			indent(out, depth);

			String key = store.segment(node);

			if (needsQuotedKey(key))
				quoted(key, out);
			else
				out.write(key);

			out.write(':');

			int child = store.firstChild(node);

			if (child != 0) {
				after(comment, out);
				out.write('\n');

				yaml(store, child, out, depth + 1);
				continue;
			}

			ValueRef ref = store.value(node);
			if (ref.largeText()) {
				out.write(' ');
				ref.writeJson(out);
				after(comment, out);
				out.write('\n');
				continue;
			}

			Object value = ref.get();

			if (value instanceof String) {
				writeYamlString((String) value, comment, out, depth);
				continue;
			}

			if (isYamlContainer(value)) {
				String writtenValue = store.getWrittenValue(node);

				if (wasWrittenAsJson(writtenValue, value)) {
					out.write(' ');

					if (ref instanceof ValueRef.Memory)
						jsonValue(value, out, 0, null);
					else
						ref.writeJson(out);

					after(comment, out);
					out.write('\n');
				} else
					writeYamlContainerAfterKey(value, comment, out, depth, new IdentityHashMap<>());

				continue;
			}

			out.write(' ');

			if (ref instanceof ValueRef.Memory)
				jsonValue(value, out, 0, null);
			else
				ref.writeJson(out);

			after(comment, out);
			out.write('\n');
		}
	}

	private static boolean isYamlContainer(Object value) {
		return value instanceof Map || value instanceof Collection || value != null && value.getClass().isArray();
	}

	private static boolean wasWrittenAsJson(String writtenValue, Object value) {
		if (writtenValue == null)
			return false;

		int length = writtenValue.length();
		int i = 0;

		while (i < length && Character.isWhitespace(writtenValue.charAt(i)))
			++i;

		if (i >= length)
			return false;

		char first = writtenValue.charAt(i);

		if (value instanceof Map)
			return first == '{';

		return first == '[';
	}

	private static boolean isEmptyYamlContainer(Object value) {
		if (value instanceof Map)
			return ((Map<?, ?>) value).isEmpty();

		if (value instanceof Collection)
			return ((Collection<?>) value).isEmpty();

		return Array.getLength(value) == 0;
	}

	private static void writeYamlContainerAfterKey(Object value, String comment, Writer out, int depth,
			IdentityHashMap<Object, Boolean> seen) throws IOException {

		if (isEmptyYamlContainer(value)) {
			out.write(value instanceof Map ? " {}" : " []");
			after(comment, out);
			out.write('\n');
			return;
		}

		after(comment, out);
		out.write('\n');

		if (value instanceof Map)
			writeYamlMapBlock((Map<?, ?>) value, out, depth + 1, seen);
		else
			writeYamlSequenceBlock(value, out, depth, seen);
	}

	private static void writeYamlMapBlock(Map<?, ?> map, Writer out, int depth, IdentityHashMap<Object, Boolean> seen)
			throws IOException {

		if (depth > MAX_DEPTH)
			throw new IOException("Config value nesting exceeds " + MAX_DEPTH);

		if (seen.put(map, Boolean.TRUE) != null)
			throw new IOException("Cyclic Config value");

		try {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				checkInterrupted();

				indent(out, depth);
				writeYamlMapEntry(entry, out, depth, seen);
			}
		} finally {
			seen.remove(map);
		}
	}

	private static void writeYamlMapEntry(Map.Entry<?, ?> entry, Writer out, int depth,
			IdentityHashMap<Object, Boolean> seen) throws IOException {

		writeYamlKey(entry.getKey(), out);
		out.write(':');

		writeYamlValueAfterMapKey(entry.getValue(), out, depth, seen);
	}

	private static void writeYamlValueAfterMapKey(Object value, Writer out, int depth,
			IdentityHashMap<Object, Boolean> seen) throws IOException {

		if (!isYamlContainer(value)) {
			out.write(' ');
			writeYamlScalar(value, out);
			out.write('\n');
			return;
		}

		if (isEmptyYamlContainer(value)) {
			out.write(value instanceof Map ? " {}" : " []");
			out.write('\n');
			return;
		}

		out.write('\n');

		if (value instanceof Map)
			writeYamlMapBlock((Map<?, ?>) value, out, depth + 1, seen);
		else
			writeYamlSequenceBlock(value, out, depth, seen);
	}

	private static void writeYamlSequenceBlock(Object sequence, Writer out, int depth,
			IdentityHashMap<Object, Boolean> seen) throws IOException {

		if (depth > MAX_DEPTH)
			throw new IOException("Config value nesting exceeds " + MAX_DEPTH);

		if (seen.put(sequence, Boolean.TRUE) != null)
			throw new IOException("Cyclic Config value");

		try {
			if (sequence instanceof Collection) {
				for (Object value : (Collection<?>) sequence) {
					checkInterrupted();
					writeYamlSequenceItem(value, out, depth, seen);
				}

				return;
			}

			int length = Array.getLength(sequence);

			for (int i = 0; i < length; ++i) {
				checkInterrupted();
				writeYamlSequenceItem(Array.get(sequence, i), out, depth, seen);
			}
		} finally {
			seen.remove(sequence);
		}
	}

	private static void writeYamlSequenceItem(Object value, Writer out, int depth,
			IdentityHashMap<Object, Boolean> seen) throws IOException {

		/*
		 * Map inside of list:
		 *
		 * - key: subkey: value
		 */
		if (value instanceof Map && !((Map<?, ?>) value).isEmpty()) {
			writeYamlMapAsSequenceItem((Map<?, ?>) value, out, depth, seen);
			return;
		}

		indent(out, depth);
		out.write('-');

		if (!isYamlContainer(value)) {
			out.write(' ');
			writeYamlScalar(value, out);
			out.write('\n');
			return;
		}

		if (isEmptyYamlContainer(value)) {
			out.write(value instanceof Map ? " {}" : " []");
			out.write('\n');
			return;
		}

		out.write('\n');

		if (value instanceof Map)
			writeYamlMapBlock((Map<?, ?>) value, out, depth + 1, seen);
		else
			writeYamlSequenceBlock(value, out, depth + 1, seen);
	}

	private static void writeYamlMapAsSequenceItem(Map<?, ?> map, Writer out, int depth,
			IdentityHashMap<Object, Boolean> seen) throws IOException {

		if (depth + 1 > MAX_DEPTH)
			throw new IOException("Config value nesting exceeds " + MAX_DEPTH);

		if (seen.put(map, Boolean.TRUE) != null)
			throw new IOException("Cyclic Config value");

		try {
			boolean first = true;

			for (Map.Entry<?, ?> entry : map.entrySet()) {
				checkInterrupted();

				if (first) {
					indent(out, depth);
					out.write("- ");
					first = false;
				} else
					indent(out, depth + 1);

				writeYamlMapEntry(entry, out, depth + 1, seen);
			}
		} finally {
			seen.remove(map);
		}
	}

	private static void writeYamlKey(Object key, Writer out) throws IOException {
		String value = String.valueOf(key);

		if (needsQuotedKey(value))
			quoted(value, out);
		else
			out.write(value);
	}

	private static void writeYamlScalar(Object value, Writer out) throws IOException {
		jsonValue(value, out, 0, null);
	}

	private static boolean needsQuotedKey(String key) {
		int length = key.length();

		if (length == 0 || key.charAt(0) == ' ')
			return true;

		for (int i = 0; i < length; i++) {
			char c = key.charAt(i);

			if (c == '#' || c == ':')
				return true;
		}

		return false;
	}

	private static void writeYamlString(String text, String comment, Writer out, int depth) throws IOException {
		int length = text.length();
		int newline = -1;

		for (int i = 0; i < length; i++) {
			char c = text.charAt(i);

			if (c == '\n' || c == '\r') {
				newline = i;
				break;
			}
		}

		if (newline < 0) {
			out.write(' ');
			quoted(text, out);
			after(comment, out);
			out.write('\n');
			return;
		}

		out.write(" |");
		after(comment, out);
		out.write('\n');

		int start = 0;

		while (start < length) {
			int end = start;

			while (end < length) {
				char c = text.charAt(end);

				if (c == '\n' || c == '\r')
					break;

				end++;
			}

			indent(out, depth + 1);

			if (end > start)
				out.write(text, start, end - start);

			out.write('\n');

			if (end >= length)
				break;

			if (text.charAt(end++) == '\r' && end < length && text.charAt(end) == '\n')
				end++;

			start = end;
		}
	}

	private static void after(String comment, Writer out) throws IOException {
		if (comment == null || comment.isEmpty())
			return;

		out.write(' ');
		out.write(comment);
	}

	private static void indent(Writer out, int depth) throws IOException {
		if (depth <= 0)
			return;

		int length = depth << 1;

		if (length > INDENT.length)
			throw new IOException("Config nesting exceeds " + MAX_DEPTH);

		out.write(INDENT, 0, length);
	}

	private static void comments(Collection<String> comments, Writer out, int depth) throws IOException {
		if (comments == null || comments.isEmpty())
			return;

		for (String comment : comments) {
			indent(out, depth);

			if (comment != null)
				out.write(comment);

			out.write('\n');
		}
	}

	public static void quoted(CharSequence value, Writer out) throws IOException {
		out.write('"');

		int length = value.length();
		int start = 0;

		for (int i = 0; i < length; i++) {
			char c = value.charAt(i);

			if (c != '"' && c != '\\' && c >= 32)
				continue;

			if (i > start)
				writeRange(value, start, i, out);

			out.write('\\');

			switch (c) {
			case '"':
				out.write('"');
				break;

			case '\\':
				out.write('\\');
				break;

			case '\b':
				out.write('b');
				break;

			case '\f':
				out.write('f');
				break;

			case '\n':
				out.write('n');
				break;

			case '\r':
				out.write('r');
				break;

			case '\t':
				out.write('t');
				break;

			default:
				out.write('u');
				out.write('0');
				out.write('0');
				out.write(HEX[c >>> 4 & 15]);
				out.write(HEX[c & 15]);
				break;
			}

			start = i + 1;
		}

		if (start < length)
			writeRange(value, start, length, out);

		out.write('"');
	}

	private static void writeRange(CharSequence value, int start, int end, Writer out) throws IOException {
		int length = end - start;

		if (length <= 0)
			return;

		if (value instanceof String) {
			out.write((String) value, start, length);
			return;
		}

		if (value instanceof StringContainer) {
			out.write(((StringContainer) value).getValueWithoutTrim(), start, length);
			return;
		}

		out.append(value, start, end);
	}

	public static void escaped(char[] buffer, int length, Writer out) throws IOException {
		int start = 0;

		for (int i = 0; i < length; i++) {
			char c = buffer[i];

			if (c != '"' && c != '\\' && c >= 32)
				continue;

			if (i > start)
				out.write(buffer, start, i - start);

			out.write('\\');

			switch (c) {
			case '"':
				out.write('"');
				break;

			case '\\':
				out.write('\\');
				break;

			case '\b':
				out.write('b');
				break;

			case '\f':
				out.write('f');
				break;

			case '\n':
				out.write('n');
				break;

			case '\r':
				out.write('r');
				break;

			case '\t':
				out.write('t');
				break;

			default:
				out.write('u');
				out.write('0');
				out.write('0');
				out.write(HEX[c >>> 4 & 15]);
				out.write(HEX[c & 15]);
				break;
			}

			start = i + 1;
		}

		if (start < length)
			out.write(buffer, start, length - start);
	}

	public static void jsonValue(Object value, Writer out, int depth, IdentityHashMap<Object, Boolean> seen)
			throws IOException {
		if (depth > MAX_DEPTH)
			throw new IOException("Config value nesting exceeds " + MAX_DEPTH);

		if (value == null) {
			out.write(NULL);
			return;
		}

		if (value instanceof ValueRef) {
			((ValueRef) value).writeJson(out);
			return;
		}

		if (value instanceof String) {
			quoted((String) value, out);
			return;
		}

		if (value instanceof StringContainer) {
			quoted((StringContainer) value, out);
			return;
		}

		if (value instanceof CharSequence) {
			quoted((CharSequence) value, out);
			return;
		}

		if (value instanceof Character) {
			out.write('"');
			writeQuotedCharacter((Character) value, out);
			out.write('"');
			return;
		}

		if (value instanceof Boolean) {
			out.write((Boolean) value ? TRUE : FALSE);
			return;
		}

		if (value instanceof Integer) {
			out.write(Integer.toString((Integer) value));
			return;
		}

		if (value instanceof Long) {
			out.write(Long.toString((Long) value));
			return;
		}

		if (value instanceof Short) {
			out.write(Short.toString((Short) value));
			return;
		}

		if (value instanceof Byte) {
			out.write(Byte.toString((Byte) value));
			return;
		}

		if (value instanceof Double) {
			double number = (Double) value;

			if (Double.isNaN(number) || Double.isInfinite(number))
				throw new IOException("Non-finite Config number");

			out.write(Double.toString(number));
			return;
		}

		if (value instanceof Float) {
			float number = (Float) value;

			if (Float.isNaN(number) || Float.isInfinite(number))
				throw new IOException("Non-finite Config number");

			out.write(Float.toString(number));
			return;
		}

		if (value instanceof Number) {
			out.write(value.toString());
			return;
		}

		if (seen == null)
			seen = new IdentityHashMap<>();

		if (seen.put(value, Boolean.TRUE) != null)
			throw new IOException("Cyclic Config value");

		try {
			if (value instanceof Map) {
				writeMap((Map<?, ?>) value, out, depth, seen);
				return;
			}

			if (value instanceof Iterable) {
				writeIterable((Iterable<?>) value, out, depth, seen);
				return;
			}

			Class<?> type = value.getClass();

			if (type.isArray()) {
				writeArray(value, type, out, depth, seen);
				return;
			}

			out.write(Json.writer().write(value));
		} finally {
			seen.remove(value);
		}
	}

	private static void writeMap(Map<?, ?> map, Writer out, int depth, IdentityHashMap<Object, Boolean> seen)
			throws IOException {
		out.write('{');

		boolean first = true;

		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (first)
				first = false;
			else
				out.write(',');

			Object key = entry.getKey();

			if (key instanceof CharSequence)
				quoted((CharSequence) key, out);
			else
				quoted(String.valueOf(key), out);

			out.write(':');

			jsonValue(entry.getValue(), out, depth + 1, seen);
		}

		out.write('}');
	}

	private static void writeIterable(Iterable<?> iterable, Writer out, int depth,
			IdentityHashMap<Object, Boolean> seen) throws IOException {
		out.write('[');

		boolean first = true;

		for (Object value : iterable) {
			if (first)
				first = false;
			else
				out.write(',');

			jsonValue(value, out, depth + 1, seen);
		}

		out.write(']');
	}

	private static void writeArray(Object value, Class<?> type, Writer out, int depth,
			IdentityHashMap<Object, Boolean> seen) throws IOException {
		if (type == byte[].class) {
			byte[] array = (byte[]) value;

			out.write('[');

			for (int i = 0; i < array.length; i++) {
				if (i != 0)
					out.write(',');

				out.write(Byte.toString(array[i]));
			}

			out.write(']');
			return;
		}

		if (type == short[].class) {
			short[] array = (short[]) value;

			out.write('[');

			for (int i = 0; i < array.length; i++) {
				if (i != 0)
					out.write(',');

				out.write(Short.toString(array[i]));
			}

			out.write(']');
			return;
		}

		if (type == int[].class) {
			int[] array = (int[]) value;

			out.write('[');

			for (int i = 0; i < array.length; i++) {
				if (i != 0)
					out.write(',');

				out.write(Integer.toString(array[i]));
			}

			out.write(']');
			return;
		}

		if (type == long[].class) {
			long[] array = (long[]) value;

			out.write('[');

			for (int i = 0; i < array.length; i++) {
				if (i != 0)
					out.write(',');

				out.write(Long.toString(array[i]));
			}

			out.write(']');
			return;
		}

		if (type == boolean[].class) {
			boolean[] array = (boolean[]) value;

			out.write('[');

			for (int i = 0; i < array.length; i++) {
				if (i != 0)
					out.write(',');

				out.write(array[i] ? TRUE : FALSE);
			}

			out.write(']');
			return;
		}

		if (type == char[].class) {
			char[] array = (char[]) value;

			out.write('[');

			for (int i = 0; i < array.length; i++) {
				if (i != 0)
					out.write(',');

				out.write('"');
				writeQuotedCharacter(array[i], out);
				out.write('"');
			}

			out.write(']');
			return;
		}

		if (type == float[].class) {
			float[] array = (float[]) value;

			out.write('[');

			for (int i = 0; i < array.length; i++) {
				if (i != 0)
					out.write(',');

				float number = array[i];

				if (Float.isNaN(number) || Float.isInfinite(number))
					throw new IOException("Non-finite Config number");

				out.write(Float.toString(number));
			}

			out.write(']');
			return;
		}

		if (type == double[].class) {
			double[] array = (double[]) value;

			out.write('[');

			for (int i = 0; i < array.length; i++) {
				if (i != 0)
					out.write(',');

				double number = array[i];

				if (Double.isNaN(number) || Double.isInfinite(number))
					throw new IOException("Non-finite Config number");

				out.write(Double.toString(number));
			}

			out.write(']');
			return;
		}

		if (value instanceof Object[]) {
			Object[] array = (Object[]) value;

			out.write('[');

			for (int i = 0; i < array.length; i++) {
				if (i != 0)
					out.write(',');

				jsonValue(array[i], out, depth + 1, seen);
			}

			out.write(']');
			return;
		}

		int length = Array.getLength(value);

		out.write('[');

		for (int i = 0; i < length; i++) {
			if (i != 0)
				out.write(',');

			jsonValue(Array.get(value, i), out, depth + 1, seen);
		}

		out.write(']');
	}

	private static void writeQuotedCharacter(char c, Writer out) throws IOException {
		switch (c) {
		case '"':
			out.write("\\\"");
			return;

		case '\\':
			out.write("\\\\");
			return;

		case '\b':
			out.write("\\b");
			return;

		case '\f':
			out.write("\\f");
			return;

		case '\n':
			out.write("\\n");
			return;

		case '\r':
			out.write("\\r");
			return;

		case '\t':
			out.write("\\t");
			return;

		default:
			if (c >= 32) {
				out.write(c);
				return;
			}

			out.write("\\u00");
			out.write(HEX[c >>> 4 & 15]);
			out.write(HEX[c & 15]);
		}
	}

	private static void checkInterrupted() throws InterruptedIOException {
		if (Thread.currentThread().isInterrupted())
			throw new InterruptedIOException("Config save interrupted");
	}
}