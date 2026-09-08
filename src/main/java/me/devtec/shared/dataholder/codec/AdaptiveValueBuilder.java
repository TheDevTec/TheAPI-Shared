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

/** Spills one collection independently of the number of Config keys. */
final class AdaptiveValueBuilder {
	private final ConfigDocument document;
	private final boolean map;
	private List<Object> list;
	private Map<Object, Object> entries;
	private DiskValueStore disk;
	private long ref, estimate = 48;

	AdaptiveValueBuilder(ConfigDocument d, boolean map) {
		document = d;
		this.map = map;
		if (map)
			entries = new LinkedHashMap<>();
		else
			list = new ArrayList<>();
	}

	void add(Object key, Object value) {
		estimate += 40 + MemoryEstimator.estimate(key) + MemoryEstimator.estimate(value);
		if (disk == null && estimate > Math.max(32768, ConfigMemoryPolicy.budget() / 16)) {
			document.storage.forceDisk();
			disk = ((DiskNodeStore) document.storage.store()).values;
			ref = disk.composite(map);
			if (map) {
				for (Map.Entry<Object, Object> e : entries.entrySet())
					disk.appendValue(ref, e.getKey(), e.getValue());
				entries = null;
			} else {
				for (Object v : list)
					disk.appendValue(ref, null, v);
				list = null;
			}
		}
		if (disk != null)
			disk.appendValue(ref, key, value);
		else if (map)
			entries.put(key, value);
		else
			list.add(value);
	}

	Object finish() {
		return disk != null ? disk.finish(ref, estimate) : map ? entries : list;
	}
}