package me.devtec.shared.dataholder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import me.devtec.shared.dataholder.store.AdaptiveConfigStore;
import me.devtec.shared.dataholder.store.ConfigStore;
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
		storage.beforeMutation(144L + 2L * segment.length());
		return storage.store().child(parent, segment, true);
	}

	public void put(int node, Object value, NodeMetadata metadata, boolean modified) {
		ValueRef ref = value instanceof ValueRef ? (ValueRef) value : new ValueRef.Memory(value);

		long estimate = ref.estimatedHeap();

		if (metadata != null) {
			estimate += MemoryEstimator.estimate(metadata.writtenValue);
			estimate += MemoryEstimator.estimate(metadata.comments);
			estimate += MemoryEstimator.estimate(metadata.commentAfterValue);
		}

		ConfigStore current = storage.store();

		storage.beforeMutation(estimate + current.additionalEntryHeap(node));

		storage.store().put(node, ref, metadata, modified, estimate);
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
				ConfigStore store = storage.store();
				int n = store.resolve(key, false);

				return n == 0 ? fallback : store.value(n).get();
			}

			@Override
			public void changed(Object value) {
				if (closed)
					return;
				ConfigStore store = storage.store();

				int n = store.resolve(key, false);

				if (n == 0)
					return;

				NodeMetadata metadata = store.metadata(n);
				metadata.writtenValue = null;

				put(n, value, metadata, true);
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
