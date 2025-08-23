package me.devtec.shared.dataholder.cache;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.scheduler.Tasker;

public class TempMap<K, V> extends AbstractMap<K, V> {
	private static final long DEFAULT_WAIT_TIME = 5 * 60 * 1000; // 5min

	private final Map<Entry<K, V>, Long> queue = new ConcurrentHashMap<>();
	private long cacheTime;
	private RemoveCallback<Entry<K, V>> callback;

	// internal
	private volatile Tasker task; // reference na běžící Tasker
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
		inactiveTask = System.currentTimeMillis() / 1000L + DEFAULT_WAIT_TIME;

		for (Entry<Entry<K, V>, Long> value : queue.entrySet())
			if (Objects.equals(value.getKey().getKey(), key)) {
				V previous = value.getKey().setValue(val);
				value.setValue(System.currentTimeMillis() / 50L);
				return previous;
			}

		Entry<K, V> entry = new Entry<K, V>() {
			V value = val;

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
				V previous = this.value;
				this.value = value;
				return previous;
			}

			@Override
			public String toString() {
				return getKey() + "=" + getValue();
			}
		};
		queue.put(entry, System.currentTimeMillis() / 50L);
		startTaskIfNeeded();
		return null;
	}

	private void startTaskIfNeeded() {
		if (task != null && !task.isCancelled()) return;

		task = new Tasker() {
			@Override
			public void run() {
				long now = System.currentTimeMillis() / 50L;
				Iterator<Entry<Entry<K, V>, Long>> iterator = queue.entrySet().iterator();

				while (iterator.hasNext()) {
					Entry<Entry<K, V>, Long> e = iterator.next();
					if (e.getValue() - now + cacheTime <= 0) {
						iterator.remove();
						if (callback != null)
							callback.call(e.getKey());
					}
				}

				if (queue.isEmpty() && inactiveTask - System.currentTimeMillis() / 1000 <= 0) {
					task = null;
					cancel();
				}
			}
		};
		task.runRepeating(1, 1);
	}

	public long getTimeOf(K key) {
		for (Entry<Entry<K, V>, Long> value : queue.entrySet())
			if (value.getKey().getKey().equals(key))
				return value.getValue();
		return 0;
	}

	@Override
	public V get(Object key) {
		for (Entry<Entry<K, V>, Long> value : queue.entrySet())
			if (value.getKey().getKey().equals(key)) {
				value.setValue(System.currentTimeMillis() / 50L);
				return value.getKey().getValue();
			}
		return null;
	}

	/**
	 * @apiNote Get Entry with value from key without updating time
	 */
	public Entry<V, Long> getRaw(Object key) {
		for (Entry<Entry<K, V>, Long> value : queue.entrySet())
			if (value.getKey().getKey().equals(key)) {
				value.setValue(System.currentTimeMillis() / 50L);
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

					@Override
					public String toString() {
						return getKey() + "=" + getValue();
					}
				};
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
		boolean first = true;
		for (Entry<K, V> entry : queue.keySet()) {
			if (first)
				first = false;
			else
				container.append(',').append(' ');
			container.append(entry.getKey() + "=" + entry.getValue());
		}
		return container.append('}').toString();
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return queue.keySet();
	}
}
