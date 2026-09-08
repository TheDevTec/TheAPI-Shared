package me.devtec.shared.dataholder.store;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;

/**
 * Compatibility boundary only: DataValue objects are detached; writes go
 * through put/set.
 */
public final class ConfigMapView extends AbstractMap<String, DataValue> {
	private final ConfigDocument document;

	public ConfigMapView(ConfigDocument document) {
		this.document = document;
	}

	@Override
	public int size() {
		return document.storage.store().size();
	}

	@Override
	public boolean containsKey(Object key) {
		ConfigStore store = document.storage.store();
		return key instanceof String && store.hasValue((String) key);
	}

	public Object getValue(Object key, boolean writtenValue) {
		if (!(key instanceof String))
			return null;

		String path = (String) key;
		ConfigStore store = document.storage.store();

		if (writtenValue) {
			int node = store.resolve(path, false);

			if (!store.hasValue(node))
				return null;

			return store.getWrittenValue(node);
		}

		return store.getValue(path);
	}

	@Override
	public DataValue get(Object key) {
		if (!(key instanceof String))
			return null;

		ConfigStore store = document.storage.store();
		int node = store.resolve((String) key, false);

		if (!store.hasValue(node))
			return null;

		DataValue value = DataValue.of(store.getWrittenValue(node), store.getValue(node), store.getComment(node),
				store.getComments(node));

		value.modified = store.modified(node);
		return value;
	}

	@Override
	public DataValue put(String key, DataValue value) {
		DataValue old = get(key);
		int n = document.child(0, key);
		document.put(n, value.value, new NodeMetadata(value.writtenValue, value.commentAfterValue, value.comments),
				value.modified);
		return old;
	}

	@Override
	public DataValue remove(Object key) {
		DataValue old = get(key);
		if (old != null)
			document.storage.store().remove((String) key, false);
		return old;
	}

	@Override
	public Set<Entry<String, DataValue>> entrySet() {
		return new AbstractSet<Entry<String, DataValue>>() {
			@Override
			public int size() {
				return ConfigMapView.this.size();
			}

			@Override
			public Iterator<Entry<String, DataValue>> iterator() {
				final ConfigStore store = document.storage.store();
				final long generation = document.storage.generation();

				return new Iterator<Entry<String, DataValue>>() {
					private int next = store.firstEntry();
					private final StringContainer path = new StringContainer(64);

					private void check() {
						if (generation != document.storage.generation())
							throw new ConcurrentModificationException();
					}

					@Override
					public boolean hasNext() {
						check();
						return next != 0;
					}

					@Override
					public Entry<String, DataValue> next() {
						check();

						if (next == 0)
							throw new NoSuchElementException();

						final int node = next;
						next = store.nextEntry(node);

						final String key = store.entryKey(node, path);

						DataValue value = DataValue.of(store.getWrittenValue(node), store.getValue(node),
								store.getComment(node), store.getComments(node));

						value.modified = store.modified(node);

						return new SimpleEntry<String, DataValue>(key, value) {
							private static final long serialVersionUID = -7441730680501051025L;

							@Override
							public DataValue setValue(DataValue value) {
								DataValue old = ConfigMapView.this.put(key, value);
								super.setValue(value);
								return old;
							}
						};
					}
				};
			}
		};
	}
}