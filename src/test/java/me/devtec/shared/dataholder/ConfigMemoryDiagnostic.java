package me.devtec.shared.dataholder;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;

import me.devtec.shared.dataholder.store.ConfigStore;
import me.devtec.shared.dataholder.store.PackedMemoryStore;

/** Agent-assisted reachable-object sizing; intentionally not an automatic test. */
public final class ConfigMemoryDiagnostic {
	private static Instrumentation instrumentation;

	public static void premain(String options, Instrumentation agent) {
		instrumentation = agent;
	}

	public static void main(String[] args) throws Exception {
		if (instrumentation == null || args.length < 1 || args.length > 2)
			throw new IllegalArgumentException("Use -javaagent:config-memory-diagnostic.jar and one input file argument");
		try (Config config = Config.loadFromFile(args[0])) {
			if (!config.getDataLoader().isLoaded()) throw new IllegalStateException("Config load failed");
			ConfigStore store = config.getDataLoader().document().storage.store();
			System.out.println("backend=" + store.getClass().getSimpleName() + " entries=" + store.size());
			System.out.println("store.estimatedHeap.bytes=" + store.estimatedHeap());
			if (!(store instanceof PackedMemoryStore)) {
				System.out.println("Input migrated to disk; path String representation is not resident.");
				return;
			}
			Object paths = field(store, "entryPaths");
			Object segments = field(store, "segments");
			System.out.println("paths.reachable.bytes=" + size(paths, new IdentityHashMap<Object, Boolean>()));
			IdentityHashMap<Object, Boolean> shared = new IdentityHashMap<>();
			size(segments, shared);
			System.out.println("paths.additionalToSegments.bytes=" + size(paths, shared));
			System.out.println("paths.accountedStrings.bytes=" + field(store, "entryPathStringBytes"));
			System.out.println("paths.accountedPageArrays.bytes=" + field(store, "entryPathArrayBytes"));
			System.out.println("store.reachable.bytes=" + size(store, new IdentityHashMap<Object, Boolean>()));
			if (args.length == 2) {
				ConfigStore baselineStore = (ConfigStore) Class.forName(args[1]).getDeclaredConstructor().newInstance();
				try {
					store.copyTo(baselineStore);
					System.out.println("baseline.store.reachable.bytes=" + size(baselineStore, new IdentityHashMap<Object, Boolean>()));
				} finally { baselineStore.close(); }
			}
			System.out.println("Reachable size includes shared objects; this is not GC retained size or allocation rate.");
			long baseline = (Long) field(store, "entryPathStringBytes");
			String prefix = "__ram_" + java.util.UUID.randomUUID().toString();
			String parent = prefix + ".parent";
			config.set(parent, 1);
			config.set(parent + ".child", 2);
			long withValues = (Long) field(store, "entryPathStringBytes");
			config.getDataLoader().remove(parent, false);
			long parentRemoved = (Long) field(store, "entryPathStringBytes");
			if (parentRemoved >= withValues || config.getInt(parent + ".child") != 2)
				throw new AssertionError("Value-only removal did not release its path or preserve its child");
			config.getDataLoader().remove(prefix, true);
			if ((Long) field(store, "entryPathStringBytes") != baseline)
				throw new AssertionError("Subtree removal retained canonical paths");
			System.out.println("path.releaseChecks=PASS");
			java.util.Set<String> keys = new java.util.LinkedHashSet<>(config.getKeys(true));
			config.getDataLoader().document().storage.forceDisk();
			if (!keys.equals(config.getKeys(true))) throw new AssertionError("Disk migration changed keys");
			config.getDataLoader().document().storage.forceMemory();
			if (!keys.equals(config.getKeys(true))) throw new AssertionError("Memory migration changed keys");
			System.out.println("path.migrationChecks=PASS");
		}
	}

	private static Object field(Object object, String name) throws Exception {
		Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(object);
	}

	private static long size(Object root, IdentityHashMap<Object, Boolean> seen) throws Exception {
		ArrayDeque<Object> pending = new ArrayDeque<>();
		if (root != null) pending.add(root);
		long bytes = 0;
		while (!pending.isEmpty()) {
			Object object = pending.removeLast();
			if (seen.put(object, Boolean.TRUE) != null) continue;
			bytes += instrumentation.getObjectSize(object);
			Class<?> type = object.getClass();
			if (type.isArray()) {
				if (!type.getComponentType().isPrimitive())
					for (int i = 0; i < Array.getLength(object); i++) {
						Object value = Array.get(object, i);
						if (value != null) pending.add(value);
					}
			} else {
				for (; type != null; type = type.getSuperclass())
					for (Field field : type.getDeclaredFields()) {
						if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
						field.setAccessible(true);
						Object value = field.get(object);
						if (value != null) pending.add(value);
					}
			}
		}
		return bytes;
	}
}
