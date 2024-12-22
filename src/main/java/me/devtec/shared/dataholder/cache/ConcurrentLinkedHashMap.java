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

	private transient volatile Entry<K, V>[] entries;
	private transient volatile AtomicInteger size = new AtomicInteger();
	private transient final ReentrantLock lock;
	private transient WeakEntry head;
	private transient WeakEntry tail;
	private final float loadFactor;

	@SuppressWarnings("unchecked")
	public ConcurrentLinkedHashMap(int size, float loadFactor) {
		this.loadFactor = loadFactor;
		entries = new Entry[size <= 0 ? DEFAULT_SIZE : size];
		lock = new ReentrantLock();
		head = null;
		tail = null;
	}

	public ConcurrentLinkedHashMap() {
		this(DEFAULT_SIZE, DEFAULT_LOAD_FACTOR);
	}

	private int hash(Object key) {
		return key == null ? 0 : (key.hashCode() & 0x7fffffff) % entries.length;
	}

	@Override
	public V put(@Nonnull K key, V value) {
		lock.lock();
		try {
			if (size.get() >= entries.length * loadFactor) {
				resize();
			}
			int index = hash(key);
			while (entries[index] != null) {
				if (entries[index].getKey().equals(key)) {
					return entries[index].setValue(value);
				}
				index = (index + 1) % entries.length;
			}
			WeakEntry newEntry = new WeakEntry(value) {
				@Override
				public K getKey() {
					return key;
				}
			};
			if (head == null) {
				head = newEntry;
			} else {
				tail.next = newEntry;
				newEntry.prev = tail;
			}
			tail = newEntry;
			entries[index] = newEntry;
			size.incrementAndGet();
			return null;
		} finally {
			lock.unlock();
		}
	}

	private void putInternal(Entry<K, V> entry) {
		lock.lock();
		try {
			if (size.get() >= entries.length * loadFactor) {
				resize();
			}
			int index = hash(entry.getKey());
			while (entries[index] != null) {
				if (entries[index].getKey().equals(entry.getKey())) {
					entries[index].setValue(entry.getValue());
					return;
				}
				index = (index + 1) % entries.length;
			}
			entries[index] = entry;
			size.incrementAndGet();
		} finally {
			lock.unlock();
		}
	}

	@SuppressWarnings("unchecked")
	private void resize() {
		Entry<K, V>[] oldEntries = entries;
		entries = new Entry[(int) (oldEntries.length * 1.75)];
		size.set(0);
		for (Entry<K, V> entry : oldEntries) {
			if (entry != null) {
				putInternal(entry);
			}
		}
	}

	@Override
	public V get(@Nonnull Object key) {
		int index = hash(key);
		while (entries[index] != null) {
			if (entries[index].getKey().equals(key)) {
				return entries[index].getValue();
			}
			index = (index + 1) % entries.length;
		}
		return null;
	}

	@Override
	public V remove(@Nonnull Object key) {
		lock.lock();
		try {
			int index = hash(key);
			while (entries[index] != null) {
				if (entries[index].getKey().equals(key)) {
					V value = entries[index].getValue();
					size.decrementAndGet();
					removeFromLinkedList((WeakEntry) entries[index]);
					entries[index] = null;
					rehash();
					return value;
				}
				index = (index + 1) % entries.length;
			}
			return null;
		} finally {
			lock.unlock();
		}
	}

	private void removeFromLinkedList(WeakEntry entry) {
		if (entry == null) {
			return;
		}
		if (entry.prev != null) {
			entry.prev.next = entry.next;
		} else {
			head = entry.next;
		}
		if (entry.next != null) {
			entry.next.prev = entry.prev;
		} else {
			tail = entry.prev;
		}
	}

	@SuppressWarnings("unchecked")
	private void rehash() {
		Entry<K, V>[] oldEntries = entries;
		entries = new Entry[oldEntries.length];
		size.set(0);
		for (Entry<K, V> entry : oldEntries) {
			if (entry != null) {
				putInternal(entry);
			}
		}
	}

	@Override
	public void clear() {
		lock.lock();
		try {
			Arrays.fill(entries, null);
			size.set(0);
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
		try {
			if (head != null) {
				for (WeakEntry entry = head; entry != null; entry = entry.next) {
					if (Objects.equals(entry.getValue(), value)) {
						return true;
					}
				}
			}
			return false;
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
			put(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public Set<K> keySet() {
		return new AbstractSet<K>() {
			@Override
			public Iterator<K> iterator() {
				return new Iterator<K>() {
					boolean first = true;
					private WeakEntry entry;

					@Override
					public void remove() {
						ConcurrentLinkedHashMap.this.remove(entry.getKey());
					}

					@Override
					public boolean hasNext() {
						return first ? head != null : entry.next != null;
					}

					@Override
					public K next() {
						if (first) {
							first = false;
							return (entry = head).getKey();
						}
						return (entry = entry.next).getKey();
					}
				};
			}

			@Override
			public int size() {
				return size.get();
			}
		};
	}

	@Override
	public Collection<V> values() {
		return new AbstractSet<V>() {
			@Override
			public Iterator<V> iterator() {
				return new Iterator<V>() {
					boolean first = true;
					private WeakEntry entry;

					@Override
					public void remove() {
						ConcurrentLinkedHashMap.this.remove(entry.getKey());
					}

					@Override
					public boolean hasNext() {
						return first ? head != null : entry.next != null;
					}

					@Override
					public V next() {
						if (first) {
							first = false;
							return (entry = head).getValue();
						}
						return (entry = entry.next).getValue();
					}
				};
			}

			@Override
			public int size() {
				return size.get();
			}
		};
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return new AbstractSet<Entry<K, V>>() {
			@Override
			public Iterator<Entry<K, V>> iterator() {
				return new Iterator<Entry<K, V>>() {
					boolean first = true;
					private WeakEntry entry;

					@Override
					public void remove() {
						ConcurrentLinkedHashMap.this.remove(entry.getKey());
					}

					@Override
					public boolean hasNext() {
						return first ? head != null : entry.next != null;
					}

					@Override
					public Entry<K, V> next() {
						if (first) {
							first = false;
							return entry = head;
						}
						return entry = entry.next;
					}
				};
			}

			@Override
			public int size() {
				return size.get();
			}
		};
	}

	@Override
	public String toString() {
		lock.lock();
		try {
			StringContainer container = new StringContainer("{", 0, 32);
			boolean first = true;
			for (WeakEntry entry = head; entry != null; entry = entry.next) {
				if (!first) {
					container.append(',').append(' ');
				}
				container.append(entry.toString());
				first = false;
			}
			return container.append('}').toString();
		} finally {
			lock.unlock();
		}
	}

	abstract class WeakEntry implements Entry<K, V> {
		private V value;
		protected WeakEntry prev;
		protected WeakEntry next;

		WeakEntry(V value) {
			this.value = value;
			prev = null;
			next = null;
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
			return getKey() + "=" + value;
		}
	}
}
