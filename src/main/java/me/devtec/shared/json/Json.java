package me.devtec.shared.json;

import java.util.Arrays;
import java.util.Map;

import me.devtec.shared.json.custom.CustomJsonReader;
import me.devtec.shared.json.custom.CustomJsonWriter;

public class Json {

	private static final DataReader[] EMPTY_READERS = {};
	private static final DataWriter[] EMPTY_WRITERS = {};

	private static volatile DataReader[] readers = EMPTY_READERS;
	private static volatile DataWriter[] writers = EMPTY_WRITERS;

	private static JReader reader = new CustomJsonReader();
	private static JWriter writer = new CustomJsonWriter();

	public static void init(JReader reader, JWriter writer) {
		Json.reader = reader;
		Json.writer = writer;
	}

	public static Object processDataReaders(Map<String, Object> map) {
		final DataReader[] readers = Json.readers;

		for (final DataReader reader : readers) {
			if (!reader.isAllowed(map))
				continue;

			final Object result = reader.read(map);
			if (result != null)
				return result;
		}

		return null;
	}

	public static Map<String, Object> processDataWriters(Object obj) {
		final DataWriter[] writers = Json.writers;
		Map<String, Object> result = null;

		for (final DataWriter writer : writers) {
			if (!writer.isAllowed(obj))
				continue;

			result = writer.write(obj);
			if (result != null && !result.isEmpty())
				return result;
		}

		return result;
	}

	public static JReader reader() {
		return reader;
	}

	public static JWriter writer() {
		return writer;
	}

	public static JReader setReader(JReader reader) {
		return Json.reader = reader;
	}

	public static JWriter setWriter(JWriter writer) {
		return Json.writer = writer;
	}

	public static synchronized void registerDataReader(DataReader reader) {
		final DataReader[] current = readers;
		final DataReader[] next = Arrays.copyOf(current, current.length + 1);

		next[current.length] = reader;
		readers = next;
	}

	public static synchronized void unregisterDataReader(DataReader reader) {
		final DataReader[] current = readers;

		for (int i = 0; i < current.length; ++i) {
			if (!equals(current[i], reader))
				continue;

			final int newLength = current.length - 1;

			if (newLength == 0) {
				readers = EMPTY_READERS;
				return;
			}

			final DataReader[] next = new DataReader[newLength];

			if (i != 0)
				System.arraycopy(current, 0, next, 0, i);

			if (i != newLength)
				System.arraycopy(current, i + 1, next, i, newLength - i);

			readers = next;
			return;
		}
	}

	public static synchronized void registerDataWriter(DataWriter writer) {
		final DataWriter[] current = writers;
		final DataWriter[] next = Arrays.copyOf(current, current.length + 1);

		next[current.length] = writer;
		writers = next;
	}

	public static synchronized void unregisterDataWriter(DataWriter writer) {
		final DataWriter[] current = writers;

		for (int i = 0; i < current.length; ++i) {
			if (!equals(current[i], writer))
				continue;

			final int newLength = current.length - 1;

			if (newLength == 0) {
				writers = EMPTY_WRITERS;
				return;
			}

			final DataWriter[] next = new DataWriter[newLength];

			if (i != 0)
				System.arraycopy(current, 0, next, 0, i);

			if (i != newLength)
				System.arraycopy(current, i + 1, next, i, newLength - i);

			writers = next;
			return;
		}
	}

	private static boolean equals(Object first, Object second) {
		return first == second || first != null && first.equals(second);
	}

	public interface DataReader {
		boolean isAllowed(Map<String, Object> map);

		Object read(Map<String, Object> map);
	}

	public interface DataWriter {
		boolean isAllowed(Object object);

		Map<String, Object> write(Object object);
	}
}