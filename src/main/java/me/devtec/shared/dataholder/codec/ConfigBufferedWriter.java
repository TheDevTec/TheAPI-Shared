package me.devtec.shared.dataholder.codec;

import java.io.IOException;
import java.io.Writer;

/**
 * Operation-owned buffer; Config serialization does not share writers between
 * threads.
 */
public final class ConfigBufferedWriter extends Writer {

	private static final int CAPACITY = 32768;

	private final Writer output;
	private final char[] buffer = new char[CAPACITY];

	private int position;
	private boolean closed;

	public ConfigBufferedWriter(Writer output) {
		if (output == null)
			throw new NullPointerException("output");
		this.output = output;
	}

	private void checkOpen() throws IOException {
		if (closed)
			throw new IOException("Config writer is closed");
	}

	private void drain() throws IOException {
		if (position == 0)
			return;

		output.write(buffer, 0, position);
		position = 0;
	}

	@Override
	public void write(int value) throws IOException {
		checkOpen();

		if (position == CAPACITY)
			drain();

		buffer[position++] = (char) value;
	}

	@Override
	public void write(char[] value, int offset, int length) throws IOException {
		checkOpen();

		if (value == null)
			throw new NullPointerException("value");
		if (offset < 0 || length < 0 || offset > value.length - length)
			throw new IndexOutOfBoundsException();
		if (length == 0)
			return;

		int remaining = CAPACITY - position;

		if (length <= remaining) {
			System.arraycopy(value, offset, buffer, position, length);
			position += length;
			return;
		}

		if (position != 0) {
			System.arraycopy(value, offset, buffer, position, remaining);
			position = CAPACITY;
			offset += remaining;
			length -= remaining;
			drain();
		}

		if (length >= CAPACITY) {
			int direct = length - length % CAPACITY;

			output.write(value, offset, direct);

			offset += direct;
			length -= direct;
		}

		if (length != 0) {
			System.arraycopy(value, offset, buffer, 0, length);
			position = length;
		}
	}

	@Override
	public void write(String value, int offset, int length) throws IOException {
		checkOpen();

		if (value == null)
			throw new NullPointerException("value");
		if (offset < 0 || length < 0 || offset > value.length() - length)
			throw new IndexOutOfBoundsException();
		if (length == 0)
			return;

		while (length > 0) {
			int available = CAPACITY - position;

			if (available == 0) {
				drain();
				available = CAPACITY;
			}

			int count = Math.min(length, available);

			value.getChars(offset, offset + count, buffer, position);

			position += count;
			offset += count;
			length -= count;
		}
	}

	@Override
	public void flush() throws IOException {
		checkOpen();
		drain();
		output.flush();
	}

	@Override
	public void close() throws IOException {
		if (closed)
			return;

		try {
			drain();
		} finally {
			closed = true;
			output.close();
		}
	}
}