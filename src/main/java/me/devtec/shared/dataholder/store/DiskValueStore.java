package me.devtec.shared.dataholder.store;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import me.devtec.shared.json.Json;

/**
 * Typed logical values. Linked composite records allow unknown-length streaming
 * builders.
 */
public final class DiskValueStore {
	public Object getValue(long offset) {
		try {
			return read(offset, null);
		} catch (IOException e) {
			throw failure(e);
		}
	}

	public boolean isNull(long offset) {
		try {
			return file.getByte(offset) == 0;
		} catch (IOException e) {
			throw failure(e);
		}
	}

	public void writeJsonValue(long offset, Writer output) throws IOException {
		writeJson(offset, output, 0);
	}

	long writeMetadata(NodeMetadata metadata) {
		try {
			long raw = metadata.writtenValue == null ? 0 : writeValue(metadata.writtenValue, 0);
			long after = metadata.commentAfterValue == null ? 0 : writeValue(metadata.commentAfterValue, 0);
			long comments = metadata.comments == null ? 0 : writeValue(metadata.comments, 0);
			if (raw == 0 && after == 0 && comments == 0)
				return 0;
			long pos = file.length();
			file.putLong(pos, raw);
			file.putLong(pos + 8, after);
			file.putLong(pos + 16, comments);
			file.putLong(pos + 24,
					MemoryEstimator.estimate(metadata.writtenValue)
							+ MemoryEstimator.estimate(metadata.commentAfterValue)
							+ MemoryEstimator.estimate(metadata.comments));
			return pos;
		} catch (IOException e) {
			throw failure(e);
		}
	}

	private Object metadataField(long metadata, int field) {
		if (metadata == 0)
			return null;
		try {
			long offset = file.getLong(metadata + field * 8L);
			return offset == 0 ? null : read(offset, null);
		} catch (IOException e) {
			throw failure(e);
		}
	}

	String writtenValue(long metadata) {
		return (String) metadataField(metadata, 0);
	}

	String comment(long metadata) {
		return (String) metadataField(metadata, 1);
	}

	@SuppressWarnings("unchecked")
	List<String> comments(long metadata) {
		Collection<String> comments = (Collection<String>) metadataField(metadata, 2);
		return comments == null ? null : new ArrayList<>(comments);
	}

	long metadataHeap(long metadata) {
		if (metadata == 0)
			return 0;
		try {
			return file.getLong(metadata + 24);
		} catch (IOException e) {
			throw failure(e);
		}
	}

	private final DiskGeneration generation;
	private final PagedFile file;

	DiskValueStore(DiskGeneration generation, int pages) throws IOException {
		this.generation = generation;
		file = generation.file("values.dat", pages);
		file.putLong(0, 0);
	}

	long bytes() {
		return file.length();
	}

	long cacheBytes() {
		return file.cacheBytes();
	}

	void flush() throws IOException {
		file.flush();
	}

	private static IllegalStateException failure(IOException e) {
		return new IllegalStateException("Config value I/O failed", e);
	}

	public ValueRef write(Object value) {
		try {
			return new Stored(writeValue(value, 0), MemoryEstimator.estimate(value));
		} catch (IOException e) {
			throw failure(e);
		}
	}

	ValueRef reference(long offset, long estimate) {
		return new Stored(offset, estimate);
	}

	long offset(ValueRef value) {
		if (value instanceof Stored) {
			Stored stored = (Stored) value;
			if (stored.owner() == this)
				return stored.offset;
			try {
				return copy(stored.owner(), stored.offset, 0);
			} catch (IOException e) {
				throw failure(e);
			}
		}
		return ((Stored) write(value.get())).offset;
	}

	private long copy(DiskValueStore source, long p, int depth) throws IOException {
		if (depth > 256)
			throw new IOException("Value nesting exceeds 256");
		int type = source.file.getByte(p);
		if (type == 9)
			try (Reader r = source.textReader(p + 1)) {
				return ((Stored) text(r)).offset;
			}
		if (type == 10 || type == 11) {
			long target = composite(type);
			for (long n = source.file.getLong(p + 9); n != 0; n = source.file.getLong(n))
				append(target, type == 11 ? copy(source, source.file.getLong(n + 8), depth + 1) : 0,
						copy(source, source.file.getLong(n + 16), depth + 1));
			return target;
		}
		return writeValue(source.read(p, null), depth);
	}

	private long writeValue(Object v, int depth) throws IOException {
		if (depth > 256)
			throw new IOException("Config value nesting exceeds 256");
		if (v instanceof Stored)
			return ((Stored) v).owner() == this ? ((Stored) v).offset
					: copy(((Stored) v).owner(), ((Stored) v).offset, depth);
		if (v instanceof StoredList) {
			StoredList list = (StoredList) v;
			return list.owner() == this ? list.ref : copy(list.owner(), list.ref, depth);
		}
		if (v instanceof StoredMap) {
			StoredMap map = (StoredMap) v;
			return map.owner() == this ? map.ref : copy(map.owner(), map.ref, depth);
		}
		long p = file.length();
		if (v == null) {
			file.putByte(p, 0);
			return p;
		}
		if (v instanceof Boolean) {
			file.putByte(p, (Boolean) v ? 2 : 1);
			return p;
		}
		if (v instanceof Number) {
			int type = v instanceof Integer ? 3
					: v instanceof Long ? 4
							: v instanceof Double ? 5
									: v instanceof Float ? 6 : v instanceof Short ? 7 : v instanceof Byte ? 8 : 15;
			if (type == 15) {
				file.putByte(p, 15);
				text(v.toString());
				return p;
			}
			file.putByte(p, type);
			file.putLong(p + 1, v instanceof Double ? Double.doubleToLongBits((Double) v)
					: v instanceof Float ? Float.floatToIntBits((Float) v) : ((Number) v).longValue());
			return p;
		}
		if (v instanceof CharSequence || v instanceof Character) {
			file.putByte(p, 9);
			text(v.toString());
			return p;
		}
		if (v instanceof Map) {
			long ref = composite(11);
			for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet())
				append(ref, writeValue(e.getKey(), depth + 1), writeValue(e.getValue(), depth + 1));
			return ref;
		}
		if (v instanceof Iterable) {
			long ref = composite(10);
			for (Object x : (Iterable<?>) v)
				append(ref, 0, writeValue(x, depth + 1));
			return ref;
		}
		if (v.getClass().isArray()) {
			long ref = composite(10);
			for (int i = 0; i < java.lang.reflect.Array.getLength(v); i++)
				append(ref, 0, writeValue(java.lang.reflect.Array.get(v, i), depth + 1));
			return ref;
		}
		file.putByte(p, 12);
		text(Json.writer().simpleWrite(v));
		return p;
	}

	private void text(String s) throws IOException {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		long p = file.length();
		file.putLong(p, bytes.length);
		file.write(p + 8, bytes, 0, bytes.length);
	}

	private String textAt(long p) throws IOException {
		long len = file.getLong(p);
		if (len < 0 || len > Math.min(Integer.MAX_VALUE - 8, Runtime.getRuntime().maxMemory() / 8))
			throw new IOException("Config String is too large to materialize; use streaming save");
		byte[] b = new byte[(int) len];
		file.read(p + 8, b, 0, b.length);
		return new String(b, StandardCharsets.UTF_8);
	}

	public long composite(boolean map) {
		try {
			return composite(map ? 11 : 10);
		} catch (IOException e) {
			throw failure(e);
		}
	}

	private long composite(int type) throws IOException {
		long p = file.length();
		file.putByte(p, type);
		file.putLong(p + 1, 0);
		file.putLong(p + 9, 0);
		file.putLong(p + 17, 0);
		return p;
	}

	public void appendValue(long ref, Object key, Object value) {
		try {
			append(ref, key == null ? 0 : writeValue(key, 0), writeValue(value, 0));
		} catch (IOException e) {
			throw failure(e);
		}
	}

	private void append(long ref, long key, long value) throws IOException {
		long p = file.length(), last = file.getLong(ref + 17);
		file.putLong(p, 0);
		file.putLong(p + 8, key);
		file.putLong(p + 16, value);
		if (last == 0)
			file.putLong(ref + 9, p);
		else
			file.putLong(last, p);
		file.putLong(ref + 17, p);
		file.putLong(ref + 1, file.getLong(ref + 1) + 1);
	}

	public ValueRef finish(long ref, long estimate) {
		return new Stored(ref, estimate);
	}

	/**
	 * Appends exact UTF-8 bytes, patching the length only after the stream
	 * completes.
	 */
	public ValueRef text(Reader reader) throws IOException {
		final long p = file.length();
		file.putByte(p, 9);
		file.putLong(p + 1, 0);
		OutputStream stream = new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				file.putByte(file.length(), b);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				file.write(file.length(), b, off, len);
			}
		};
		try (Writer writer = new OutputStreamWriter(stream,
				StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT))) {
			char[] buffer = new char[16384];
			long chars = 0;
			int n;
			while ((n = reader.read(buffer)) != -1) {
				if (Thread.currentThread().isInterrupted())
					throw new InterruptedIOException("Config text interrupted");
				writer.write(buffer, 0, n);
				chars += n;
			}
			writer.close();
			file.putLong(p + 1, file.length() - p - 9);
			return new Stored(p, 40 + 2 * chars);
		}
	}

	public TextBuilder textBuilder() throws IOException {
		return new TextBuilder();
	}

	public final class TextBuilder extends Writer {
		private final long offset;
		private final Writer writer;
		private long chars;

		TextBuilder() throws IOException {
			offset = file.length();
			file.putByte(offset, 9);
			file.putLong(offset + 1, 0);
			writer = new BufferedWriter(new OutputStreamWriter(new OutputStream() {
				@Override
				public void write(int b) throws IOException {
					file.putByte(file.length(), b);
				}

				@Override
				public void write(byte[] b, int off, int len) throws IOException {
					file.write(file.length(), b, off, len);
				}
			}, StandardCharsets.UTF_8.newEncoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)), 16384);
		}

		@Override
		public void write(char[] b, int off, int len) throws IOException {
			writer.write(b, off, len);
			chars += len;
		}

		@Override
		public void write(String s, int off, int len) throws IOException {
			writer.write(s, off, len);
			chars += len;
		}

		@Override
		public void write(int c) throws IOException {
			writer.write(c);
			chars++;
		}

		@Override
		public void flush() throws IOException {
			writer.flush();
		}

		@Override
		public void close() throws IOException {
			writer.close();
		}

		public ValueRef finish() throws IOException {
			close();
			file.putLong(offset + 1, file.length() - offset - 9);
			return new Stored(offset, 40 + chars * 2);
		}
	}

	private Reader textReader(final long p) throws IOException {
		final long length = file.getLong(p);
		if (length < 0 || p + 8 > file.length() - length)
			throw new IOException("Invalid Config text bounds");
		return new InputStreamReader(new InputStream() {
			long pos = p + 8, remaining = length;

			@Override
			public int read() throws IOException {
				if (remaining == 0)
					return -1;
				remaining--;
				return file.getByte(pos++);
			}

			@Override
			public int read(byte[] b, int off, int count) throws IOException {
				if (count == 0)
					return 0;
				if (remaining == 0)
					return -1;
				int n = (int) Math.min(count, remaining);
				file.read(pos, b, off, n);
				pos += n;
				remaining -= n;
				return n;
			}
		}, StandardCharsets.UTF_8);
	}

	private void writeJson(long p, Writer out, int depth) throws IOException {
		if (depth > 256)
			throw new IOException("Config value nesting exceeds 256");
		int type = file.getByte(p);
		if (type == 9) {
			if (file.getLong(p + 1) <= 32768) {
				me.devtec.shared.dataholder.codec.ConfigWriter.quoted(textAt(p + 1), out);
				return;
			}
			out.write('"');
			try (Reader r = textReader(p + 1)) {
				char[] buffer = new char[16384];
				int n;
				while ((n = r.read(buffer)) != -1) {
					if (Thread.currentThread().isInterrupted())
						throw new InterruptedIOException("Config save interrupted");
					me.devtec.shared.dataholder.codec.ConfigWriter.escaped(buffer, n, out);
				}
			}
			out.write('"');
		} else if (type == 10 || type == 11) {
			out.write(type == 10 ? '[' : '{');
			boolean first = true;
			for (long n = file.getLong(p + 9); n != 0; n = file.getLong(n)) {
				if (!first)
					out.write(',');
				first = false;
				if (type == 11) {
					Object key = read(file.getLong(n + 8), null);
					me.devtec.shared.dataholder.codec.ConfigWriter.quoted(String.valueOf(key), out);
					out.write(':');
				}
				writeJson(file.getLong(n + 16), out, depth + 1);
			}
			out.write(type == 10 ? ']' : '}');
		} else
			me.devtec.shared.dataholder.codec.ConfigWriter.jsonValue(read(p, null), out, depth, null);
	}

	private Object read(long p, Runnable changed) throws IOException {
		switch (file.getByte(p)) {
		case 0:
			return null;
		case 1:
			return false;
		case 2:
			return true;
		case 3:
			return (int) file.getLong(p + 1);
		case 4:
			return file.getLong(p + 1);
		case 5:
			return Double.longBitsToDouble(file.getLong(p + 1));
		case 6:
			return Float.intBitsToFloat((int) file.getLong(p + 1));
		case 7:
			return (short) file.getLong(p + 1);
		case 8:
			return (byte) file.getLong(p + 1);
		case 9:
			return textAt(p + 1);
		case 10:
			return new StoredList(p, changed);
		case 11:
			return new StoredMap(p, changed);
		case 12:
			return Json.reader().read(textAt(p + 1));
		case 15:
			return new java.math.BigDecimal(textAt(p + 1));
		default:
			throw new IOException("Unknown Config value type");
		}
	}

	void retainExternal(Object value) {
		if (value instanceof StoredList || value instanceof StoredMap)
			generation.external();
	}

	private final class Stored implements ValueRef {
		final long offset, estimate;

		Stored(long p, long e) {
			offset = p;
			estimate = e;
		}

		DiskValueStore owner() {
			return DiskValueStore.this;
		}

		@Override
		public Object get() {
			try {
				return read(offset, null);
			} catch (IOException e) {
				throw failure(e);
			}
		}

		@Override
		public long estimatedHeap() {
			return estimate;
		}

		@Override
		public void writeJson(Writer writer) throws IOException {
			DiskValueStore.this.writeJson(offset, writer, 0);
		}

		@Override
		public boolean largeText() {
			try {
				return file.getByte(offset) == 9 && file.getLong(offset + 1) > 32768;
			} catch (IOException e) {
				throw failure(e);
			}
		}

		@Override
		public boolean requiresExternalValue() {
			return true;
		}
	}

	private long item(long ref, int index) throws IOException {
		long count = file.getLong(ref + 1);
		if (index < 0 || index >= count)
			throw new IndexOutOfBoundsException();
		long n = file.getLong(ref + 9);
		while (index-- > 0)
			n = file.getLong(n);
		return n;
	}

	private int count(long ref) {
		try {
			long n = file.getLong(ref + 1);
			if (n > Integer.MAX_VALUE)
				throw new IllegalStateException("Collection exceeds Java List size");
			return (int) n;
		} catch (IOException e) {
			throw failure(e);
		}
	}

	public final class StoredList extends AbstractList<Object> {
		final long ref;
		final Runnable changed;

		StoredList(long r, Runnable c) {
			ref = r;
			changed = c;
		}

		DiskValueStore owner() {
			return DiskValueStore.this;
		}

		@Override
		public int size() {
			return count(ref);
		}

		@Override
		public Object get(int i) {
			try {
				return read(file.getLong(item(ref, i) + 16), changed);
			} catch (IOException e) {
				throw failure(e);
			}
		}

		@Override
		public Object set(int i, Object value) {
			try {
				long p = item(ref, i);
				Object old = read(file.getLong(p + 16), changed);
				long v = writeValue(value, 0);
				file.putLong(p + 16, v);
				if (changed != null)
					changed.run();
				return old;
			} catch (IOException e) {
				throw failure(e);
			}
		}

		@Override
		public void add(int index, Object value) {
			try {
				int size = size();
				if (index < 0 || index > size)
					throw new IndexOutOfBoundsException();
				long v = writeValue(value, 0);
				if (index == size)
					append(ref, 0, v);
				else {
					long p = file.length(), next = item(ref, index);
					file.putLong(p, next);
					file.putLong(p + 8, 0);
					file.putLong(p + 16, v);
					if (index == 0)
						file.putLong(ref + 9, p);
					else
						file.putLong(item(ref, index - 1), p);
					file.putLong(ref + 1, size + 1);
				}
				modCount++;
				if (changed != null)
					changed.run();
			} catch (IOException e) {
				throw failure(e);
			}
		}

		@Override
		public Object remove(int index) {
			try {
				long p = item(ref, index), next = file.getLong(p), prev = index == 0 ? 0 : item(ref, index - 1);
				Object old = read(file.getLong(p + 16), changed);
				if (prev == 0)
					file.putLong(ref + 9, next);
				else
					file.putLong(prev, next);
				if (next == 0)
					file.putLong(ref + 17, prev);
				file.putLong(ref + 1, size() - 1);
				modCount++;
				if (changed != null)
					changed.run();
				return old;
			} catch (IOException e) {
				throw failure(e);
			}
		}

		@Override
		public Iterator<Object> iterator() {
			try {
				final long first = file.getLong(ref + 9);
				return new Iterator<Object>() {
					long next = first;

					@Override
					public boolean hasNext() {
						return next != 0;
					}

					@Override
					public Object next() {
						if (next == 0)
							throw new NoSuchElementException();
						try {
							long p = next;
							next = file.getLong(p);
							return read(file.getLong(p + 16), changed);
						} catch (IOException e) {
							throw failure(e);
						}
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			} catch (IOException e) {
				throw failure(e);
			}
		}
	}

	public final class StoredMap extends AbstractMap<Object, Object> {
		final long ref;
		final Runnable changed;

		StoredMap(long r, Runnable c) {
			ref = r;
			changed = c;
		}

		DiskValueStore owner() {
			return DiskValueStore.this;
		}

		@Override
		public Object remove(Object key) {
			try {
				long previous = 0;
				for (long p = file.getLong(ref + 9); p != 0; p = file.getLong(p)) {
					if (Objects.equals(key, read(file.getLong(p + 8), changed))) {
						long next = file.getLong(p);
						Object old = read(file.getLong(p + 16), changed);
						if (previous == 0)
							file.putLong(ref + 9, next);
						else
							file.putLong(previous, next);
						if (next == 0)
							file.putLong(ref + 17, previous);
						file.putLong(ref + 1, size() - 1);
						if (changed != null)
							changed.run();
						return old;
					}
					previous = p;
				}
				return null;
			} catch (IOException e) {
				throw failure(e);
			}
		}

		@Override
		public void clear() {
			try {
				file.putLong(ref + 1, 0);
				file.putLong(ref + 9, 0);
				file.putLong(ref + 17, 0);
				if (changed != null)
					changed.run();
			} catch (IOException e) {
				throw failure(e);
			}
		}

		@Override
		public int size() {
			return count(ref);
		}

		@Override
		public Object put(Object key, Object value) {
			try {
				for (long p = file.getLong(ref + 9); p != 0; p = file.getLong(p))
					if (Objects.equals(key, read(file.getLong(p + 8), changed))) {
						Object old = read(file.getLong(p + 16), changed);
						file.putLong(p + 16, writeValue(value, 0));
						if (changed != null)
							changed.run();
						return old;
					}
				append(ref, writeValue(key, 0), writeValue(value, 0));
				if (changed != null)
					changed.run();
				return null;
			} catch (IOException e) {
				throw failure(e);
			}
		}

		@Override
		public Set<Entry<Object, Object>> entrySet() {
			return new AbstractSet<Entry<Object, Object>>() {
				@Override
				public int size() {
					return StoredMap.this.size();
				}

				@Override
				public Iterator<Entry<Object, Object>> iterator() {
					try {
						final long first = file.getLong(ref + 9);
						return new Iterator<Entry<Object, Object>>() {
							long next = first;

							@Override
							public boolean hasNext() {
								return next != 0;
							}

							@Override
							public Entry<Object, Object> next() {
								if (next == 0)
									throw new NoSuchElementException();
								try {
									final long p = next;
									next = file.getLong(p);
									return new SimpleEntry<Object, Object>(read(file.getLong(p + 8), changed),
											read(file.getLong(p + 16), changed)) {
										private static final long serialVersionUID = -2780207506584735369L;

										@Override
										public Object setValue(Object v) {
											Object old = StoredMap.this.put(getKey(), v);
											super.setValue(v);
											return old;
										}
									};
								} catch (IOException e) {
									throw failure(e);
								}
							}

							@Override
							public void remove() {
								throw new UnsupportedOperationException();
							}
						};
					} catch (IOException e) {
						throw failure(e);
					}
				}
			};
		}
	}
}
