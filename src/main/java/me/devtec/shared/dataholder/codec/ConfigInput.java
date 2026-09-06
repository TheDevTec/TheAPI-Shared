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
	private final Reader reader;
	private final char[] buffer = new char[32768];
	private int pos, limit;
	long offset, line = 1, column;
	final String format;

	ConfigInput(Reader r, String f) {
		reader = r;
		format = f;
	}

	int peek() throws IOException {
		if (limit < 0)
			return -1;
		if (pos == limit) {
			if (Thread.currentThread().isInterrupted())
				throw new InterruptedIOException("Config operation interrupted");
			limit = reader.read(buffer);
			pos = 0;
			if (limit < 0)
				return -1;
		}
		return buffer[pos];
	}

	int read() throws IOException {
		int c = peek();
		if (c != -1) {
			pos++;
			offset++;
			if (c == '\n') {
				line++;
				column = 0;
			} else
				column++;
		}
		return c;
	}

	void spaces() throws IOException {
		while (peek() >= 0 && peek() <= 32)
			read();
	}

	void expect(int c) throws IOException {
		if (read() != c)
			throw error("Expected '" + (char) c + "'");
	}

	ConfigParseException error(String message) {
		return new ConfigParseException(format, offset, line, column, message);
	}

	String line() throws IOException {
		if (peek() < 0)
			return null;
		StringContainer b = new StringContainer();
		int c;
		while ((c = read()) >= 0 && c != '\n') {
			if (c != '\r')
				b.append((char) c);
			if (b.length() > 16 * 1024 * 1024)
				throw error("Line exceeds materialization limit");
		}
		return b.toString();
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}
}
