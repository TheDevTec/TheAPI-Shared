package me.devtec.shared.dataholder.store;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import me.devtec.shared.dataholder.ConfigDocument;

/**
 * Disk hierarchy with incremental linear hashing and persistent page-cached
 * handles.
 */
public final class DiskNodeStore extends ConfigStore {
	private static final int WIDTH = 16 * 8;
	private final DiskGeneration generation;
	private final PagedFile records, segments, index;
	public final DiskValueStore values;
	private int level = 4, split;
	private boolean closed;
	private static final byte[] EMPTY_RECORD = new byte[WIDTH];
	private final byte[] bulkRecord = new byte[WIDTH];
	private int[] bulkHeads;

	private long[] bulkBloom;
	private long bulkBloomMask;

	private boolean bulkLoad;

	public DiskNodeStore() {
		DiskGeneration g = null;

		try {
			g = new DiskGeneration();
			generation = g;

			final long budget = ConfigMemoryPolicy.budget();

			/*
			 * Použijeme maximálně polovinu per-config budgetu pro disk page caches.
			 *
			 * 4 backing files: records segments index values
			 */
			final long cacheBudget = Math.max(4L * PagedFile.PAGE, budget / 2);

			final long perFile = cacheBudget / 4;

			final int pages = (int) Math.max(4L, Math.min(16384L, perFile / PagedFile.PAGE));

			records = g.file("nodes.dat", pages);
			segments = g.file("segments.dat", pages);
			index = g.file("lookup.dat", pages);
			values = new DiskValueStore(g, pages);

			allocate(0, "");

			for (int i = 0; i < 16; ++i)
				index.putLong(i * 8L, 0);

		} catch (IOException e) {
			if (g != null)
				g.abort();

			throw failure(e);
		}
	}

	public void beginBulkLoad(long sourceBytes) {
		if (bulkLoad || nodes != 1)
			return;

		/*
		 * Odhad počtu buckets podle source size.
		 *
		 * Pro 1.85 GiB: ~15.5M desired -> 16,777,216 buckets.
		 */
		long desiredBuckets = Math.max(16L, sourceBytes / 128L);

		/*
		 * Bucket heads budou při bulk loadu čistě v RAM:
		 *
		 * int = 4 bytes.
		 *
		 * Použijeme max 1/16 per-config budgetu, nejvýše 128 MiB.
		 */
		final long heap = Runtime.getRuntime().maxMemory();

		final long headBudget = Math.max(256L * 1024L,
				Math.min(128L * 1024L * 1024L, Math.min(ConfigMemoryPolicy.budget() / 8L, heap / 32L)));

		long maxBuckets = highestPowerOfTwo(Math.max(16L, headBudget / Integer.BYTES));

		long bucketCount = nextPowerOfTwo(desiredBuckets);

		if (bucketCount > maxBuckets)
			bucketCount = maxBuckets;

		/*
		 * int[] samozřejmě musí být int-addressable. Prakticky sem kvůli headBudget
		 * nikdy nedojdeme.
		 */
		if (bucketCount > Integer.MAX_VALUE - 8L)
			bucketCount = highestPowerOfTwo(Integer.MAX_VALUE - 8L);

		level = log2(bucketCount);
		split = 0;

		/*
		 * Tohle je celý live hash index při bulk loadu.
		 *
		 * 16,777,216 buckets = 64 MiB.
		 */
		bulkHeads = new int[(int) bucketCount];

		/*
		 * Bounded Bloom: 2–32 MiB.
		 */
		final long bloomBytes = Math.max(256L * 1024L,
				Math.min(32L * 1024L * 1024L, Math.min(ConfigMemoryPolicy.budget() / 16L, heap / 64L)));

		int longs = 1;

		long wantedLongs = bloomBytes / Long.BYTES;

		while (longs < wantedLongs && longs < 1 << 22)
			longs <<= 1;

		bulkBloom = new long[longs];

		bulkBloomMask = longs * 64L - 1L;

		bulkLoad = true;
	}

	private static long mixBloom(long value) {
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdL;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53L;
		value ^= value >>> 33;
		return value;
	}

	private void bulkBloomAdd(long hash) {
		if (bulkBloom == null)
			return;

		long h1 = mixBloom(hash);
		long h2 = mixBloom(hash ^ 0x9e3779b97f4a7c15L);

		long bit1 = h1 & bulkBloomMask;
		long bit2 = h2 & bulkBloomMask;

		bulkBloom[(int) (bit1 >>> 6)] |= 1L << ((int) bit1 & 63);

		bulkBloom[(int) (bit2 >>> 6)] |= 1L << ((int) bit2 & 63);
	}

	private boolean bulkBloomMightContain(long hash) {
		if (bulkBloom == null)
			return true;

		long h1 = mixBloom(hash);
		long h2 = mixBloom(hash ^ 0x9e3779b97f4a7c15L);

		long bit1 = h1 & bulkBloomMask;
		long bit2 = h2 & bulkBloomMask;

		return (bulkBloom[(int) (bit1 >>> 6)] & 1L << ((int) bit1 & 63)) != 0
				&& (bulkBloom[(int) (bit2 >>> 6)] & 1L << ((int) bit2 & 63)) != 0;
	}

	private static long nextPowerOfTwo(long value) {
		if (value <= 1)
			return 1;

		long highest = Long.highestOneBit(value - 1);

		if (highest >= 1L << 62)
			return 1L << 62;

		return highest << 1;
	}

	private static long highestPowerOfTwo(long value) {
		if (value <= 1)
			return 1;

		return Long.highestOneBit(value);
	}

	private static int log2(long value) {
		return 63 - Long.numberOfLeadingZeros(value);
	}

	private int createBulkChild(int parent, String path, int start, int end, long hash, int bucket) {

		if (nodes == Integer.MAX_VALUE)
			throw new IllegalStateException("Config node limit exceeded");

		final int node = nodes;

		final String name = path.substring(start, end);

		final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

		try {
			final long segmentOffset = segments.length();

			segments.write(segmentOffset, nameBytes, 0, nameBytes.length);

			final int previousHead = bulkHeads[bucket];

			final int last = (int) field(parent, LAST);

			final byte[] record = bulkRecord;

			Arrays.fill(record, (byte) 0);

			/*
			 * Používáme už existující putLong().
			 */
			putLong(record, PARENT * Long.BYTES, parent);

			putLong(record, PREV * Long.BYTES, last);

			putLong(record, HASH * Long.BYTES, hash);

			putLong(record, HASH_NEXT * Long.BYTES, previousHead);

			/*
			 * DiskNodeStore private:
			 *
			 * 12 = segment offset 13 = UTF-8 segment length
			 */
			putLong(record, 12 * Long.BYTES, segmentOffset);

			putLong(record, 13 * Long.BYTES, nameBytes.length);

			/*
			 * Nový record vznikne jedním write(), ne allocate() + několik field().
			 */
			records.write((long) node * WIDTH, record, 0, WIDTH);

			++nodes;

			/*
			 * Bulk hash head je RAM-only.
			 */
			bulkHeads[bucket] = node;

			/*
			 * Hierarchy stále potřebuje patchnout parent + previous sibling.
			 */
			if (last == 0)
				field(parent, FIRST, node);
			else
				field(last, NEXT, node);

			field(parent, LAST, node);

			retained += 144L + 2L * name.length();

			++revision;

			return node;

		} catch (IOException e) {
			throw failure(e);
		}
	}

	@Override
	protected int childHashed(int parent, String path, int start, int end, long hash, boolean create) {

		if (!bulkLoad)
			return super.childHashed(parent, path, start, end, hash, create);

		final int bucket = bucket(hash);

		/*
		 * Bloom miss = tento parent+segment hash definitivně ještě nebyl vložen.
		 */
		if (!bulkBloomMightContain(hash)) {
			if (!create)
				return 0;

			final int node = createBulkChild(parent, path, start, end, hash, bucket);

			bulkBloomAdd(hash);

			return node;
		}

		/*
		 * Bloom positive:
		 *
		 * může to být skutečný duplicate, nebo false positive.
		 */
		final int length = end - start;

		for (int node = bulkHeads[bucket]; node != 0; node = (int) field(node, HASH_NEXT)) {

			if (field(node, HASH) != hash || field(node, PARENT) != parent || (field(node, FLAGS) & 2L) != 0L)
				continue;

			final String existing = segment(node);

			if (existing.length() == length && path.regionMatches(start, existing, 0, length))
				return node;
		}

		if (!create)
			return 0;

		/*
		 * Bloom false-positive.
		 */
		final int node = createBulkChild(parent, path, start, end, hash, bucket);

		bulkBloomAdd(hash);

		return node;
	}

	private static IllegalStateException failure(IOException e) {
		return new IllegalStateException("Config backing I/O failed", e);
	}

	@Override
	public Object externalValue(ConfigDocument document, String key, Object value) {
		return document.externalValue(key, value);
	}

	@Override
	protected long field(int n, int f) {
		try {
			return records.getLong((long) n * WIDTH + f * 8);
		} catch (IOException e) {
			throw failure(e);
		}
	}

	@Override
	protected void field(int n, int f, long v) {
		try {
			records.putLong((long) n * WIDTH + f * 8, v);
		} catch (IOException e) {
			throw failure(e);
		}
	}

	@Override
	protected void allocate(int n, String name) {
		try {
			byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
			long offset = segments.length();
			segments.write(offset, bytes, 0, bytes.length);
			records.write((long) n * WIDTH, EMPTY_RECORD, 0, WIDTH);
			field(n, 12, offset);
			field(n, 13, bytes.length);
		} catch (IOException e) {
			throw failure(e);
		}
	}

	@Override
	public String segment(int n) {
		try {
			long len = field(n, 13);
			if (len < 0 || len > 1024 * 1024)
				throw new IOException("Invalid Config segment length");
			byte[] b = new byte[(int) len];
			segments.read(field(n, 12), b, 0, b.length);
			return new String(b, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw failure(e);
		}
	}

	@Override
	public ValueRef value(int n) {
		long estimate = field(n, ESTIMATE) - values.metadataHeap(field(n, 15));
		return values.reference(field(n, 14), Math.max(0, estimate));
	}

	@Override
	public Object getValue(int node) {
		return values.getValue(field(node, 14));
	}

	@Override
	public boolean isNullValue(int node) {
		return values.isNull(field(node, 14));
	}

	@Override
	public String getWrittenValue(int node) {
		return values.writtenValue(field(node, 15));
	}

	@Override
	public String getComment(int node) {
		return values.comment(field(node, 15));
	}

	@Override
	public List<String> getComments(int node) {
		return values.comments(field(node, 15));
	}

	@Override
	public void writeJsonValue(int node, java.io.Writer output) throws IOException {
		values.writeJsonValue(field(node, 14), output);
	}

	@Override
	public NodeMetadata metadata(int n) {
		long p = field(n, 15);
		if (p == 0)
			return new NodeMetadata();
		return new NodeMetadata(values.writtenValue(p), values.comment(p), values.comments(p));
	}

	@Override
	protected void content(int n, ValueRef v, NodeMetadata m) {
		long value = values.offset(v);
		long meta = values.writeMetadata(m);
		field(n, 14, value);
		field(n, 15, meta);
	}

	@Override
	protected int bucket(long h) {
		int b = (int) h & (1 << level) - 1;
		return b < split ? (int) h & (1 << level + 1) - 1 : b;
	}

	@Override
	protected int bucketHead(int b) {
		if (bulkLoad)
			return bulkHeads[b];

		try {
			return (int) index.getLong((long) b * Long.BYTES);
		} catch (IOException e) {
			throw failure(e);
		}
	}

	@Override
	protected void bucketHead(int b, int n) {
		if (bulkLoad) {
			bulkHeads[b] = n;
			return;
		}

		try {
			index.putLong((long) b * Long.BYTES, n);
		} catch (IOException e) {
			throw failure(e);
		}
	}

	private static void putLong(byte[] target, int offset, long value) {
		target[offset] = (byte) (value >>> 56);
		target[offset + 1] = (byte) (value >>> 48);
		target[offset + 2] = (byte) (value >>> 40);
		target[offset + 3] = (byte) (value >>> 32);
		target[offset + 4] = (byte) (value >>> 24);
		target[offset + 5] = (byte) (value >>> 16);
		target[offset + 6] = (byte) (value >>> 8);
		target[offset + 7] = (byte) value;
	}

	@Override
	protected void indexAdded() {
		if (bulkLoad) {
			/*
			 * ConfigStore.copyTo() po vytvoření každého node zavolá indexAdded().
			 *
			 * bulkHeads už byly vloženy přes bucketHead(), tady doplníme Bloom.
			 */
			final int node = nodes - 1;

			if (node > 0)
				bulkBloomAdd(field(node, HASH));

			return;
		}

		if (nodes < ((1L << level) + split) * 3)
			return;

		int old = split;
		int added = (1 << level) + split;

		int head = bucketHead(old);

		bucketHead(added, 0);
		bucketHead(old, 0);

		while (head != 0) {
			int next = (int) field(head, HASH_NEXT);

			if ((field(head, FLAGS) & 2) == 0) {
				int b = ((int) field(head, HASH) & 1 << level) == 0 ? old : added;

				field(head, HASH_NEXT, bucketHead(b));

				bucketHead(b, head);
			}

			head = next;
		}

		if (++split == 1 << level) {
			split = 0;

			if (++level >= 30)
				throw new IllegalStateException("Config hash index limit");
		}
	}

	public void endBulkLoad() {
		if (!bulkLoad)
			return;

		final int[] heads = bulkHeads;

		try {
			/*
			 * Sekvenční zápis místo milionů random putLong().
			 *
			 * 1 MiB staging buffer.
			 */
			final byte[] buffer = new byte[1024 * 1024];

			final int headsPerBuffer = buffer.length / Long.BYTES;

			int base = 0;

			while (base < heads.length) {
				final int count = Math.min(headsPerBuffer, heads.length - base);

				int pos = 0;

				for (int i = 0; i < count; ++i) {
					putLong(buffer, pos, heads[base + i]);

					pos += Long.BYTES;
				}

				index.write((long) base * Long.BYTES, buffer, 0, count * Long.BYTES);

				base += count;
			}

		} catch (IOException e) {
			throw failure(e);

		} finally {
			bulkLoad = false;

			bulkHeads = null;

			bulkBloom = null;
			bulkBloomMask = 0L;
		}
	}

	@Override
	public boolean disk() {
		return true;
	}

	public void retainExternal(Object value) {
		values.retainExternal(value);
	}

	@Override
	public long backingBytes() {
		return records.length() + segments.length() + index.length() + values.bytes();
	}

	@Override
	public long cacheBytes() {
		return records.cacheBytes() + segments.cacheBytes() + index.cacheBytes() + values.cacheBytes();
	}

	@Override
	public void flush() {
		try {
			records.flush();
			segments.flush();
			index.flush();
			values.flush();
		} catch (IOException e) {
			throw failure(e);
		}
	}

	@Override
	public void close() {
		if (!closed) {
			closed = true;
			revision++;
			generation.release();
		}
	}
}
