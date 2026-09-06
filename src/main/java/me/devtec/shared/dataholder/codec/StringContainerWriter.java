package me.devtec.shared.dataholder.codec;

import java.io.Writer;

import me.devtec.shared.dataholder.StringContainer;

/**
 * Character output for String APIs: no intermediate UTF-8 array and decode
 * pass.
 */
public final class StringContainerWriter extends Writer {
	private final StringContainer buffer = new StringContainer(256);
	private final long limit = Math.min(Integer.MAX_VALUE - 8, Runtime.getRuntime().maxMemory() / 8);

	private void reserve(int length) {
		if (length < 0 || (long) buffer.length() + length > limit)
			throw new IllegalStateException("Config output exceeds in-memory limit; use save() or saveTo()");
	}

	@Override
	public void write(int c) {
		reserve(1);
		buffer.append((char) c);
	}

	@Override
	public void write(char[] value, int offset, int length) {
		reserve(length);
		buffer.append(value, offset, length);
	}

	@Override
	public void write(String value, int offset, int length) {
		reserve(length);
		buffer.append(value, offset, offset + length);
	}

	@Override
	public void write(String value) {
		reserve(value.length());
		buffer.append(value);
	}

	@Override
	public Writer append(CharSequence value) {
		if (value == null)
			value = "null";
		reserve(value.length());
		buffer.append(value);
		return this;
	}

	@Override
	public Writer append(CharSequence value, int start, int end) {
		if (value == null)
			value = "null";
		reserve(end - start);
		buffer.append(value, start, end);
		return this;
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}

	@Override
	public String toString() {
		return buffer.toString();
	}

	public StringContainer container() {
		return buffer;
	}
}