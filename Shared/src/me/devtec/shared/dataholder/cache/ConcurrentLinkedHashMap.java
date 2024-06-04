package me.devtec.shared.dataholder.cache;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ConcurrentLinkedHashMap<K, V> implements Map<K, V> {
	private final ConcurrentHashMap<K, V> map;
	private final ConcurrentLinkedDeque<K> order;

	public ConcurrentLinkedHashMap() {
		map = new ConcurrentHashMap<>();
		order = new ConcurrentLinkedDeque<>();
	}

	@Override
	public synchronized V put(K key, V value) {
		if (!map.containsKey(key))
			order.add(key);
		return map.put(key, value);
	}

	@Override
	public V get(Object key) {
		return map.get(key);
	}

	@Override
	public synchronized V remove(Object key) {
		order.remove(key);
		return map.remove(key);
	}

	@Override
	public synchronized void clear() {
		map.clear();
		order.clear();
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		for (Map.Entry<? extends K, ? extends V> entry : m.entrySet())
			put(entry.getKey(), entry.getValue());
	}

	@Override
	public Set<K> keySet() {
		return map.keySet();
	}

	@Override
	public Collection<V> values() {
		return map.values();
	}

	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		return new AbstractSet<Map.Entry<K, V>>() {
			@Override
			public Iterator<Map.Entry<K, V>> iterator() {
				return new Iterator<Map.Entry<K, V>>() {
					private final Iterator<K> orderIterator = order.iterator();

					@Override
					public boolean hasNext() {
						return orderIterator.hasNext();
					}

					@Override
					public Map.Entry<K, V> next() {
						K key = orderIterator.next();
						V value = map.get(key);
						return new Map.Entry<K, V>() {
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
								return map.put(key, value);
							}
						};
					}
				};
			}

			@Override
			public int size() {
				return map.size();
			}
		};
	}

	@Override
	public String toString() {
		return map.toString();
	}
}