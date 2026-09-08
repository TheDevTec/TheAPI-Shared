package me.devtec.shared.dataholder.codec;

import java.io.Writer;

import me.devtec.shared.dataholder.StringContainer;

/**
 * Character output for String APIs: no intermediate UTF-8 array and decode
 * pass.
 */
public final class StringContainerWriter extends Writer {

	private final StringContainer buffer = new StringContainer(256);
	private final long limit = Math.min(Integer.MAX_VALUE - 8L, Runtime.getRuntime().maxMemory() / 8L);

	private void reserve(int length) {
		if (length < 0 || (long) buffer.length() + length > limit)
			throw new IllegalStateException("Config output exceeds in-memory limit; use save() or saveTo()");
	}

	@Override
	public void write(int value) {
		reserve(1);
		buffer.append((char) value);
	}

	@Override
	public void write(char[] value, int offset, int length) {
		if (value == null)
			throw new NullPointerException("value");
		if (offset < 0 || length < 0 || offset > value.length - length)
			throw new IndexOutOfBoundsException();
		if (length == 0)
			return;

		reserve(length);
		buffer.append(value, offset, length);
	}

	@Override
	public void write(String value, int offset, int length) {
		if (value == null)
			throw new NullPointerException("value");
		if (offset < 0 || length < 0 || offset > value.length() - length)
			throw new IndexOutOfBoundsException();
		if (length == 0)
			return;

		reserve(length);
		buffer.append(value, offset, offset + length);
	}

	@Override
	public void write(String value) {
		if (value == null)
			throw new NullPointerException("value");

		int length = value.length();

		if (length == 0)
			return;

		reserve(length);
		buffer.append(value);
	}

	@Override
	public Writer append(char value) {
		reserve(1);
		buffer.append(value);
		return this;
	}

	@Override
	public Writer append(CharSequence value) {
		if (value == null)
			value = "null";

		int length = value.length();

		if (length == 0)
			return this;

		reserve(length);
		buffer.append(value);
		return this;
	}

	@Override
	public Writer append(CharSequence value, int start, int end) {
		if (value == null)
			value = "null";

		if (start < 0 || end < start || end > value.length())
			throw new IndexOutOfBoundsException();

		int length = end - start;

		if (length == 0)
			return this;

		reserve(length);
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