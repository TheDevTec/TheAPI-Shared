package me.devtec.shared.dataholder.cache;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.scheduler.Tasker;

public class TempMap<K, V> extends AbstractMap<K, V> {
	private static long DEFAULT_WAIT_TIME = 5 * 60 * 1000; // 5min

	private LinkedHashMap<Entry<K, V>, Long> queue = new LinkedHashMap<>();
	private long cacheTime;
	private RemoveCallback<Entry<K, V>> callback;

	// internal
	private int task;
	private long inactiveTask;

	/**
	 * @param cacheTime Should be in Minecraft ticks time (1 = 50 milis)
	 */
	public TempMap(long cacheTime) {
		this.cacheTime = cacheTime;
	}

	/**
	 * @param cacheTime Should be in Minecraft ticks time (1 = 50 milis)
	 */
	public TempMap(long cacheTime, Map<K, V> map) {
		this(cacheTime);
		putAll(map);
	}

	public RemoveCallback<Entry<K, V>> getCallback() {
		return callback;
	}

	public TempMap<K, V> setCallback(RemoveCallback<Entry<K, V>> callback) {
		this.callback = callback;
		return this;
	}

	public long getCacheTime() {
		return cacheTime;
	}

	public void setCacheTime(long cacheTime) {
		this.cacheTime = cacheTime;
	}

	@Override
	public V put(K key, V val) {
		inactiveTask = 0;
		Iterator<Entry<Entry<K, V>, Long>> iterator = queue.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<Entry<K, V>, Long> value = iterator.next();
			if (value.getKey().getKey().equals(key)) {
				V previous = value.getKey().setValue(val);
				value.getKey().setValue(val);
				value.setValue(System.currentTimeMillis() / 50);
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
				int hash = 12;
				hash = 29 * hash + Objects.hashCode(key);
				return 29 * hash + Objects.hashCode(value);
			}
		};
		queue.put(entry, System.currentTimeMillis() / 50);
		if (task == 0)
			task = new Tasker() {

				@Override
				public void run() {
					Iterator<Entry<Entry<K, V>, Long>> iterator = queue.entrySet().iterator();
					while (iterator.hasNext()) {
						Entry<Entry<K, V>, Long> entry = iterator.next();
						if (entry.getValue() - System.currentTimeMillis() / 50 + cacheTime <= 0) {
							iterator.remove();
							RemoveCallback<Entry<K, V>> callback = getCallback();
							if (callback != null)
								callback.call(entry.getKey());
						}
					}
					if (queue.isEmpty())
						if (inactiveTask == 0)
							inactiveTask = System.currentTimeMillis() / 1000 + DEFAULT_WAIT_TIME;
						else if (inactiveTask - System.currentTimeMillis() / 1000 <= 0) {
							task = 0;
							cancel();
						}

				}
			}.runRepeating(1, 1);
		return null;
	}

	public long getTimeOf(K key) {
		Iterator<Entry<Entry<K, V>, Long>> iterator = queue.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<Entry<K, V>, Long> value = iterator.next();
			if (value.getKey().getKey().equals(key))
				return value.getValue();
		}
		return 0;
	}

	@Override
	public V get(Object key) {
		Iterator<Entry<Entry<K, V>, Long>> iterator = queue.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<Entry<K, V>, Long> value = iterator.next();
			if (value.getKey().getKey().equals(key)) {
				value.setValue(System.currentTimeMillis() / 50);
				return value.getKey().getValue();
			}
		}
		return null;
	}

	/**
	 * @apiNote Get Entry with value from key without updating time
	 */
	public Entry<V, Long> getRaw(Object key) {
		Iterator<Entry<Entry<K, V>, Long>> iterator = queue.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<Entry<K, V>, Long> value = iterator.next();
			if (value.getKey().getKey().equals(key)) {
				value.setValue(System.currentTimeMillis() / 50);
				return new Entry<V, Long>() {

					@Override
					public V getKey() {
						return value.getKey().getValue();
					}

					@Override
					public Long getValue() {
						return value.getValue();
					}

					@Override
					public Long setValue(Long value) {
						throw new UnsupportedOperationException("You can't modify value inside Entry of TempMap");
					}
				};
			}
		}
		return null;
	}

	@Override
	public V remove(Object key) {
		Iterator<Entry<Entry<K, V>, Long>> iterator = queue.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<Entry<K, V>, Long> value = iterator.next();
			if (value.getKey().getKey().equals(key)) {
				iterator.remove();
				return value.getKey().getValue();
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
