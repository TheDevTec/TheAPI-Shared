package me.devtec.shared.dataholder.codec;

import java.io.IOException;
import java.io.Writer;

import me.devtec.shared.dataholder.StringContainer;

/** Operation-owned buffer; Config serialization does not share writers between threads. */
public final class ConfigBufferedWriter extends Writer {
	private static final int CAPACITY = 32768;
	private final Writer output;
	private final StringContainer buffer = new StringContainer(CAPACITY);
	private boolean closed;

	public ConfigBufferedWriter(Writer output) {
		this.output = output;
	}

	private void checkOpen() throws IOException {
		if (closed) throw new IOException("Config writer is closed");
	}

	private void drain() throws IOException {
		if (buffer.length() == 0) return;
		output.write(buffer.getValueWithoutTrim(), 0, buffer.length());
		buffer.clear();
	}

	@Override
	public void write(int value) throws IOException {
		checkOpen();
		if (buffer.length() == CAPACITY) drain();
		buffer.append((char) value);
	}

	@Override
	public void write(char[] value, int offset, int length) throws IOException {
		checkOpen();
		if (offset < 0 || length < 0 || offset > value.length - length) throw new IndexOutOfBoundsException();
		if (length >= CAPACITY) {
			drain();
			output.write(value, offset, length);
			return;
		}
		if (length > CAPACITY - buffer.length()) drain();
		buffer.append(value, offset, length);
	}

	@Override
	public void write(String value, int offset, int length) throws IOException {
		checkOpen();
		if (offset < 0 || length < 0 || offset > value.length() - length) throw new IndexOutOfBoundsException();
		// Bound the encoder's temporary character copy, even for huge String values.
		while (length > 0) {
			if (buffer.length() == CAPACITY) drain();
			int count = Math.min(length, CAPACITY - buffer.length());
			buffer.append(value, offset, offset + count);
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
		if (closed) return;
		try { drain(); } finally {
			closed = true;
			output.close();
		}
	}
}
