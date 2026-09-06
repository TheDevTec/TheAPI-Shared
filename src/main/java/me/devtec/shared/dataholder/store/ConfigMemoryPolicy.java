package me.devtec.shared.dataholder.store;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class ConfigMemoryPolicy {

	public static volatile long maxMemoryBytes = 0;
	public static volatile long globalMemoryBytes = 0;

	public static volatile double spillRatio = .85;
	public static volatile double returnRatio = .45;

	public static volatile int recheckMutations = 4096;
	public static volatile int cooldownMutations = 8192;

	/*
	 * Kolikanásobek source size očekáváme v PackedMemoryStore.
	 *
	 * YAML není 1:1: - node records - segment Strings - ValueRef - metadata - hash
	 * indexes - entry path arena
	 *
	 * 2.25 je rozumný startovní konzervativní odhad.
	 */
	public static volatile double packedSourceMultiplier = 12.0D;

	/*
	 * Nechceme plánovat Packed až přesně k hranici spillRatio. Malá rezerva zabrání
	 * migraci těsně před koncem loadu.
	 */
	public static volatile double preflightSafetyRatio = .80D;

	private static final long TOTAL = totalMemory();

	private ConfigMemoryPolicy() {
	}

	public static long budget() {
		return maxMemoryBytes > 0 ? maxMemoryBytes
				: Math.max(65536L, Math.min((long) (TOTAL * .05D), (long) (Runtime.getRuntime().maxMemory() * .35D)));
	}

	public static long globalBudget() {
		return globalMemoryBytes > 0 ? globalMemoryBytes
				: Math.min((long) (TOTAL * .20D), (long) (Runtime.getRuntime().maxMemory() * .50D));
	}

	public static long usedHeap() {
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	public static long maxPackedSourceBytes() {
		final long memoryLimit = packedLoadLimit();

		final double multiplier = Math.max(1.0D, packedSourceMultiplier);

		return (long) (memoryLimit / multiplier);
	}

	public static boolean heapSafe(long extra) {
		return usedHeap() + extra < Runtime.getRuntime().maxMemory() * .55D;
	}

	/*
	 * Reálná hranice, do které chceme PackedMemoryStore během loadu pustit.
	 */
	public static long packedLoadLimit() {
		final long configured = (long) (budget() * spillRatio);

		final long safe = (long) (configured * preflightSafetyRatio);

		/*
		 * Zároveň respektuj aktuálně dostupný heap.
		 *
		 * Neplánuj nový config tak, aby celkový heap překročil stejných 55 %, které
		 * používá heapSafe().
		 */
		final long heapLimit = (long) (Runtime.getRuntime().maxMemory() * .55D);

		final long available = Math.max(0L, heapLimit - usedHeap());

		return Math.max(65536L, Math.min(safe, available));
	}

	/*
	 * Odhad, kolik RAM source zabere po rozbalení do PackedMemoryStore.
	 */
	public static long estimatePackedBytes(long sourceBytes) {
		if (sourceBytes <= 0L)
			return 0L;

		double estimated = sourceBytes * packedSourceMultiplier;

		if (estimated >= Long.MAX_VALUE)
			return Long.MAX_VALUE;

		return Math.max(sourceBytes, (long) Math.ceil(estimated));
	}

	/*
	 * Hlavní preflight rozhodnutí.
	 */
	public static boolean shouldStartOnDisk(long sourceBytes) {
		if (sourceBytes <= 0L)
			return false;

		final long estimated = estimatePackedBytes(sourceBytes);

		final long limit = packedLoadLimit();

		return estimated >= limit;
	}

	private static long totalMemory() {
		long result = -1L;

		try {
			Class<?> type = Class.forName("com.sun.management.OperatingSystemMXBean");

			result = ((Number) type.getMethod("getTotalPhysicalMemorySize")
					.invoke(ManagementFactory.getOperatingSystemMXBean())).longValue();

		} catch (ReflectiveOperationException ignored) {
		}

		for (String path : new String[] { "/sys/fs/cgroup/memory.max", "/sys/fs/cgroup/memory/memory.limit_in_bytes" })
			try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {

				String value = reader.readLine();

				if (value == null || "max".equals(value))
					continue;

				long limit = Long.parseLong(value);

				if (limit > 0L && limit < Long.MAX_VALUE / 2L)
					result = result <= 0L ? limit : Math.min(result, limit);

			} catch (IOException | NumberFormatException ignored) {
			}

		/*
		 * Pokud fyzickou/cgroup RAM nezjistíme, použij heap jako portable fallback.
		 */
		if (result <= 0L)
			result = Runtime.getRuntime().maxMemory();

		return result;
	}
}