package me.devtec.shared.json;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import me.devtec.shared.dataholder.store.DiskValueStore.StoredList;
import me.devtec.shared.dataholder.store.DiskValueStore.StoredMap;

/**
 * Bounded-memory JSON writer used by huge Config saves.
 *
 * <p>
 * Unlike JWriter#simpleWrite this writer never has to construct one giant
 * result String. Maps/collections are emitted incrementally and StoredMap /
 * StoredList can copy untouched raw JSON ranges straight from their backing
 * storage.
 * </p>
 */
public final class StreamingJsonWriter {

	private StreamingJsonWriter() {
	}

	public static void write(Writer output, Object value) throws IOException {
		if (output == null)
			throw new NullPointerException("output");

		writeValue(output, value);
	}

	public static void writeValue(Writer output, Object value) throws IOException {
		if (value == null) {
			output.write("null");
			return;
		}

		if (value instanceof StoredMap || value instanceof StoredList) {
			me.devtec.shared.dataholder.codec.ConfigWriter.jsonValue(value, output, 0,
					new java.util.IdentityHashMap<>());
			return;
		}

		if ((value instanceof CharSequence) || (value instanceof Character)) {
			writeString(output, value.toString());
			return;
		}

		if (value instanceof Boolean || value instanceof Number) {
			output.write(String.valueOf(value));
			return;
		}

		/*
		 * Preserve the current JsonUtils contract:
		 *
		 * HashMap/LinkedHashMap and ArrayList/LinkedList are ordinary JSON containers
		 * and can therefore be streamed directly.
		 */
		if (value instanceof HashMap) {
			writeMap(output, (Map<?, ?>) value);
			return;
		}

		if (value instanceof ArrayList || value instanceof LinkedList) {
			writeCollection(output, (Collection<?>) value);
			return;
		}

		/*
		 * Keep custom Map/Collection implementations, arrays, enums and custom objects
		 * on the configured JWriter conversion path first.
		 */
		Object converted = null;

		try {
			converted = Json.writer().writeWithoutParse(value);
		} catch (Exception ignored) {
		}

		if (converted != null && converted != value) {
			writeValue(output, converted);
			return;
		}

		if (value instanceof Map) {
			writeMap(output, (Map<?, ?>) value);
			return;
		}

		if (value instanceof Collection) {
			writeCollection(output, (Collection<?>) value);
			return;
		}

		if (value.getClass().isArray()) {
			writeArray(output, value);
			return;
		}

		final String encoded = Json.writer().simpleWrite(value);

		if (encoded == null)
			output.write("null");
		else
			output.write(encoded);
	}

	public static void writeMap(Writer output, Map<?, ?> map) throws IOException {
		output.write('{');

		boolean first = true;

		for (Entry<?, ?> entry : map.entrySet()) {
			if (first)
				first = false;
			else
				output.write(',');

			writeString(output, String.valueOf(entry.getKey()));
			output.write(':');
			writePreparedChild(output, entry.getValue());
		}

		output.write('}');
	}

	public static void writeCollection(Writer output, Collection<?> collection) throws IOException {
		output.write('[');

		final Iterator<?> iterator = collection.iterator();

		if (iterator.hasNext()) {
			writePreparedChild(output, iterator.next());

			while (iterator.hasNext()) {
				output.write(',');
				writePreparedChild(output, iterator.next());
			}
		}

		output.write(']');
	}

	private static void writeArray(Writer output, Object array) throws IOException {
		output.write('[');

		final int length = Array.getLength(array);

		if (length != 0) {
			writePreparedChild(output, Array.get(array, 0));

			for (int i = 1; i < length; ++i) {
				output.write(',');
				writePreparedChild(output, Array.get(array, i));
			}
		}

		output.write(']');
	}

	private static void writePreparedChild(Writer output, Object value) throws IOException {
		if (value == null || value instanceof StoredMap || value instanceof StoredList || value instanceof CharSequence
				|| value instanceof Character || value instanceof Boolean || value instanceof Number
				|| value instanceof HashMap || value instanceof ArrayList || value instanceof LinkedList) {
			writeValue(output, value);
			return;
		}

		Object converted = null;

		try {
			converted = Json.writer().writeWithoutParse(value);
		} catch (Exception ignored) {
		}

		writeValue(output, converted != null && converted != value ? converted : value);
	}

	public static void writeString(Writer output, String text) throws IOException {
		output.write('"');

		if (text != null && !text.isEmpty()) {
			final int length = text.length();
			int start = 0;

			for (int i = 0; i < length; ++i) {
				final char c = text.charAt(i);

				if (c != '"' && c != '\\' && c >= 0x20)
					continue;

				if (i > start)
					output.write(text, start, i - start);

				switch (c) {
				case '"':
					output.write("\\\"");
					break;
				case '\\':
					output.write("\\\\");
					break;
				case '\b':
					output.write("\\b");
					break;
				case '\f':
					output.write("\\f");
					break;
				case '\n':
					output.write("\\n");
					break;
				case '\r':
					output.write("\\r");
					break;
				case '\t':
					output.write("\\t");
					break;
				default:
					output.write("\\u00");
					output.write(hex(c >>> 4 & 0xF));
					output.write(hex(c & 0xF));
					break;
				}

				start = i + 1;
			}

			if (start < length)
				output.write(text, start, length - start);
		}

		output.write('"');
	}

	private static char hex(int value) {
		return (char) (value < 10 ? '0' + value : 'a' + value - 10);
	}
}