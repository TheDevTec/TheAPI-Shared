package me.devtec.shared.dataholder.codec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.store.ConfigMemoryPolicy;
import me.devtec.shared.dataholder.store.DiskNodeStore;
import me.devtec.shared.dataholder.store.DiskValueStore;
import me.devtec.shared.dataholder.store.MemoryEstimator;

/**
 * Spills one collection independently of the number of Config keys.
 */
final class AdaptiveValueBuilder {

	private static final long MIN_SPILL_THRESHOLD = 32768L;
	private static final long BASE_ESTIMATE = 48L;
	private static final long ENTRY_OVERHEAD = 40L;

	private final ConfigDocument document;
	private final boolean map;
	private final long spillThreshold;

	private List<Object> list;
	private Map<Object, Object> entries;

	private DiskValueStore disk;
	private long ref;
	private long estimate = BASE_ESTIMATE;

	AdaptiveValueBuilder(ConfigDocument document, boolean map) {
		this.document = document;
		this.map = map;

		long budget = ConfigMemoryPolicy.budget();
		long threshold = budget >>> 4;
		spillThreshold = threshold < MIN_SPILL_THRESHOLD ? MIN_SPILL_THRESHOLD : threshold;

		if (map)
			entries = new LinkedHashMap<>();
		else
			list = new ArrayList<>();
	}

	void add(Object key, Object value) {
		long added = ENTRY_OVERHEAD + MemoryEstimator.estimate(value);

		if (map)
			added += MemoryEstimator.estimate(key);

		estimate += added;

		if (disk == null && estimate > spillThreshold)
			spill();

		if (disk != null)
			disk.appendValue(ref, key, value);
		else if (map)
			entries.put(key, value);
		else
			list.add(value);
	}

	private void spill() {
		document.storage.forceDisk();

		DiskNodeStore store = (DiskNodeStore) document.storage.store();
		disk = store.values;
		ref = disk.composite(map);

		if (map) {
			for (Map.Entry<Object, Object> entry : entries.entrySet())
				disk.appendValue(ref, entry.getKey(), entry.getValue());

			entries = null;
		} else {
			for (Object value : list)
				disk.appendValue(ref, null, value);

			list = null;
		}
	}

	Object finish() {
		if (disk != null)
			return disk.finish(ref, estimate);

		return map ? entries : list;
	}
}