package me.devtec.shared.dataholder.cache;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.scheduler.Tasker;

public class TempList<V> extends AbstractList<V> {
	private List<Entry<V, Long>> queue = new ArrayList<>();
	private long cacheTime;

	/**
	 * @param cacheTime Should be in Minecraft ticks time (1 = 50 milis)
	 */
	public TempList(long cacheTime) {
		this.cacheTime = cacheTime;
		new Tasker() {
			@Override
			public void run() {
				Iterator<Entry<V, Long>> iterator = queue.iterator();
				while (iterator.hasNext())
					if (iterator.next().getValue() - System.currentTimeMillis() / 50 + TempList.this.cacheTime <= 0)
						iterator.remove();
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
		Entry<V, Long> value = queue.get(index);
		value.setValue(System.currentTimeMillis() / 50);
		return value.getKey();
	}

	@Override
	public V remove(int index) {
		if (index < 0 || index >= size())
			return null;
		Entry<V, Long> removed = queue.remove(index);
		return removed == null ? null : removed.getKey();
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
