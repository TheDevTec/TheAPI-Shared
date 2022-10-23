package me.devtec.shared.dataholder.cache;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.scheduler.Tasker;

public class TempList<V> extends AbstractList<V> {
	private ArrayDeque<Entry<V, Long>> queue = new ArrayDeque<>();
	private long cacheTime;

	/**
	 * @param cacheTime Should be in Minecraft ticks time (1 = 50 milis)
	 */
	public TempList(long cacheTime) {
		this.cacheTime = cacheTime;
		new Tasker() {
			@Override
			public void run() {
				Entry<V, Long> first = queue.peekFirst();
				if (first != null && first.getValue() - System.currentTimeMillis() / 50 + TempList.this.cacheTime <= 0)
					queue.removeFirst();
			}
		}.runRepeating(1, 1);
	}

	/**
	 * @param cacheTime Should be in Minecraft ticks time (1 = 50 milis)
	 */
	public TempList(long cacheTime, Collection<V> collection) {
		this(cacheTime);
		for (V value : collection)
			add(value);
	}

	public long getCacheTime() {
		return cacheTime;
	}

	public void setCacheTime(long cacheTime) {
		this.cacheTime = cacheTime;
	}

	@Override
	public void add(int pos, V val) {
		queue.add(new Entry<V, Long>() {
			long time = System.currentTimeMillis() / 50;

			@Override
			public V getKey() {
				return val;
			}

			@Override
			public Long getValue() {
				return time;
			}

			@Override
			public Long setValue(Long value) {
				time = value;
				return time;
			}
		});
	}

	@Override
	public V get(int index) {
		if (index < 0 || index >= size())
			return null;
		if (index == 0) {
			Entry<V, Long> first = queue.peekFirst();
			first.setValue(System.currentTimeMillis() / 50);
			queue.add(first);
			return first.getKey();
		}
		if (index == size() - 1) {
			Entry<V, Long> last = queue.getLast();
			last.setValue(System.currentTimeMillis() / 50);
			return last.getKey();
		}
		Iterator<Entry<V, Long>> iterator = queue.iterator();
		int pos = 0;
		while (iterator.hasNext()) {
			Entry<V, Long> value = iterator.next();
			if (pos++ == index) {
				iterator.remove();
				value.setValue(System.currentTimeMillis() / 50);
				queue.add(value); // update time
				return value.getKey();
			}
		}
		return null;
	}

	@Override
	public V remove(int index) {
		if (index < 0 || index >= size())
			return null;
		if (index == 0)
			return queue.peekFirst().getKey();
		if (index == size() - 1)
			return queue.peekLast().getKey();
		Iterator<Entry<V, Long>> iterator = queue.iterator();
		int pos = 0;
		while (iterator.hasNext()) {
			Entry<V, Long> value = iterator.next();
			if (pos++ == index) {
				iterator.remove();
				return value.getKey();
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
		StringContainer container = new StringContainer(size() * 8).append('[');
		Iterator<Entry<V, Long>> iterator = queue.iterator();
		boolean first = true;
		while (iterator.hasNext()) {
			if (first)
				first = false;
			else
				container.append(',').append(' ');
			container.append(iterator.next().getKey() + "");
		}
		return container.append(']').toString();
	}
}
