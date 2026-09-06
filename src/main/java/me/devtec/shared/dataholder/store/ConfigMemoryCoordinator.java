package me.devtec.shared.dataholder.store;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Weak ownership: signals pressure to the active store, never migrates another
 * caller's data.
 */
public final class ConfigMemoryCoordinator {
	private static final Map<AdaptiveConfigStore, Boolean> STORES = new WeakHashMap<>();

	private ConfigMemoryCoordinator() {
	}

	static synchronized void register(AdaptiveConfigStore store) {
		STORES.put(store, Boolean.TRUE);
	}

	public static synchronized long retainedBytes() {
		long n = 0;
		for (AdaptiveConfigStore s : STORES.keySet())
			n += s.residentBytes();
		return n;
	}

	static boolean pressure() {
		return retainedBytes() > ConfigMemoryPolicy.globalBudget();
	}
}