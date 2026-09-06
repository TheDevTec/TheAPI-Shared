package me.devtec.shared.dataholder.store;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.StringContainer;

/**
 * Small fixed-size pages avoid a single ever-growing node object graph.
 *
 * PackedMemoryStore additionally keeps a primitive full-path index optimized
 * for point lookups. Complete entry paths are canonical Strings, shared by
 * lookup and iteration.
 *
 * Metadata uses structure-of-arrays instead of one NodeMetadata object per
 * entry. Hot fields such as writtenValue therefore do not require another
 * object pointer chase.
 */
public final class PackedMemoryStore extends ConfigStore {

	private static final int SHIFT = 8;
	private static final int SIZE = 1 << SHIFT;
	private static final int MASK = SIZE - 1;

	/*
	 * ConfigStore uses fields 0..11.
	 *
	 * Field 12 is private to PackedMemoryStore and contains:
	 *
	 * high 32 bit = Java String-compatible hash of complete path low 32 bit =
	 * complete path length
	 */
	private static final int PATH_HASH = 12;
	private static final int FIELDS = 13;

	private static final int INITIAL_PAGES = 4;
	private static final int INITIAL_PATH_INDEX = 64;

	/*
	 * Keep the direct path index relatively sparse.
	 *
	 * 3 / 5 = 60 % load factor.
	 */
	private static final int PATH_LOAD_NUMERATOR = 3;
	private static final int PATH_LOAD_DENOMINATOR = 5;

	private long[][] records = new long[INITIAL_PAGES][];

	private String[][] segments = new String[INITIAL_PAGES][];

	private ValueRef[][] values = new ValueRef[INITIAL_PAGES][];

	/*
	 * Metadata SoA.
	 *
	 * NodeMetadata itself is now only an API/transport object.
	 *
	 * This means getStringValue() can fetch writtenValue directly:
	 *
	 * writtenValues[page][local]
	 *
	 * without:
	 *
	 * metadata[page][local] -> NodeMetadata -> writtenValue
	 */
	private String[][] writtenValues = new String[INITIAL_PAGES][];

	private String[][] commentAfterValues = new String[INITIAL_PAGES][];

	private List<String>[][] comments = newListOuter(INITIAL_PAGES);

	private String[][] entryPaths = new String[INITIAL_PAGES][];
	private long entryPathArrayBytes, entryPathStringBytes;
	private long nodePageBytes, segmentHeapBytes, valueHeapBytes, valueReferenceBytes;

	/*
	 * Existing parent+segment hierarchy index.
	 */
	private int[] buckets = new int[32];

	/*
	 * Full-path open-addressed index.
	 *
	 * 0 = empty >0 = node id
	 *
	 * Deleted nodes can temporarily remain in this array. They are ignored through
	 * FLAGS and removed on the next rebuild/resize.
	 */
	private int[] pathIndex = new int[INITIAL_PATH_INDEX];

	private int pathIndexUsed;

	/*
	 * Next node that still needs PATH_HASH + pathIndex processing.
	 *
	 * Keeping this in indexAdded() is important because ConfigStore.copyTo()
	 * directly allocates/copies nodes and then calls target.indexAdded().
	 */
	private int pathIndexProcessedNodes = 1;

	/*
	 * Extra physical memory introduced solely by PATH_HASH.
	 *
	 * Existing retained already accounts for the old 12-field representation, so
	 * only the additional eighth byte field/page delta is tracked here.
	 */
	private long pathRecordBytes;

	private static final ValueRef EMPTY_VALUE = new ValueRef.Memory(null);

	private static final NodeMetadata EMPTY_METADATA = new NodeMetadata();

	public PackedMemoryStore() {
		allocate(0, "");
	}

	// ========================================================================
	// GENERIC ARRAY CREATION
	// ========================================================================

	@SuppressWarnings("unchecked")
	private static List<String>[][] newListOuter(int size) {
		return new List[size][];
	}

	@SuppressWarnings("unchecked")
	private static List<String>[] newListPage() {
		return new List[SIZE];
	}

	// ========================================================================
	// DIRECT FULL-PATH LOOKUP
	// ========================================================================

	@Override
	public int resolve(String path, boolean create) {
		if (create)
			return super.resolve(0, path, true);
		return (int) findPathState(0, path);
	}

	@Override
	public int resolve(int parent, String path, boolean create) {
		if (create)
			return super.resolve(parent, path, true);
		return (int) findPathState(parent, path);
	}

	@Override
	public boolean hasValue(String path) {
		return findEntry(path) != 0;
	}

	@Override
	public Object getValue(String path) {
		final int node = findEntry(path);

		if (node == 0)
			return null;

		final ValueRef ref = values[node >>> SHIFT][node & MASK];

		return ref == null ? null : ref.get();
	}

	@Override
	public boolean hasKeyOrSection(String path) {
		if (path.indexOf('.') < 0)
			return resolve(path, false) != 0;

		if (findEntry(path) != 0)
			return true;

		return resolve(path, false) != 0;
	}

	private long findPathState(int parent, String path) {
		checkPathDepth(path);

		final long fingerprint = parent == 0 ? pathFingerprint(path) : relativePathFingerprint(parent, path);

		final int[] index = pathIndex;

		final int mask = index.length - 1;

		int slot = mixPathFingerprint(fingerprint) & mask;

		while (true) {
			final int node = index[slot];

			if (node == 0)
				return 0L;

			final int page = node >>> SHIFT;

			final int local = node & MASK;

			final long[] record = records[page];

			final int offset = local * FIELDS;

			if (record[offset + PATH_HASH] == fingerprint) {

				final long flags = record[offset + FLAGS];

				if ((flags & 2L) == 0L && pathEquals(node, parent, path, record, offset))

					return flags << 32 | node & 0xffffffffL;
			}

			slot = slot + 1 & mask;
		}
	}

	private boolean pathEquals(int node, int stopParent, String path, long[] firstRecord, int firstOffset) {

		final int firstPage = node >>> SHIFT;

		final int firstLocal = node & MASK;

		final String firstSegment = segments[firstPage][firstLocal];

		final int firstParent = (int) firstRecord[firstOffset + PARENT];

		/*
		 * Very common case:
		 *
		 * key
		 *
		 * instead of:
		 *
		 * parent.child
		 */
		if (stopParent == 0 && firstParent == 0)

			return firstSegment.equals(path);

		int position = path.length();

		boolean first = true;

		while (node != stopParent) {
			if (node == 0)
				return false;

			final int page = node >>> SHIFT;

			final int local = node & MASK;

			final long[] record;
			final int offset;

			if (first) {
				record = firstRecord;

				offset = firstOffset;

				first = false;
			} else {
				record = records[page];

				offset = local * FIELDS;
			}

			final String segment = segments[page][local];

			final int length = segment.length();

			position -= length;

			if (position < 0)
				return false;

			for (int i = 0; i < length; ++i)

				if (path.charAt(position + i) != segment.charAt(i))

					return false;

			node = (int) record[offset + PARENT];

			if (node != stopParent) {
				if (position == 0 || path.charAt(position - 1) != '.')

					return false;

				--position;
			}
		}

		return position == 0;
	}

	/**
	 * Root-relative path.
	 *
	 * String.hashCode() is deliberately used because Java 8 String caches it.
	 */
	private static long pathFingerprint(String path) {

		return fingerprint(path.hashCode(), path.length());
	}

	/**
	 * Constructs:
	 *
	 * parent.full.path + "." + relativePath
	 *
	 * without constructing that String.
	 */
	private long relativePathFingerprint(int parent, String path) {

		final long[] parentRecord = records[parent >>> SHIFT];

		final long parentFingerprint = parentRecord[(parent & MASK) * FIELDS + PATH_HASH];

		int hash = fingerprintHash(parentFingerprint);

		hash = 31 * hash + '.';

		final int length = path.length();

		for (int i = 0; i < length; ++i)

			hash = 31 * hash + path.charAt(i);

		return fingerprint(hash, fingerprintLength(parentFingerprint) + 1 + length);
	}

	/**
	 * Preserve ConfigStore's nesting limit without adding a complete extra path
	 * scan for normal short paths.
	 *
	 * More than 256 segments is impossible when String length < 256.
	 */
	private static void checkPathDepth(String path) {

		final int length = path.length();

		if (length < 256)
			return;

		int depth = 1;

		for (int i = 0; i < length; ++i)

			if (path.charAt(i) == '.' && ++depth > 256)

				throw new IllegalArgumentException("Config path nesting exceeds 256");
	}

	private static long fingerprint(int hash, int length) {

		return (long) hash << 32 | length & 0xffffffffL;
	}

	private static int fingerprintHash(long fingerprint) {

		return (int) (fingerprint >>> 32);
	}

	private static int fingerprintLength(long fingerprint) {

		return (int) fingerprint;
	}

	private int mixPathFingerprint(long fingerprint) {

		int hash = (int) (fingerprint >>> 32);

		hash ^= (int) fingerprint * 0x9e3779b9;

		hash ^= (int) seed;

		hash ^= hash >>> 16;

		return hash;
	}

	// ========================================================================
	// FULL PATH INDEX CREATION
	// ========================================================================

	/**
	 * Builds PATH_HASH from canonical parent + segment data.
	 */
	private long storedPathFingerprint(int node) {

		final int page = node >>> SHIFT;

		final int local = node & MASK;

		final long[] record = records[page];

		final int offset = local * FIELDS;

		final int parent = (int) record[offset + PARENT];

		final String segment = segments[page][local];

		int hash;
		int length;

		if (parent == 0) {
			hash = 0;

			length = segment.length();
		} else {
			final long[] parentRecord = records[parent >>> SHIFT];

			final long parentFingerprint = parentRecord[(parent & MASK) * FIELDS + PATH_HASH];

			hash = fingerprintHash(parentFingerprint);

			length = fingerprintLength(parentFingerprint) + 1 + segment.length();

			hash = 31 * hash + '.';
		}

		final int segmentLength = segment.length();

		for (int i = 0; i < segmentLength; ++i)

			hash = 31 * hash + segment.charAt(i);

		return fingerprint(hash, length);
	}

	private void ensurePathIndexForInsert(int limitExclusive) {

		if ((pathIndexUsed + 1L) * PATH_LOAD_DENOMINATOR < (long) pathIndex.length * PATH_LOAD_NUMERATOR)

			return;

		/*
		 * First compact at the same size.
		 *
		 * This removes nodes that were pruned or deleted since the previous rebuild.
		 */
		rebuildPathIndex(pathIndex.length, limitExclusive);

		if ((pathIndexUsed + 1L) * PATH_LOAD_DENOMINATOR < (long) pathIndex.length * PATH_LOAD_NUMERATOR)

			return;

		if (pathIndex.length >= 1 << 30)
			throw new IllegalStateException("Packed Config path index limit exceeded");

		rebuildPathIndex(pathIndex.length << 1, limitExclusive);
	}

	private void rebuildPathIndex(int newSize, int limitExclusive) {

		pathIndex = new int[newSize];

		pathIndexUsed = 0;

		for (int node = 1; node < limitExclusive; ++node) {

			final long[] record = records[node >>> SHIFT];

			final int offset = (node & MASK) * FIELDS;

			if ((record[offset + FLAGS] & 2L) != 0L)

				continue;

			insertPathNodeNoResize(node, record[offset + PATH_HASH]);
		}
	}

	private void insertPathNodeNoResize(int node, long fingerprint) {

		final int mask = pathIndex.length - 1;

		int slot = mixPathFingerprint(fingerprint) & mask;

		while (pathIndex[slot] != 0)
			slot = slot + 1 & mask;

		pathIndex[slot] = node;

		++pathIndexUsed;
	}

	/**
	 * Called from indexAdded().
	 *
	 * This intentionally does not live only in createChildFast().
	 *
	 * ConfigStore.copyTo() directly allocates/copies nodes into a target store and
	 * calls indexAdded(), so this also correctly initializes Disk -> Packed
	 * migration.
	 */
	private void indexNewPathNodes() {

		while (pathIndexProcessedNodes < nodes) {

			final int node = pathIndexProcessedNodes;

			final long[] record = records[node >>> SHIFT];

			final int offset = (node & MASK) * FIELDS;

			final long fingerprint = storedPathFingerprint(node);

			record[offset + PATH_HASH] = fingerprint;

			if ((record[offset + FLAGS] & 2L) == 0L) {

				ensurePathIndexForInsert(node);

				insertPathNodeNoResize(node, fingerprint);
			}

			pathIndexProcessedNodes = node + 1;
		}
	}

	// ========================================================================
	// MEMORY STORE
	// ========================================================================

	private void ensurePageCapacity(int page) {

		if (page < records.length)
			return;

		int size = records.length;

		do
			size <<= 1;
		while (size <= page);

		records = Arrays.copyOf(records, size);

		segments = Arrays.copyOf(segments, size);

		values = Arrays.copyOf(values, size);

		writtenValues = Arrays.copyOf(writtenValues, size);

		commentAfterValues = Arrays.copyOf(commentAfterValues, size);

		comments = Arrays.copyOf(comments, size);

		entryPaths = Arrays.copyOf(entryPaths, size);
	}

	@Override
	protected long field(int node, int field) {

		return records[node >>> SHIFT][(node & MASK) * FIELDS + field];
	}

	@Override
	protected void field(int node, int field, long value) {

		if (field == ESTIMATE)
			valueHeapBytes += value - records[node >>> SHIFT][(node & MASK) * FIELDS + ESTIMATE];
		if (field == FLAGS && (value & 2L) != 0)
			releaseEntryPath(node);
		records[node >>> SHIFT][(node & MASK) * FIELDS + field] = value;
	}

	@Override
	protected void allocate(int node, String segment) {

		final int page = node >>> SHIFT;

		ensurePageCapacity(page);

		if (records[page] == null) {
			// Twelve base fields plus five reference pages; the thirteenth field
			// and canonical key pages are accounted separately below.
			nodePageBytes += 16L + (long) SIZE * 12 * Long.BYTES + 5L * (16L + SIZE * Long.BYTES);
			records[page] = new long[SIZE * FIELDS];

			segments[page] = new String[SIZE];

			values[page] = new ValueRef[SIZE];

			writtenValues[page] = new String[SIZE];

			commentAfterValues[page] = new String[SIZE];

			comments[page] = newListPage();

			/*
			 * Only the extra PATH_HASH field compared to the old 12-field representation.
			 */
			pathRecordBytes += (long) SIZE * Long.BYTES;
		}

		if (entryPaths[page] == null) {

			entryPaths[page] = new String[SIZE];

			entryPathArrayBytes += 16L + (long) SIZE * Long.BYTES;
		}

		segmentHeapBytes += pathStringBytes(segment);
		segments[page][node & MASK] = segment;
	}

	private void ensureEntryPath(int node) {
		int page = node >>> SHIFT, local = node & MASK;
		if (entryPaths[page][local] != null)
			return;
		if (parent(node) == 0) {
			entryPaths[page][local] = segments[page][local];
			return;
		}
		StringContainer path = new StringContainer(fullPathLength(node));
		appendPath(path, node);
		String key = path.toString();
		entryPaths[page][local] = key;
		entryPathStringBytes += pathStringBytes(key);
	}

	private static long pathStringBytes(String key) {
		// Conservative Java 8 String + backing char[] estimate, including alignment.
		return 64L + (2L * key.length() + 7L & ~7L);
	}

	@Override
	protected void releaseEntryPath(int node) {
		int page = node >>> SHIFT, local = node & MASK;
		String key = entryPaths[page][local];
		if (key == null)
			return;
		if (parent(node) != 0)
			entryPathStringBytes -= pathStringBytes(key);
		entryPaths[page][local] = null;
	}

	@Override
	public long additionalEntryHeap(int node) {
		if (entryPaths[node >>> SHIFT][node & MASK] != null || parent(node) == 0)
			return 0;
		// Include construction scratch as well as the retained String in the preflight.
		return 128L + 4L * fullPathLength(node);
	}

	@Override
	public String segment(int node) {
		return segments[node >>> SHIFT][node & MASK];
	}

	@Override
	public ValueRef value(int node) {
		final ValueRef value = values[node >>> SHIFT][node & MASK];
		return value == null ? EMPTY_VALUE : value;
	}

	@Override
	public Object getValue(int node) {
		final ValueRef ref = values[node >>> SHIFT][node & MASK];
		return ref == null ? null : ref.get();
	}

	// ========================================================================
	// METADATA SoA
	// ========================================================================

	@Override
	public String getWrittenValue(int node) {
		return writtenValues[node >>> SHIFT][node & MASK];
	}

	@Override
	public String getComment(int node) {
		return commentAfterValues[node >>> SHIFT][node & MASK];
	}

	@Override
	public List<String> getComments(int node) {
		return comments[node >>> SHIFT][node & MASK];
	}

	@Override
	public String getStringValue(int node) {
		final int page = node >>> SHIFT;
		final int local = node & MASK;

		final String written = writtenValues[page][local];

		if (written != null)
			return written;

		final ValueRef ref = values[page][local];

		if (ref == null)
			return null;

		final Object value = ref.get();

		if (value == null)
			return null;

		return String.valueOf(value);
	}

	@Override
	public String getStringValue(String path) {
		final int node = findEntry(path);

		if (node == 0)
			return null;

		return getStringValue(node);
	}

	@Override
	protected String entryKey(int node, StringContainer fallback) {
		String key = entryPaths[node >>> SHIFT][node & MASK];
		return key != null ? key : super.entryKey(node, fallback == null ? new StringContainer() : fallback);
	}

	@Override
	public Object externalValue(ConfigDocument document, String key, Object value) {
		return value;
	}

	@Override
	public String getWrittenValue(String path) {
		final int node = findEntry(path);
		if (node == 0)
			return null;
		return writtenValues[node >>> SHIFT][node & MASK];
	}

	@Override
	public String getComment(String path) {
		final int node = (int) findPathState(0, path);
		if (node == 0)
			return null;
		return commentAfterValues[node >>> SHIFT][node & MASK];
	}

	@Override
	public List<String> getComments(String path) {
		final int node = (int) findPathState(0, path);
		if (node == 0)
			return null;
		return comments[node >>> SHIFT][node & MASK];
	}

	@Override
	public boolean hasValue(int node) {
		if (node == 0)
			return false;
		final long[] record = records[node >>> SHIFT];
		return (record[(node & MASK) * FIELDS + FLAGS] & 1L) != 0L;
	}

	@Override
	public boolean modified(int node) {
		final long[] record = records[node >>> SHIFT];
		return record[(node & MASK) * FIELDS + EPOCH] > savedEpoch;
	}

	@Override
	public NodeMetadata metadata(int node) {
		final int page = node >>> SHIFT;
		final int local = node & MASK;
		final String written = writtenValues[page][local];
		final String comment = commentAfterValues[page][local];
		final List<String> commentList = comments[page][local];
		if (written == null && comment == null && commentList == null)
			return EMPTY_METADATA;

		/*
		 * Cold/API path.
		 *
		 * NodeMetadata is reconstructed only when a caller explicitly requests the
		 * aggregate metadata representation.
		 */
		return new NodeMetadata(written, comment, commentList);
	}

	@Override
	protected void content(int node, ValueRef value, NodeMetadata meta) {
		if (node != 0)
			ensureEntryPath(node);
		final int page = node >>> SHIFT;
		final int local = node & MASK;
		if (values[page][local] == null)
			valueReferenceBytes += 32L;
		values[page][local] = value;
		if (meta == null) {
			writtenValues[page][local] = null;
			commentAfterValues[page][local] = null;
			comments[page][local] = null;
			return;
		}

		/*
		 * Publish canonical metadata directly into parallel arrays.
		 *
		 * The NodeMetadata wrapper itself is deliberately not retained.
		 */
		writtenValues[page][local] = meta.writtenValue;
		commentAfterValues[page][local] = meta.commentAfterValue;
		comments[page][local] = meta.comments;
	}

	// ========================================================================
	// ENTRY PATH EXACT LOOKUP
	// ========================================================================
	@Override
	protected int findValueNode(String path) {
		return findEntry(path);
	}

	private int findEntry(String path) {
		checkPathDepth(path);

		final long fingerprint = pathFingerprint(path);

		final int[] index = pathIndex;

		final int mask = index.length - 1;

		int slot = mixPathFingerprint(fingerprint) & mask;

		while (true) {
			final int node = index[slot];

			if (node == 0)
				return 0;

			final int page = node >>> SHIFT;

			final int local = node & MASK;

			final long[] record = records[page];

			final int offset = local * FIELDS;

			if (record[offset + PATH_HASH] == fingerprint && (record[offset + FLAGS] & 3L) == 1L) {

				final String key = entryPaths[page][local];

				if (path.equals(key))

					return node;
			}

			slot = slot + 1 & mask;
		}
	}

	@Override
	public int firstChild(int node) {
		final long[] record = records[node >>> SHIFT];
		return (int) record[(node & MASK) * FIELDS + FIRST];
	}

	@Override
	public int nextSibling(int node) {
		final long[] record = records[node >>> SHIFT];
		return (int) record[(node & MASK) * FIELDS + NEXT];
	}

	@Override
	public int parent(int node) {
		final long[] record = records[node >>> SHIFT];
		return (int) record[(node & MASK) * FIELDS + PARENT];
	}

	@Override
	public int nextEntry(int node) {
		final long[] record = records[node >>> SHIFT];
		return (int) record[(node & MASK) * FIELDS + ORDER_NEXT];
	}

	// ========================================================================
	// HIERARCHICAL CHILD INDEX
	// ========================================================================

	@Override
	protected int childHashed(int parent, String path, int start, int end, long hash, boolean create) {
		final int bucket = (int) hash & buckets.length - 1;

		final int length = end - start;

		int node = buckets[bucket];

		while (node != 0) {
			final int page = node >>> SHIFT;

			final int local = node & MASK;

			final long[] record = records[page];

			final int offset = local * FIELDS;

			if (record[offset + HASH] == hash && record[offset + PARENT] == parent
					&& (record[offset + FLAGS] & 2L) == 0L) {

				final String segment = segments[page][local];

				if (segmentEquals(path, start, segment, length))
					return node;
			}

			node = (int) record[offset + HASH_NEXT];
		}

		if (!create)
			return 0;

		return createChildFast(parent, path, start, end, hash, bucket);
	}

	private static boolean segmentEquals(String path, int start, String segment, int length) {
		if (segment.length() != length)
			return false;

		for (int i = 0; i < length; ++i)

			if (path.charAt(start + i) != segment.charAt(i))
				return false;

		return true;
	}

	private int createChildFast(int parent, String path, int start, int end, long hash, int bucket) {
		if (nodes == Integer.MAX_VALUE)
			throw new IllegalStateException("Config node limit exceeded");

		final int node = nodes;

		final String segment = path.substring(start, end);

		allocate(node, segment);

		++nodes;

		// ------------------------------------------------
		// node
		// ------------------------------------------------

		final int page = node >>> SHIFT;

		final int local = node & MASK;

		final long[] record = records[page];

		final int offset = local * FIELDS;

		record[offset + PARENT] = parent;

		record[offset + HASH] = hash;

		record[offset + HASH_NEXT] = buckets[bucket];

		buckets[bucket] = node;

		// ------------------------------------------------
		// parent
		// ------------------------------------------------

		final int parentPage = parent >>> SHIFT;

		final int parentLocal = parent & MASK;

		final long[] parentRecord = records[parentPage];

		final int parentOffset = parentLocal * FIELDS;

		final int last = (int) parentRecord[parentOffset + LAST];

		record[offset + PREV] = last;

		if (last == 0)
			parentRecord[parentOffset + FIRST] = node;
		else {
			final long[] lastRecord = records[last >>> SHIFT];

			lastRecord[(last & MASK) * FIELDS + NEXT] = node;
		}

		parentRecord[parentOffset + LAST] = node;

		retained += 144 + 2L * segment.length();

		++revision;

		/*
		 * Updates both:
		 *
		 * - direct path index - hierarchical hash table resize
		 */
		indexAdded();
		return node;
	}

	@Override
	protected int bucket(long hash) {
		return (int) hash & buckets.length - 1;
	}

	@Override
	protected int bucketHead(int bucket) {
		return buckets[bucket];
	}

	@Override
	protected void bucketHead(int bucket, int node) {
		buckets[bucket] = node;
	}

	@Override
	protected void indexAdded() {
		/*
		 * Must happen even when the hierarchical bucket array does not resize.
		 */
		indexNewPathNodes();

		if (nodes < buckets.length * 0.75D)

			return;

		buckets = new int[buckets.length << 1];

		for (int node = 1; node < nodes; ++node) {

			final long[] record = records[node >>> SHIFT];

			final int offset = (node & MASK) * FIELDS;

			if ((record[offset + FLAGS] & 2L) != 0L)

				continue;

			final int bucket = (int) record[offset + HASH] & buckets.length - 1;

			record[offset + HASH_NEXT] = buckets[bucket];

			buckets[bucket] = node;
		}
	}

	// ========================================================================
	// PATH CREATION
	// ========================================================================

	@Override
	protected void appendPath(StringContainer container, int node) {
		if (node == 0)
			return;

		final int page = node >>> SHIFT;

		final int local = node & MASK;

		final long[] record = records[page];

		final int offset = local * FIELDS;

		final int parent = (int) record[offset + PARENT];

		if (parent != 0) {
			appendPath(container, parent);

			container.append('.');
		}

		container.append(segments[page][local]);
	}

	private int fullPathLength(int node) {
		final long[] record = records[node >>> SHIFT];
		return fingerprintLength(record[(node & MASK) * FIELDS + PATH_HASH]);
	}

	// ========================================================================
	// ENTRY ITERATION
	// ========================================================================

	@Override
	protected Set<String> allEntryKeys() {
		return new AbstractSet<String>() {
			@Override
			public Iterator<String> iterator() {
				final long expected = revision;
				return new Iterator<String>() {
					private int next = firstEntry;

					@Override
					public boolean hasNext() {
						return next != 0;
					}

					@Override
					public String next() {
						if (expected != revision)
							throw new ConcurrentModificationException();
						final int node = next;
						if (node == 0)
							throw new NoSuchElementException();
						final int page = node >>> SHIFT, local = node & MASK;
						final long[] record = records[page];
						final int offset = local * FIELDS;
						next = (int) record[offset + ORDER_NEXT];
						if (record[offset + PARENT] == 0)
							return segments[page][local];
						// Return the canonical String; no path materialization during iteration.
						return entryKey(node, null);
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}

			@Override
			public int size() {
				return entries;
			}

			@Override
			public boolean contains(Object key) {
				return key instanceof String && hasValue((String) key);
			}
		};
	}
	// ========================================================================
	// MEMORY ACCOUNTING
	// ========================================================================

	@Override
	public long estimatedHeap() {
		long physicalFloor = 256L + nodePageBytes + segmentHeapBytes + valueHeapBytes + valueReferenceBytes
				+ 6L * (16L + records.length * Long.BYTES) + 16L + buckets.length * Integer.BYTES;
		return Math.max(retained, physicalFloor) + pathRecordBytes + 16L + (long) pathIndex.length * Integer.BYTES
				+ entryPathArrayBytes + entryPathStringBytes + 16L + (long) entryPaths.length * Long.BYTES;
	}

	@Override
	public boolean disk() {
		return false;
	}

	@Override
	public long backingBytes() {
		return 0;
	}

	@Override
	public long cacheBytes() {
		return 0;
	}

	@Override
	public void close() {
		records = new long[0][];

		segments = new String[0][];

		values = new ValueRef[0][];

		writtenValues = new String[0][];

		commentAfterValues = new String[0][];

		comments = newListOuter(0);

		buckets = new int[0];

		pathIndex = new int[0];

		pathIndexUsed = 0;

		pathIndexProcessedNodes = 1;

		pathRecordBytes = 0;

		entryPaths = new String[0][];
		entryPathArrayBytes = entryPathStringBytes = 0;
		nodePageBytes = segmentHeapBytes = valueHeapBytes = valueReferenceBytes = 0;

		++revision;
	}
}
