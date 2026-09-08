package me.devtec.shared.dataholder.store;

import java.util.*;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.StringContainer;

/**
 * Ordered hierarchy. Node zero is the root; full paths exist only at the API
 * boundary.
 */
public abstract class ConfigStore implements AutoCloseable {
	protected static final int PARENT = 0, FIRST = 1, LAST = 2, NEXT = 3, PREV = 4, HASH_NEXT = 5, ORDER_NEXT = 6,
			ORDER_PREV = 7, EPOCH = 8, HASH = 9, FLAGS = 10, ESTIMATE = 11;
	private static final ValueRef EMPTY_VALUE = new ValueRef.Memory(null);
	protected int nodes = 1, entries, firstEntry, lastEntry;
	protected long mutationEpoch, savedEpoch, retained = 256, revision;
	protected final long seed;

	protected ConfigStore() {
		seed = new Random().nextLong();
	}

	protected abstract long field(int node, int field);

	protected abstract void field(int node, int field, long value);

	protected abstract void allocate(int node, String segment);

	public abstract String segment(int node);

	public abstract ValueRef value(int node);

	public abstract NodeMetadata metadata(int node);

	public Object getValue(int node) {
		return value(node).get();
	}

	public boolean isNullValue(int node) {
		return getValue(node) == null;
	}

	public String getWrittenValue(int node) {
		return metadata(node).writtenValue;
	}

	public String getComment(int node) {
		return metadata(node).commentAfterValue;
	}

	public List<String> getComments(int node) {
		return metadata(node).comments;
	}

	public Object externalValue(ConfigDocument document, String key, Object value) {
		return value;
	}

	public String getWrittenValue(String path) {
		int node = resolve(path, false);
		return node == 0 ? null : getWrittenValue(node);
	}

	public String getComment(String path) {
		int node = resolve(path, false);
		return node == 0 ? null : getComment(node);
	}

	public List<String> getComments(String path) {
		int node = resolve(path, false);
		return node == 0 ? null : getComments(node);
	}

	public String getStringValue(int node) {
		if (!hasValue(node))
			return null;

		String raw = getWrittenValue(node);

		if (raw != null)
			return raw;

		Object value = getValue(node);

		return value == null ? null : String.valueOf(value);
	}

	public String getStringValue(String path) {
		int node = resolve(path, false);

		if (node == 0 || !hasValue(node))
			return null;

		return getStringValue(node);
	}

	public void writeJsonValue(int node, java.io.Writer output) throws java.io.IOException {
		value(node).writeJson(output);
	}

	protected abstract void content(int node, ValueRef value, NodeMetadata metadata);

	protected abstract int bucket(long hash);

	protected abstract int bucketHead(int bucket);

	protected abstract void bucketHead(int bucket, int head);

	protected abstract void indexAdded();

	public abstract boolean disk();

	public abstract long backingBytes();

	public abstract long cacheBytes();

	public void flush() {
	}

	@Override
	public abstract void close();

	public int size() {
		return entries;
	}

	public long estimatedHeap() {
		return retained;
	}

	/** Additional canonical key storage required when publishing a value. */
	public long additionalEntryHeap(int node) {
		return 0;
	}

	public long revision() {
		return revision;
	}

	public int firstChild(int n) {
		return (int) field(n, FIRST);
	}

	public int nextSibling(int n) {
		return (int) field(n, NEXT);
	}

	public int parent(int n) {
		return (int) field(n, PARENT);
	}

	public int firstEntry() {
		return firstEntry;
	}

	public int nextEntry(int n) {
		return (int) field(n, ORDER_NEXT);
	}

	public boolean hasValue(int n) {
		return n != 0 && (field(n, FLAGS) & 1) != 0;
	}

	public boolean modified(int n) {
		return field(n, EPOCH) > savedEpoch;
	}

	public void markSaved() {
		savedEpoch = mutationEpoch;
	}

	protected long hash(int parent, CharSequence s, int start, int end) {
		long h = seed ^ parent * 0x9e3779b97f4a7c15L;

		for (int i = start; i < end; ++i) {
			h ^= s.charAt(i);
			h *= 0x100000001b3L;
		}

		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;

		return h;
	}

	public int child(int parent, String name, boolean create) {
		long hash = hash(parent, name, 0, name.length());
		return childHashed(parent, name, 0, name.length(), hash, create);
	}

	public boolean hasValue(String path) {
		int node = resolve(path, false);
		return node != 0 && hasValue(node);
	}

	protected int childHashed(int parent, String path, int start, int end, long hash, boolean create) {
		int b = bucket(hash);
		for (int n = bucketHead(b); n != 0; n = (int) field(n, HASH_NEXT)) {

			if (field(n, HASH) != hash || field(n, PARENT) != parent || (field(n, FLAGS) & 2) != 0)
				continue;

			String segment = segment(n);
			int length = end - start;
			if (segment.length() == length && path.regionMatches(start, segment, 0, length))
				return n;
		}

		if (!create)
			return 0;
		return createChild(parent, path, start, end, hash, b);
	}

	protected int createChild(int parent, String path, int start, int end, long hash, int bucket) {

		if (nodes == Integer.MAX_VALUE)
			throw new IllegalStateException("Config node limit exceeded");

		int n = nodes;

		String segment = path.substring(start, end);

		allocate(n, segment);

		++nodes;

		field(n, PARENT, parent);
		field(n, HASH, hash);

		field(n, HASH_NEXT, bucketHead(bucket));

		bucketHead(bucket, n);

		int last = (int) field(parent, LAST);

		field(n, PREV, last);

		if (last == 0)
			field(parent, FIRST, n);
		else
			field(last, NEXT, n);

		field(parent, LAST, n);

		retained += 144 + 2L * segment.length();

		++revision;

		indexAdded();

		return n;
	}

	public int resolve(String path, boolean create) {
		return resolve(0, path, create);
	}

	public int resolve(int parent, String path, boolean create) {
		final int length = path.length();

		int start = 0;
		int depth = 0;

		long hash = seed ^ parent * 0x9e3779b97f4a7c15L;

		for (int i = 0; i <= length; ++i) {
			if (i != length) {
				char c = path.charAt(i);

				if (c != '.') {
					hash ^= c;
					hash *= 0x100000001b3L;
					continue;
				}
			}

			if (++depth > 256)
				throw new IllegalArgumentException("Config path nesting exceeds 256");

			long finishedHash = hash;
			finishedHash ^= finishedHash >>> 33;
			finishedHash *= 0xff51afd7ed558ccdL;
			finishedHash ^= finishedHash >>> 33;

			parent = childHashed(parent, path, start, i, finishedHash, create);

			if (parent == 0)
				return 0;

			start = i + 1;

			hash = seed ^ parent * 0x9e3779b97f4a7c15L;
		}

		return parent;
	}

	public void put(int n, ValueRef value, NodeMetadata meta, boolean modified) {
		long estimate = value.estimatedHeap();

		if (meta != null) {
			estimate += MemoryEstimator.estimate(meta.writtenValue);
			estimate += MemoryEstimator.estimate(meta.comments);
			estimate += MemoryEstimator.estimate(meta.commentAfterValue);
		}

		put(n, value, meta, modified, estimate);
	}

	public void put(int n, ValueRef value, NodeMetadata meta, boolean modified, long estimate) {
		content(n, value, meta);

		retained += estimate - field(n, ESTIMATE);
		field(n, ESTIMATE, estimate);

		if (!hasValue(n)) {
			field(n, FLAGS, 1);
			field(n, ORDER_PREV, lastEntry);

			if (lastEntry == 0)
				firstEntry = n;
			else
				field(lastEntry, ORDER_NEXT, n);

			lastEntry = n;
			entries++;
			revision++;
		}

		field(n, EPOCH, modified ? ++mutationEpoch : 0);
	}

	public boolean remove(String path, boolean subtree) {
		final int node;

		if (subtree) {
			node = resolve(path, false);

			if (node == 0 || !hasValue(node) && firstChild(node) == 0)
				return false;
		} else {
			node = findValueNode(path);

			if (node == 0)
				return false;
		}

		if (subtree) {
			int current = node;

			while (firstChild(current) != 0)
				current = firstChild(current);

			while (current != node) {
				final int next = nextSibling(current);
				final int parent = parent(current);

				erase(current);

				current = next == 0 ? parent : next;

				while (firstChild(current) != 0)
					current = firstChild(current);
			}
		}

		eraseValue(node);
		prune(node);

		++mutationEpoch;
		++revision;

		return true;
	}

	private void eraseValue(int n) {
		if (!hasValue(n))
			return;
		int prev = (int) field(n, ORDER_PREV), next = nextEntry(n);
		if (prev == 0)
			firstEntry = next;
		else
			field(prev, ORDER_NEXT, next);
		if (next == 0)
			lastEntry = prev;
		else
			field(next, ORDER_PREV, prev);
		retained -= field(n, ESTIMATE);
		field(n, ESTIMATE, 0);
		field(n, FLAGS, 0);
		field(n, ORDER_NEXT, 0);
		field(n, ORDER_PREV, 0);
		content(n, EMPTY_VALUE, null);
		releaseEntryPath(n);
		entries--;
	}

	protected void releaseEntryPath(int node) {
	}

	private void erase(int n) {
		eraseValue(n);
		unlink(n);
	}

	private void prune(int n) {
		while (n != 0 && !hasValue(n) && firstChild(n) == 0) {
			int p = parent(n);
			unlink(n);
			n = p;
		}
	}

	private void unlink(int n) {
		int p = parent(n), prev = (int) field(n, PREV), next = nextSibling(n);
		if (prev == 0)
			field(p, FIRST, next);
		else
			field(prev, NEXT, next);
		if (next == 0)
			field(p, LAST, prev);
		else
			field(next, PREV, prev);
		retained -= 144 + 2L * segment(n).length();
		field(n, FLAGS, 2);
	}

	public String path(int n) {
		StringContainer container = new StringContainer();
		appendPath(container, n);
		return container.toString();
	}

	protected String path(int n, StringContainer container) {
		container.clear();
		appendPath(container, n);
		return container.toString();
	}

	protected void appendPath(StringContainer container, int n) {
		if (n == 0)
			return;

		int p = parent(n);

		if (p != 0) {
			appendPath(container, p);
			container.append('.');
		}

		container.append(segment(n));
	}

	public Object getValue(String path) {
		int node = resolve(path, false);

		if (node == 0)
			return null;

		return getValue(node);
	}

	public Set<String> keys(final String section, final boolean recursive, final boolean allEntries) {
		if (allEntries)
			return allEntryKeys();

		return new AbstractSet<String>() {
			@Override
			public Iterator<String> iterator() {
				final long expected = revision;
				final int root = section == null ? 0 : resolve(section, false);

				return new Iterator<String>() {
					int next = section != null && root == 0 ? 0 : firstChild(root);
					private StringContainer relativePath;

					{
						if (recursive)
							while (next != 0 && !hasValue(next))
								next = successor(next, root);
					}

					private void check() {
						if (expected != revision)
							throw new ConcurrentModificationException();
					}

					@Override
					public boolean hasNext() {
						check();
						return next != 0;
					}

					@Override
					public String next() {
						check();

						if (next == 0)
							throw new NoSuchElementException();

						int n = next;

						if (!recursive)
							next = nextSibling(n);
						else {
							next = successor(n, root);

							while (next != 0 && !hasValue(next))
								next = successor(next, root);
						}

						if (!recursive || parent(n) == root)
							return segment(n);
						if (relativePath == null)
							relativePath = new StringContainer(64);
						else
							relativePath.clear();
						appendRelative(relativePath, n, root);
						return relativePath.toString();
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}

			@Override
			public int size() {
				final long expected = revision;
				final int root = section == null ? 0 : resolve(section, false);
				if (section != null && root == 0)
					return 0;
				int count = 0;
				for (int node = firstChild(root); node != 0; node = recursive ? successor(node, root)
						: nextSibling(node)) {
					if (expected != revision)
						throw new ConcurrentModificationException();
					if (!recursive || hasValue(node))
						++count;
				}
				return count;
			}
		};
	}

	protected Set<String> allEntryKeys() {
		return new AbstractSet<String>() {
			@Override
			public Iterator<String> iterator() {
				final long expected = revision;

				return new Iterator<String>() {
					private int next = firstEntry;
					private final StringContainer path = new StringContainer();

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

						next = nextEntry(node);

						return entryKey(node, path);
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
				if (!(key instanceof String))
					return false;

				int node = resolve((String) key, false);
				return node != 0 && hasValue(node);
			}
		};
	}

	protected String entryKey(int node, StringContainer path) {

		path.clear();
		appendPath(path, node);

		return path.toString();
	}

	private int successor(int n, int root) {
		if (firstChild(n) != 0)
			return firstChild(n);
		while (n != root && nextSibling(n) == 0)
			n = parent(n);
		return n == root ? 0 : nextSibling(n);
	}

	private void appendRelative(StringContainer path, int n, int root) {
		int parent = parent(n);
		if (parent != root) {
			appendRelative(path, parent, root);
			path.append('.');
		}
		path.append(segment(n));
	}

	public void copyTo(ConfigStore target) {
		// Preserve node ids, including holes: active parser parent stacks stay valid on
		// spill.
		for (int n = 1; n < nodes; n++) {
			String s = segment(n);
			target.allocate(n, s);
			target.nodes++;
			for (int f = 0; f <= HASH; f++)
				target.field(n, f, field(n, f));
			target.field(n, FLAGS, field(n, FLAGS) & 2);
			target.field(n, ORDER_NEXT, 0);
			target.field(n, ORDER_PREV, 0);
			long hash = target.hash(parent(n), s, 0, s.length());
			target.field(n, HASH, hash);
			int b = target.bucket(hash);
			target.field(n, HASH_NEXT, target.bucketHead(b));
			target.bucketHead(b, n);
			target.indexAdded();
		}
		target.field(0, FIRST, field(0, FIRST));
		target.field(0, LAST, field(0, LAST));
		target.retained = retained;
		for (int n = firstEntry; n != 0; n = nextEntry(n)) {
			NodeMetadata m = metadata(n);
			target.field(n, ESTIMATE, field(n, ESTIMATE));
			ValueRef v = target.disk() ? value(n) : new ValueRef.Memory(copyValue(value(n).get()));
			target.put(n, v, new NodeMetadata(m.writtenValue, m.commentAfterValue,
					m.comments == null ? null : new ArrayList<>(m.comments)), modified(n));
		}
	}

	private static Object copyValue(Object v) {
		if (v instanceof Map) {
			Map<Object, Object> copy = new LinkedHashMap<>();
			for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet())
				copy.put(e.getKey(), copyValue(e.getValue()));
			return copy;
		}
		if (v instanceof Collection) {
			List<Object> copy = new ArrayList<>();
			for (Object x : (Collection<?>) v)
				copy.add(copyValue(x));
			return copy;
		}
		return v;
	}

	public void copyLiveTo(ConfigStore target) {
		copyLiveTo(target, Long.MAX_VALUE);
	}

	/**
	 * Returns false before decoding a value that would exceed the target budget.
	 */
	public boolean copyLiveTo(ConfigStore target, long memoryLimit) {
		for (int n = firstEntry; n != 0; n = nextEntry(n)) {
			int targetNode = target.resolve(path(n), true);
			if (!target.disk() && target.estimatedHeap() + target.additionalEntryHeap(targetNode)
					+ field(n, ESTIMATE) > memoryLimit)
				return false;
			NodeMetadata m = metadata(n);
			ValueRef v = target.disk() ? value(n) : new ValueRef.Memory(copyValue(value(n).get()));
			target.put(targetNode, v, new NodeMetadata(m.writtenValue, m.commentAfterValue,
					m.comments == null ? null : new ArrayList<>(m.comments)), modified(n));
		}
		return true;
	}

	protected int findValueNode(String path) {
		final int node = resolve(path, false);

		return node != 0 && hasValue(node) ? node : 0;
	}

	public boolean hasKeyOrSection(String path) {
		return resolve(path, false) != 0;
	}
}
