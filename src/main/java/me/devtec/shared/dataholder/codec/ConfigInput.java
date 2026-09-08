package me.devtec.shared.dataholder.codec;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;

import me.devtec.shared.dataholder.StringContainer;

/**
 * Operation-owned bounded input; no ThreadLocal retention or whole-file String.
 */
final class ConfigInput implements Closeable {

	private static final int BUFFER_SIZE = 32768;
	private static final int MAX_LINE_LENGTH = 16 * 1024 * 1024;

	private final Reader reader;
	private final char[] buffer = new char[BUFFER_SIZE];

	private int pos;
	private int limit;

	long offset;
	long line = 1;
	long column;

	final String format;

	ConfigInput(Reader reader, String format) {
		this.reader = reader;
		this.format = format;
	}

	private boolean refill() throws IOException {
		if (limit < 0)
			return false;

		if (Thread.currentThread().isInterrupted())
			throw new InterruptedIOException("Config operation interrupted");

		do
			limit = reader.read(buffer);
		while (limit == 0);

		pos = 0;
		return limit > 0;
	}

	int peek() throws IOException {
		if (pos < limit)
			return buffer[pos];

		return refill() ? buffer[0] : -1;
	}

	int read() throws IOException {
		if (pos >= limit && !refill())
			return -1;

		char c = buffer[pos++];

		offset++;

		if (c == '\n') {
			line++;
			column = 0;
		} else
			column++;

		return c;
	}

	void spaces() throws IOException {
		while (true) {
			if (pos >= limit && !refill())
				return;

			int start = pos;

			while (pos < limit && buffer[pos] <= 32) {
				char c = buffer[pos++];

				if (c == '\n') {
					line++;
					column = 0;
				} else
					column++;
			}

			offset += pos - start;

			if (pos < limit)
				return;
		}
	}

	void expect(int expected) throws IOException {
		if (read() != expected)
			throw error("Expected '" + (char) expected + "'");
	}

	ConfigParseException error(String message) {
		return new ConfigParseException(format, offset, line, column, message);
	}

	String line() throws IOException {
		if (pos >= limit && !refill())
			return null;

		StringContainer result = null;
		int materialized = 0;

		while (true) {
			int start = pos;

			while (pos < limit) {
				char c = buffer[pos];

				if (c == '\n' || c == '\r')
					break;

				pos++;
			}

			int length = pos - start;

			if ((long) materialized + length > MAX_LINE_LENGTH)
				throw error("Line exceeds materialization limit");

			if (pos < limit) {
				char separator = buffer[pos];

				if (separator == '\n') {
					if (result == null) {
						String line = length == 0 ? "" : new String(buffer, start, length);

						pos++;
						offset += length + 1L;
						lineIncrement();

						return line;
					}

					if (length != 0)
						result.append(buffer, start, length);

					materialized += length;
					offset += length + 1L;
					pos++;
					lineIncrement();

					return result.toString();
				}

				// Preserve the original behaviour: CR is ignored in the
				// materialized line, but is not considered a line break.
				if (result == null)
					result = new StringContainer(Math.max(32, length + 16));

				if (length != 0)
					result.append(buffer, start, length);

				materialized += length;
				offset += length + 1L;
				column += length + 1L;
				pos++; // skip '\r'

				continue;
			}

			// Buffer ended. If the Reader is also at EOF, avoid allocating a
			// StringContainer for the common single-buffer case.
			if (result == null) {
				if (!refill()) {
					offset += length;
					column += length;
					return length == 0 ? "" : new String(buffer, start, length);
				}

				result = new StringContainer(Math.max(64, length + 32));

				if (length != 0)
					result.append(buffer, start, length);

				materialized += length;
				offset += length;
				column += length;

				continue;
			}

			if (length != 0)
				result.append(buffer, start, length);

			materialized += length;
			offset += length;
			column += length;

			if (!refill())
				return result.toString();
		}
	}

	private void lineIncrement() {
		line++;
		column = 0;
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}
}