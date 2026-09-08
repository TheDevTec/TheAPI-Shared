package me.devtec.shared.dataholder.store;

/**
 * Transactional, bidirectional storage ownership. Config remains externally
 * synchronized.
 */
public final class AdaptiveConfigStore implements AutoCloseable {
	public enum State {
		MEMORY, MIGRATING_TO_DISK, DISK, MIGRATING_TO_MEMORY, CLOSED
	}

	private ConfigStore store = new PackedMemoryStore();
	private State state = State.MEMORY;
	private long mutations, lastMigration, generation;
	private static long spillLimit, returnLimit;
	private boolean preflightDisk;
	private boolean bulkLoad;
	private long bulkSourceBytes;
	private int recheckCountdown = Math.max(1, ConfigMemoryPolicy.recheckMutations);

	static {
		long budget = ConfigMemoryPolicy.budget();

		spillLimit = (long) (budget * ConfigMemoryPolicy.spillRatio);
		returnLimit = (long) (budget * ConfigMemoryPolicy.returnRatio);
	}

	public AdaptiveConfigStore() {
		ConfigMemoryCoordinator.register(this);
	}

	public ConfigStore store() {
		if (state == State.CLOSED)
			throw new IllegalStateException("Closed Config store");
		return store;
	}

	public State state() {
		return state;
	}

	public long generation() {
		return generation;
	}

	long residentBytes() {
		return state == State.CLOSED ? 0 : store.disk() ? store.cacheBytes() : store.estimatedHeap();
	}

	public void preflight(long sourceBytes) {
		preflightDisk = ConfigMemoryPolicy.shouldStartOnDisk(sourceBytes);
		if (preflightDisk)
			forceDisk();
	}

	public void beforeMutation(long estimate) {
		if (!store.disk() && store.estimatedHeap() + estimate > spillLimit)
			forceDisk();

		++mutations;

		if (--recheckCountdown <= 0) {
			recheckCountdown = Math.max(1, ConfigMemoryPolicy.recheckMutations);
			optimize(false);
		}
	}

	public void optimizeStorage() {
		optimize(true);
	}

	public void beginBulkLoad(long sourceBytes) {
		bulkLoad = true;
		bulkSourceBytes = sourceBytes;

		ConfigStore current = store();

		if (current instanceof DiskNodeStore)
			((DiskNodeStore) current).beginBulkLoad(sourceBytes);
	}

	public void endBulkLoad() {
		try {
			ConfigStore current = store();

			if (current instanceof DiskNodeStore)
				((DiskNodeStore) current).endBulkLoad();
		} finally {
			bulkLoad = false;
			bulkSourceBytes = 0L;
		}
	}

	public void compactStorage() {
		if (store().disk())
			migrate(true);
	}

	private void optimize(boolean explicit) {
		if (!store.disk()) {
			if (store.estimatedHeap() > spillLimit || !ConfigMemoryPolicy.heapSafe(0)
					|| ConfigMemoryCoordinator.pressure())
				forceDisk();

			return;
		}

		if (!explicit && preflightDisk)
			return;

		long estimated = store.estimatedHeap();

		if ((explicit || mutations - lastMigration >= ConfigMemoryPolicy.cooldownMutations) && estimated < returnLimit
				&& ConfigMemoryPolicy.heapSafe(estimated) && !ConfigMemoryCoordinator.pressure())
			migrate(false);
	}

	public void forceDisk() {
		if (!store().disk())
			migrate(true);
	}

	public void forceMemory() {
		if (store().disk()) {
			long estimated = store.estimatedHeap();

			if (estimated > returnLimit || !ConfigMemoryPolicy.heapSafe(estimated))
				throw new IllegalStateException("Config cannot safely fit in memory");

			migrate(false);

			if (store.disk())
				throw new IllegalStateException("Config paths and values cannot safely fit in memory");
		}
	}

	private void migrate(boolean disk) {
		ConfigStore old = store, candidate = null;
		State previous = state;
		state = disk ? State.MIGRATING_TO_DISK : State.MIGRATING_TO_MEMORY;
		try {
			candidate = disk ? new DiskNodeStore() : new PackedMemoryStore();

			/*
			 * Pokud parser právě běží, nový disk backend musí vstoupit do bulk režimu ještě
			 * PŘED copyTo().
			 */
			if (disk && bulkLoad && candidate instanceof DiskNodeStore)
				((DiskNodeStore) candidate).beginBulkLoad(bulkSourceBytes);

			if (!disk) {
				long limit = spillLimit;
				if (!old.copyLiveTo(candidate, limit)) {
					candidate.close();
					state = previous;
					lastMigration = mutations;
					return;
				}
			} else if (old.disk())
				old.copyLiveTo(candidate);
			else
				old.copyTo(candidate);

			/*
			 * Temporary backing při aktivním bulk loadu nemusíme okamžitě fyzicky
			 * flushnout.
			 *
			 * Page cache je authoritative a parsing pokračuje.
			 */
			if (!disk || !bulkLoad)
				candidate.flush();
			store = candidate;
			state = disk ? State.DISK : State.MEMORY;

			if (!disk)
				preflightDisk = false;

			generation++;
			lastMigration = mutations;
			old.close();
		} catch (RuntimeException e) {
			if (candidate != null && candidate != store)
				candidate.close();
			state = previous;
			throw e;
		}
	}

	@Override
	public void close() {
		if (state != State.CLOSED) {
			store.close();
			state = State.CLOSED;
			generation++;
		}
	}
}
