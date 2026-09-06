package me.devtec.shared.dataholder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import me.devtec.shared.dataholder.store.AdaptiveConfigStore;
import me.devtec.shared.dataholder.store.DiskNodeStore;
import me.devtec.shared.dataholder.store.LiveCollections;
import me.devtec.shared.dataholder.store.MemoryEstimator;
import me.devtec.shared.dataholder.store.NodeMetadata;
import me.devtec.shared.dataholder.store.ValueRef;

/**
 * Per-Config mutable state. Codecs are shared and contain no document state.
 */
public final class ConfigDocument implements AutoCloseable {
	public final AdaptiveConfigStore storage = new AdaptiveConfigStore();
	public final List<String> header = new ArrayList<>(), footer = new ArrayList<>();
	public String format = "empty";
	public boolean externalDirty;
	private boolean closed;

	public int child(int parent, String segment) {
		storage.beforeMutation(144 + 2L * segment.length());
		return storage.store().resolve(parent, segment, true);
	}

	public void put(int node, Object value, NodeMetadata metadata, boolean modified) {
		ValueRef ref = value instanceof ValueRef ? (ValueRef) value : new ValueRef.Memory(value);
		storage.beforeMutation(ref.estimatedHeap() + MemoryEstimator.estimate(metadata.writtenValue)
				+ MemoryEstimator.estimate(metadata.comments) + storage.store().additionalEntryHeap(node));
		storage.store().put(node, ref, metadata, modified);
	}

	public ConfigDocument copy() {
		ConfigDocument copy = new ConfigDocument();
		if (storage.store().disk())
			copy.storage.forceDisk();
		storage.store().copyTo(copy.storage.store());
		copy.header.addAll(header);
		copy.footer.addAll(footer);
		copy.format = format;
		return copy;
	}

	public Object externalValue(final String key, Object value) {
		if (value == null || !(value instanceof Collection) && !(value instanceof Map))
			return value;
		if (storage.store() instanceof DiskNodeStore)
			((DiskNodeStore) storage.store()).retainExternal(value);
		return LiveCollections.wrap(value, new LiveCollections.Change() {
			@Override
			public Object current(Object fallback) {
				if (closed)
					return fallback;
				int n = storage.store().resolve(key, false);
				return n == 0 ? fallback : storage.store().value(n).get();
			}

			@Override
			public void changed(Object value) {
				if (closed)
					return;
				int n = storage.store().resolve(key, false);
				if (n == 0)
					return;
				NodeMetadata m = storage.store().metadata(n);
				m.writtenValue = null;
				put(n, value, m, true);
				externalDirty = true;
			}
		});
	}

	@Override
	public void close() {
		closed = true;
		storage.close();
	}
}
