package me.devtec.shared.dataholder.cache;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.dataholder.StringContainer;

public class ConcurrentLinkedHashMap<K, V> implements Map<K, V> {
	private static final int DEFAULT_SIZE = 16;
	private static final float DEFAULT_LOAD_FACTOR = 0.75f;
	private static final int MAXIMUM_CAPACITY = 1 << 30;
	private static final Entry<?, ?> DELETED_ENTRY = new Entry<>(null, null, null, null) {};

	private transient volatile Entry<K, V>[] entries;
	private transient volatile AtomicInteger size = new AtomicInteger();
	private transient final ReentrantLock lock;
	private transient Entry<K, V> head;
	private transient Entry<K, V> tail;
	private final float loadFactor;
	private int threshold;

	@SuppressWarnings("unchecked")
	public ConcurrentLinkedHashMap(int size, float loadFactor) {
		this.loadFactor = loadFactor;
		int capacity = calculateCapacity(size <= 0 ? DEFAULT_SIZE : size);
		entries = new Entry[capacity];
		threshold = (int) (capacity * loadFactor);
		lock = new ReentrantLock();
		head = null;
		tail = null;
	}

	public ConcurrentLinkedHashMap() {
		this(DEFAULT_SIZE, DEFAULT_LOAD_FACTOR);
	}

	private int calculateCapacity(int size) {
		int capacity = 1;
		while (capacity < size && capacity < MAXIMUM_CAPACITY)
			capacity <<= 1;
		return Math.min(capacity, MAXIMUM_CAPACITY);
	}

	private int hash(Object key) {
		int h = key == null ? 0 : key.hashCode();
		return (h ^ h >>> 16) & entries.length - 1;
	}

	@Override
	public V put(@Nonnull K key, V value) {
		lock.lock();
		try {
			if (size.get() >= threshold)
				resize();
			return putInternal(key, value, false);
		} finally {
			lock.unlock();
		}
	}

	private V putInternal(K key, V value, boolean fromResize) {
		int index = hash(key);
		int startIndex = index;
		Entry<K, V> deletedEntry = null;
		int deletedIndex = -1;

		while (entries[index] != null && entries[index] != DELETED_ENTRY) {
			if (entries[index].key.equals(key)) {
				V oldValue = entries[index].value;
				entries[index].value = value;
				return oldValue;
			}
			index = index + 1 & entries.length - 1;
			if (index == startIndex) {
				resize();
				return putInternal(key, value, false);
			}
		}

		if (entries[index] == DELETED_ENTRY) {
			deletedEntry = entries[index];
			deletedIndex = index;
		}

		Entry<K, V> newEntry;
		if (fromResize)
			newEntry = new Entry<>(key, value, null, null);
		else {
			newEntry = new Entry<>(key, value, null, null);
			if (head == null)
				head = tail = newEntry;
			else {
				tail.next = newEntry;
				newEntry.prev = tail;
				tail = newEntry;
			}
		}

		if (deletedEntry != null)
			entries[deletedIndex] = newEntry;
		else {
			entries[index] = newEntry;
			size.incrementAndGet();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private void resize() {
		int oldCapacity = entries.length;
		if (oldCapacity >= MAXIMUM_CAPACITY) {
			threshold = Integer.MAX_VALUE;
			return;
		}

		int newCapacity = oldCapacity << 1;
		if (newCapacity > MAXIMUM_CAPACITY || newCapacity < 0)
			newCapacity = MAXIMUM_CAPACITY;

		entries = new Entry[newCapacity];
		threshold = (int) (newCapacity * loadFactor);
		AtomicInteger newSize = new AtomicInteger(0);

		Entry<K, V> current = head;
		while (current != null) {
			int index = hash(current.key);
			while (entries[index] != null)
				index = index + 1 & entries.length - 1;
			entries[index] = new Entry<>(current.key, current.value, null, null);
			newSize.incrementAndGet();
			current = current.next;
		}

		size.set(newSize.get());
	}

	@Override
	public V get(@Nonnull Object key) {
		int index = hash(key);
		int startIndex = index;

		while (entries[index] != null) {
			Entry<K, V> entry = entries[index];
			if (entry != DELETED_ENTRY && key.equals(entry.key))
				return entry.value;
			index = index + 1 & entries.length - 1;
			if (index == startIndex)
				break;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public V remove(@Nonnull Object key) {
		lock.lock();
		try {
			int index = hash(key);
			int startIndex = index;

			while (entries[index] != null) {
				Entry<K, V> entry = entries[index];
				if (entry != DELETED_ENTRY && key.equals(entry.key)) {
					V value = entry.value;
					entries[index] = (Entry<K, V>) DELETED_ENTRY;
					size.decrementAndGet();

					removeFromLinkedList(entry);
					return value;
				}
				index = index + 1 & entries.length - 1;
				if (index == startIndex)
					break;
			}
			return null;
		} finally {
			lock.unlock();
		}
	}

	private void removeFromLinkedList(Entry<K, V> entry) {
		if (entry == null) return;

		if (entry.prev != null)
			entry.prev.next = entry.next;
		else
			head = entry.next;

		if (entry.next != null)
			entry.next.prev = entry.prev;
		else
			tail = entry.prev;

		entry.prev = null;
		entry.next = null;
	}

	@Override
	public void clear() {
		lock.lock();
		try {
			Arrays.fill(entries, null);
			size.set(0);
			head = null;
			tail = null;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int size() {
		return size.get();
	}

	@Override
	public boolean isEmpty() {
		return size.get() == 0;
	}

	@Override
	public boolean containsKey(@Nonnull Object key) {
		return get(key) != null;
	}

	@Override
	public boolean containsValue(Object value) {
		Entry<K, V> current = head;
		while (current != null) {
			if (Objects.equals(current.value, value))
				return true;
			current = current.next;
		}
		return false;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		lock.lock();
		try {
			for (Map.Entry<? extends K, ? extends V> entry : m.entrySet())
				put(entry.getKey(), entry.getValue());
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Set<K> keySet() {
		return new KeySet();
	}

	@Override
	public Collection<V> values() {
		return new ValueCollection();
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return new EntrySet();
	}

	@Override
	public String toString() {
		StringContainer container = new StringContainer("{", 0, 32);
		Entry<K, V> current = head;
		boolean first = true;
		while (current != null) {
			if (!first)
				container.append(',').append(' ');
			container.append(current.toString());
			first = false;
			current = current.next;
		}
		return container.append('}').toString();
	}

	private static class Entry<K, V> implements Map.Entry<K, V> {
		final K key;
		V value;
		Entry<K, V> prev;
		Entry<K, V> next;

		Entry(K key, V value, Entry<K, V> prev, Entry<K, V> next) {
			this.key = key;
			this.value = value;
			this.prev = prev;
			this.next = next;
		}

		@Override
		public K getKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}

		@Override
		public V setValue(V value) {
			V oldValue = this.value;
			this.value = value;
			return oldValue;
		}

		@Override
		public String toString() {
			return key + "=" + value;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Map.Entry)) return false;

			Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
			return Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue());
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(key) ^ Objects.hashCode(value);
		}
	}

	private class KeySet extends AbstractSet<K> {
		@Override
		public Iterator<K> iterator() {
			return new KeyIterator();
		}

		@Override
		public int size() {
			return ConcurrentLinkedHashMap.this.size();
		}

		@Override
		public boolean contains(Object o) {
			return ConcurrentLinkedHashMap.this.containsKey(o);
		}

		@Override
		public boolean remove(Object o) {
			return ConcurrentLinkedHashMap.this.remove(o) != null;
		}

		@Override
		public void clear() {
			ConcurrentLinkedHashMap.this.clear();
		}
	}

	private class ValueCollection extends AbstractSet<V> {
		@Override
		public Iterator<V> iterator() {
			return new ValueIterator();
		}

		@Override
		public int size() {
			return ConcurrentLinkedHashMap.this.size();
		}

		@Override
		public boolean contains(Object o) {
			return ConcurrentLinkedHashMap.this.containsValue(o);
		}

		@Override
		public void clear() {
			ConcurrentLinkedHashMap.this.clear();
		}
	}

	private class EntrySet extends AbstractSet<Map.Entry<K, V>> {
		@Override
		public Iterator<Map.Entry<K, V>> iterator() {
			return new EntryIterator();
		}

		@Override
		public int size() {
			return ConcurrentLinkedHashMap.this.size();
		}

		@Override
		public boolean contains(Object o) {
			if (!(o instanceof Map.Entry)) return false;

			Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
			V value = ConcurrentLinkedHashMap.this.get(e.getKey());
			return value != null && value.equals(e.getValue());
		}

		@Override
		public boolean remove(Object o) {
			if (!(o instanceof Map.Entry)) return false;

			Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
			V value = ConcurrentLinkedHashMap.this.get(e.getKey());
			if (value != null && value.equals(e.getValue())) {
				ConcurrentLinkedHashMap.this.remove(e.getKey());
				return true;
			}
			return false;
		}

		@Override
		public void clear() {
			ConcurrentLinkedHashMap.this.clear();
		}
	}

	private abstract class AbstractIterator {
		protected Entry<K, V> next;
		protected Entry<K, V> current;
		AbstractIterator() {
			next = head;
			current = null;
			size.get();
		}

		public boolean hasNext() {
			return next != null;
		}

		protected Entry<K, V> nextEntry() {
			if (next == null) throw new java.util.NoSuchElementException();
			current = next;
			next = next.next;
			return current;
		}

		public void remove() {
			if (current == null) throw new IllegalStateException();
			ConcurrentLinkedHashMap.this.remove(current.key);
			current = null;
			size.get();
		}
	}

	private class KeyIterator extends AbstractIterator implements Iterator<K> {
		@Override
		public K next() {
			return nextEntry().key;
		}
	}

	private class ValueIterator extends AbstractIterator implements Iterator<V> {
		@Override
		public V next() {
			return nextEntry().value;
		}
	}

	private class EntryIterator extends AbstractIterator implements Iterator<Map.Entry<K, V>> {
		@Override
		public Map.Entry<K, V> next() {
			return nextEntry();
		}
	}
}