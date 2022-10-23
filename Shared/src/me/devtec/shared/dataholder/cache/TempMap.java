package me.devtec.shared.dataholder.cache;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.scheduler.Tasker;

public class TempMap<K, V> extends AbstractMap<K, V> {
	private LinkedHashMap<Entry<K, V>, Long> queue = new LinkedHashMap<>();
	private long cacheTime;

	/**
	 * @param cacheTime Should be in Minecraft ticks time (1 = 50 milis)
	 */
	public TempMap(long cacheTime) {
		this.cacheTime = cacheTime;
		new Tasker() {
			@Override
			public void run() {
				if (queue.isEmpty())
					return;
				Entry<Entry<K, V>, Long> first = queue.entrySet().iterator().next();
				if (first != null && first.getValue() - System.currentTimeMillis() / 50 + TempMap.this.cacheTime <= 0)
					queue.remove(first.getKey());
			}
		}.runRepeating(1, 1);
	}

	/**
	 * @param cacheTime Should be in Minecraft ticks time (1 = 50 milis)
	 */
	public TempMap(long cacheTime, Map<K, V> map) {
		this(cacheTime);
		putAll(map);
	}

	public long getCacheTime() {
		return cacheTime;
	}

	public void setCacheTime(long cacheTime) {
		this.cacheTime = cacheTime;
	}

	@Override
	public V put(K key, V val) {
		Iterator<Entry<K, V>> iterator = queue.keySet().iterator();
		while (iterator.hasNext()) {
			Entry<K, V> value = iterator.next();
			if (value.getKey().equals(key)) {
				V previous = value.setValue(val);
				queue.remove(value); // update time
				queue.put(value, System.currentTimeMillis() / 50);
				return previous;
			}
		}

		Entry<K, V> entry = new Entry<K, V>() {
			V value = val;

			@Override
			public K getKey() {
				return key;
			}

			@Override
			public V getValue() {
				return val;
			}

			@Override
			public V setValue(V value) {
				this.value = value;
				return this.value;
			}

			@Override
			public int hashCode() {
				return key.hashCode() + value.hashCode();
			}
		};
		queue.put(entry, System.currentTimeMillis() / 50);
		return null;
	}

	@Override
	public V get(Object key) {
		Iterator<Entry<K, V>> iterator = queue.keySet().iterator();
		while (iterator.hasNext()) {
			Entry<K, V> value = iterator.next();
			if (value.getKey().equals(key)) {
				queue.remove(value); // update time
				queue.put(value, System.currentTimeMillis() / 50);
				return value.getValue();
			}
		}
		return null;
	}

	@Override
	public V remove(Object key) {
		Iterator<Entry<K, V>> iterator = queue.keySet().iterator();
		while (iterator.hasNext()) {
			Entry<K, V> value = iterator.next();
			if (value.getKey().equals(key)) {
				queue.remove(value);
				return value.getValue();
			}
		}
		return null;
	}

	@Override
	public int size() {
		return queue.size();
	}

	@Override
	public String toString() {
		StringContainer container = new StringContainer(size() * 8).append('{');
		Iterator<Entry<K, V>> iterator = queue.keySet().iterator();
		boolean first = true;
		while (iterator.hasNext()) {
			if (first)
				first = false;
			else
				container.append(',').append(' ');
			Entry<K, V> entry = iterator.next();
			container.append(entry.getKey() + "=" + entry.getValue());
		}
		return container.append('}').toString();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return queue.keySet();
	}
}
