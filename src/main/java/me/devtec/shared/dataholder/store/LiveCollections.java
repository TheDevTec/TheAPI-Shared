package me.devtec.shared.dataholder.store;

import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * External mutable views follow their current canonical value across storage
 * migration.
 */
public final class LiveCollections {
	public interface Change {
		Object current(Object fallback);

		void changed(Object value);
	}

	private LiveCollections() {
	}

	public static Object wrap(final Object initial, final Change change) {
		if (initial instanceof List)
			return new AbstractList<Object>() {
				@SuppressWarnings("unchecked")
				List<Object> list() {
					return (List<Object>) change.current(initial);
				}

				@Override
				public int size() {
					return list().size();
				}

				@Override
				public Object get(final int index) {
					return child(list().get(index), () -> list().get(index), change, this::list);
				}

				@Override
				public Object set(int i, Object v) {
					List<Object> l = list();
					Object old = l.set(i, v);
					change.changed(l);
					return old;
				}

				@Override
				public void add(int i, Object v) {
					List<Object> l = list();
					l.add(i, v);
					change.changed(l);
					modCount++;
				}

				@Override
				public Object remove(int i) {
					List<Object> l = list();
					Object old = l.remove(i);
					change.changed(l);
					modCount++;
					return old;
				}

				@Override
				public Iterator<Object> iterator() {
					final List<Object> l = list();
					final Iterator<Object> iterator = l.iterator();
					return new Iterator<Object>() {
						@Override
						public boolean hasNext() {
							return iterator.hasNext();
						}

						@Override
						public Object next() {
							return iterator.next();
						}

						@Override
						public void remove() {
							iterator.remove();
							change.changed(l);
						}
					};
				}
			};
		if (initial instanceof Map)
			return new AbstractMap<Object, Object>() {
				@SuppressWarnings("unchecked")
				Map<Object, Object> map() {
					return (Map<Object, Object>) change.current(initial);
				}

				@Override
				public int size() {
					return map().size();
				}

				@Override
				public Object get(final Object key) {
					return child(map().get(key), () -> map().get(key), change, this::map);
				}

				@Override
				public Object put(Object k, Object v) {
					Map<Object, Object> m = map();
					Object old = m.put(k, v);
					change.changed(m);
					return old;
				}

				@Override
				public Object remove(Object k) {
					Map<Object, Object> m = map();
					Object old = m.remove(k);
					change.changed(m);
					return old;
				}

				@Override
				public void clear() {
					Map<Object, Object> m = map();
					m.clear();
					change.changed(m);
				}

				@Override
				public Set<Entry<Object, Object>> entrySet() {
					return new AbstractSet<Entry<Object, Object>>() {
						@Override
						public int size() {
							return map().size();
						}

						@Override
						public Iterator<Entry<Object, Object>> iterator() {
							final Map<Object, Object> m = map();
							final Iterator<Entry<Object, Object>> iterator = m.entrySet().iterator();
							return new Iterator<Entry<Object, Object>>() {
								@Override
								public boolean hasNext() {
									return iterator.hasNext();
								}

								@Override
								public Entry<Object, Object> next() {
									final Entry<Object, Object> e = iterator.next();
									return new SimpleEntry<Object, Object>(e.getKey(), e.getValue()) {
										private static final long serialVersionUID = -6188570432385112771L;

										@Override
										public Object setValue(Object v) {
											Object old = m.put(getKey(), v);
											change.changed(m);
											super.setValue(v);
											return old;
										}
									};
								}

								@Override
								public void remove() {
									iterator.remove();
									change.changed(m);
								}
							};
						}
					};
				}
			};
		return initial;
	}

	private static Object child(Object initial, final java.util.function.Supplier<Object> current, final Change parent,
			final java.util.function.Supplier<Object> root) {
		return wrap(initial, new Change() {
			@Override
			public Object current(Object fallback) {
				Object value = current.get();
				return value == null ? fallback : value;
			}

			@Override
			public void changed(Object value) {
				parent.changed(root.get());
			}
		});
	}
}