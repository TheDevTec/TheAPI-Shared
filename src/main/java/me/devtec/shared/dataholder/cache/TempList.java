package me.devtec.shared.dataholder.cache;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import me.devtec.shared.scheduler.Scheduler;
import me.devtec.shared.scheduler.Tasker;

public class TempList<V> extends AbstractList<V> {
	private static final long DEFAULT_WAIT_TIME = 5 * 60; // 5 minut v sekundách

	private final ConcurrentLinkedQueue<Node<V>> queue = new ConcurrentLinkedQueue<>();
	private final AtomicInteger task = new AtomicInteger(0);
	private final AtomicLong inactiveUntil = new AtomicLong(0);

	private volatile long cacheTime; // v tickách (1 = 50ms)
	private volatile RemoveCallback<V> callback;

	public TempList(long cacheTime) {
		this.cacheTime = cacheTime;
	}

	public TempList(long cacheTime, Collection<V> collection) {
		this(cacheTime);
		addAll(collection);
	}

	public RemoveCallback<V> getCallback() {
		return callback;
	}

	public List<V> setCallback(RemoveCallback<V> callback) {
		this.callback = callback;
		return this;
	}

	public long getCacheTime() {
		return cacheTime;
	}

	public void setCacheTime(long cacheTime) {
		this.cacheTime = cacheTime;
	}

	public boolean add(V value) {
		queue.add(new Node<>(value, now()));

		// spustíme cleanup jen pokud neběží
		if (task.get() == 0) {
			int id = new Tasker() {
				@Override
				public void run() {
					cleanup();
				}
			}.runRepeating(1, 1);
			task.set(id);
		}
		return true;
	}

	public boolean addAll(Collection<? extends V> values) {
		boolean changed = false;
		for (V v : values) {
			add(v);
			changed = true;
		}
		return changed;
	}

	public V get(int index) {
		int i = 0;
		for (Node<V> n : queue)
			if (i++ == index) {
				n.timestamp = now(); // refresh
				return n.value;
			}
		return null;
	}

	public int size() {
		return queue.size();
	}

	public boolean isEmpty() {
		return queue.isEmpty();
	}

	public long getTimeOf(int index) {
		int i = 0;
		for (Node<V> n : queue)
			if (i++ == index)
				return n.timestamp;
		return 0;
	}

	public long getTimeOf(V value) {
		for (Node<V> n : queue)
			if (value == null ? n.value == null : value.equals(n.value))
				return n.timestamp;
		return 0;
	}

	public boolean update(V value) {
		for (Node<V> n : queue)
			if (value == null ? n.value == null : value.equals(n.value)) {
				n.timestamp = now();
				return true;
			}
		return false;
	}

	public V remove(int index) {
		Iterator<Node<V>> it = queue.iterator();
		int i = 0;
		while (it.hasNext()) {
			Node<V> n = it.next();
			if (i++ == index) {
				it.remove();
				return n.value;
			}
		}
		return null;
	}

	@Override
	public Iterator<V> iterator() {
		Iterator<Node<V>> base = queue.iterator();
		return new Iterator<V>() {
			@Override
			public boolean hasNext() {
				return base.hasNext();
			}

			@Override
			public V next() {
				return base.next().value;
			}

			@Override
			public void remove() {
				base.remove();
			}
		};
	}

	private void cleanup() {
		long now = now();
		long expiry = now - cacheTime;

		Iterator<Node<V>> it = queue.iterator();
		while (it.hasNext()) {
			Node<V> n = it.next();
			if (n.timestamp <= expiry) {
				it.remove();
				RemoveCallback<V> cb = callback;
				if (cb != null)
					cb.call(n.value);
			}
		}

		if (queue.isEmpty()) {
			long until = inactiveUntil.get();
			if (until == 0)
				inactiveUntil.set(nowSeconds() + DEFAULT_WAIT_TIME);
			else if (nowSeconds() >= until) {
				Scheduler.cancelTask(task.getAndSet(0)); // ukončení tasku
				inactiveUntil.set(0);
			}
		} else
			inactiveUntil.set(0);
	}

	private static long now() {
		return System.currentTimeMillis() / 50;
	}

	private static long nowSeconds() {
		return System.currentTimeMillis() / 1000;
	}

	private static final class Node<V> implements Entry<V, Long> {
		final V value;
		volatile long timestamp;

		Node(V value, long timestamp) {
			this.value = value;
			this.timestamp = timestamp;
		}

		@Override
		public V getKey() {
			return value;
		}

		@Override
		public Long getValue() {
			return timestamp;
		}

		@Override
		public Long setValue(Long value) {
			long old = timestamp;
			timestamp = value;
			return old;
		}
	}
}
