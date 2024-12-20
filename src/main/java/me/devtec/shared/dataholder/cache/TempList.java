package me.devtec.shared.dataholder.cache;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.scheduler.Tasker;

public class TempList<V> extends AbstractList<V> {
	private static final long DEFAULT_WAIT_TIME = 5 * 60 * 1000; // 5min

	private final List<Entry<V, Long>> queue = new ArrayList<>();
	private long cacheTime;
	private RemoveCallback<V> callback;

	// internal
	private int task;
	private long inactiveTask;

	/**
	 * @param cacheTime Should be in Minecraft ticks time (1 = 50 milis)
	 */
	public TempList(long cacheTime) {
		this.cacheTime = cacheTime;
	}

	/**
	 * @param cacheTime Should be in Minecraft ticks time (1 = 50 milis)
	 */
	public TempList(long cacheTime, Collection<V> collection) {
		this(cacheTime);
        this.addAll(collection);
	}

	public RemoveCallback<V> getCallback() {
		return callback;
	}

	public TempList<V> setCallback(RemoveCallback<V> callback) {
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
	public void add(int pos, V val) {
		inactiveTask = 0;
		synchronized (queue) {
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
			if (task == 0) {
				task = new Tasker() {

					@Override
					public void run() {
						synchronized (queue) {
							Iterator<Entry<V, Long>> iterator = queue.iterator();
							while (iterator.hasNext()) {
								Entry<V, Long> entry = iterator.next();
								if (entry.getValue() - System.currentTimeMillis() / 50 + cacheTime <= 0) {
									iterator.remove();
									RemoveCallback<V> callback = getCallback();
									if (callback != null) {
										callback.call(entry.getKey());
									}
								}
							}
							if (queue.isEmpty()) {
								if (inactiveTask == 0) {
									inactiveTask = System.currentTimeMillis() / 1000 + DEFAULT_WAIT_TIME;
								} else if (inactiveTask - System.currentTimeMillis() / 1000 <= 0) {
									task = 0;
									cancel();
								}
							}
						}

					}
				}.runRepeating(1, 1);
			}
		}
	}

	@Override
	public V get(int index) {
		if (index < 0 || index >= size()) {
			return null;
		}
		Entry<V, Long> value = queue.get(index);
		value.setValue(System.currentTimeMillis() / 50);
		return value.getKey();
	}

	public int getPosition(V value) {
		Iterator<V> it = iterator();
		int pos = 0;
		if (value == null) {
			while (it.hasNext()) {
				if (it.next() == null) {
					return pos;
				} else {
					++pos;
				}
			}
		} else {
			while (it.hasNext()) {
				if (value.equals(it.next())) {
					return pos;
				} else {
					++pos;
				}
			}
		}
		return -1;
	}

	public boolean update(V value) {
		synchronized (queue) {
			Iterator<Entry<V, Long>> it = queue.iterator();
			if (value == null) {
				while (it.hasNext()) {
					Entry<V, Long> entry = it.next();
					if (entry.getKey() == null) {
						entry.setValue(System.currentTimeMillis() / 50);
						return true;
					}
				}
			} else {
				while (it.hasNext()) {
					Entry<V, Long> entry = it.next();
					if (value.equals(entry.getKey())) {
						entry.setValue(System.currentTimeMillis() / 50);
						return true;
					}
				}
			}
			return false;
		}
	}

	/**
	 * @apiNote Get Entry with value from index without updating time
	 */
	public Entry<V, Long> getRaw(int index) {
		if (index < 0 || index >= size()) {
			return null;
		}
		synchronized (queue) {
			return queue.get(index);
		}
	}

	/**
	 * @apiNote Get expire time of item on specified index
	 */
	public long getTimeOf(int index) {
		if (index < 0 || index >= size()) {
			return 0;
		}
		synchronized (queue) {
			return queue.get(index).getValue();
		}
	}

	/**
	 * @apiNote Get expire time of specified item
	 */
	public long getTimeOf(V value) {
		synchronized (queue) {
			for (Entry<V, Long> next : queue) {
				if (value == null ? next.getKey() == null : value.equals(next.getKey())) {
					return next.getValue();
				}
			}
		}
		return 0;
	}

	@Override
	public V remove(int index) {
		if (index < 0 || index >= size()) {
			return null;
		}
		synchronized (queue) {
			Entry<V, Long> removed = queue.remove(index);
			return removed == null ? null : removed.getKey();
		}
	}

	@Override
	public int size() {
		return queue.size();
	}

	@Override
	public String toString() {
		StringContainer container = new StringContainer(size() * 8).append('[');
		synchronized (queue) {
			Iterator<Entry<V, Long>> iterator = queue.iterator();
			boolean first = true;
			while (iterator.hasNext()) {
				if (first) {
					first = false;
				} else {
					container.append(',').append(' ');
				}
				container.append(iterator.next().getKey() + "");
			}
		}
		return container.append(']').toString();
	}

	@Override
	public Iterator<V> iterator() {
		return new Itr();
	}

	@Override
	public ListIterator<V> listIterator(int index) {
		if (index < 0 || index > size()) {
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size());
		}
		return new ListItr(index);
	}

	private class Itr implements Iterator<V> {
		/**
		 * Index of element to be returned by subsequent call to next.
		 */
		int cursor = 0;

		/**
		 * Index of element returned by most recent call to next or previous. Reset to
		 * -1 if this element is deleted by a call to remove.
		 */
		int lastRet = -1;

		/**
		 * The modCount value that the iterator believes that the backing List should
		 * have. If this expectation is violated, the iterator has detected concurrent
		 * modification.
		 */
		int expectedModCount = modCount;

		@Override
		public boolean hasNext() {
			return cursor != size();
		}

		@Override
		public V next() {
			checkForComodification();
			try {
				int i = cursor;
				Entry<V, Long> next = getRaw(i);
				lastRet = i;
				cursor = i + 1;
				return next.getKey();
			} catch (IndexOutOfBoundsException e) {
				checkForComodification();
				throw new NoSuchElementException();
			}
		}

		@Override
		public void remove() {
			if (lastRet < 0) {
				throw new IllegalStateException();
			}
			checkForComodification();

			try {
				TempList.this.remove(lastRet);
				if (lastRet < cursor) {
					cursor--;
				}
				lastRet = -1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException e) {
				throw new ConcurrentModificationException();
			}
		}

		final void checkForComodification() {
			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
		}
	}

	private class ListItr extends Itr implements ListIterator<V> {
		ListItr(int index) {
			cursor = index;
		}

		@Override
		public boolean hasPrevious() {
			return cursor != 0;
		}

		@Override
		public V previous() {
			checkForComodification();
			try {
				int i = cursor - 1;
				V previous = get(i);
				lastRet = cursor = i;
				return previous;
			} catch (IndexOutOfBoundsException e) {
				checkForComodification();
				throw new NoSuchElementException();
			}
		}

		@Override
		public int nextIndex() {
			return cursor;
		}

		@Override
		public int previousIndex() {
			return cursor - 1;
		}

		@Override
		public void set(V e) {
			if (lastRet < 0) {
				throw new IllegalStateException();
			}
			checkForComodification();

			try {
				TempList.this.set(lastRet, e);
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}

		@Override
		public void add(V e) {
			checkForComodification();

			try {
				int i = cursor;
				TempList.this.add(i, e);
				lastRet = -1;
				cursor = i + 1;
				expectedModCount = modCount;
			} catch (IndexOutOfBoundsException ex) {
				throw new ConcurrentModificationException();
			}
		}
	}
}
