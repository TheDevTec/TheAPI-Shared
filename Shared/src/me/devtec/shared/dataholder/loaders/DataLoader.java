package me.devtec.shared.dataholder.loaders;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.devtec.shared.dataholder.loaders.constructor.DataLoaderConstructor;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.loaders.constructor.LoaderPriority;
import me.devtec.shared.utility.StreamUtils;

public abstract class DataLoader implements Cloneable {

	// Data loaders hierarchy
	public static Map<LoaderPriority, Set<DataLoaderConstructor>> dataLoaders = new ConcurrentHashMap<>();
	static {
		for (LoaderPriority priority : LoaderPriority.values())
			DataLoader.dataLoaders.put(priority, new HashSet<>());

		// BUILT-IN LOADERS
		DataLoader.dataLoaders.get(LoaderPriority.LOW).add(new DataLoaderConstructor() {
			DataLoader notUsed;

			@Override
			public DataLoader construct() {
				if (notUsed == null || notUsed.isLoaded())
					notUsed = new ByteLoader();
				return notUsed;
			}
		});
		DataLoader.dataLoaders.get(LoaderPriority.NORMAL).add(new DataLoaderConstructor() {
			DataLoader notUsed;

			@Override
			public DataLoader construct() {
				if (notUsed == null || notUsed.isLoaded())
					notUsed = new JsonLoader();
				return notUsed;
			}
		});
		DataLoader.dataLoaders.get(LoaderPriority.NORMAL).add(new DataLoaderConstructor() {
			DataLoader notUsed;

			@Override
			public DataLoader construct() {
				if (notUsed == null || notUsed.isLoaded())
					notUsed = new PropertiesLoader();
				return notUsed;
			}
		});
		DataLoader.dataLoaders.get(LoaderPriority.HIGH).add(new DataLoaderConstructor() {
			DataLoader notUsed;

			@Override
			public DataLoader construct() {
				if (notUsed == null || notUsed.isLoaded())
					notUsed = new YamlLoader();
				return notUsed;
			}
		});
		DataLoader.dataLoaders.get(LoaderPriority.HIGHEST).add(EmptyLoader::new);
	}

	public static void register(LoaderPriority priority, DataLoaderConstructor constructor) {
		DataLoader.dataLoaders.get(priority).add(constructor);
	}

	public void unregister(DataLoaderConstructor constructor) {
		for (Entry<LoaderPriority, Set<DataLoaderConstructor>> entry : DataLoader.dataLoaders.entrySet())
			if (entry.getValue().remove(constructor))
				break;
	}

	// Does DataLoader have own loader from file?

	public abstract boolean loadingFromFile();

	public abstract Set<String> getPrimaryKeys();

	public abstract Map<String, DataValue> get();

	public abstract void set(String key, DataValue value);

	public abstract boolean remove(String key);

	public abstract Collection<String> getHeader();

	public abstract Collection<String> getFooter();

	public abstract Set<String> getKeys();

	public abstract void reset();

	public abstract void load(String input);

	public abstract boolean isLoaded();

	public void load(File file) {
		this.load(StreamUtils.fromStream(file));
	}

	public static DataLoader findLoaderFor(File input) {
		String inputString = null;
		loadersLoop: for (LoaderPriority priority : LoaderPriority.values())
			for (DataLoaderConstructor constructor : DataLoader.dataLoaders.get(priority)) {
				DataLoader loader = constructor.construct();
				if (inputString == null && loader.loadingFromFile())
					loader.load(input);
				else {
					if (inputString == null) {
						inputString = StreamUtils.fromStream(input);
						if (inputString == null)
							break loadersLoop;
					}
					loader.load(inputString);
				}
				if (loader.isLoaded())
					return loader;
			}
		EmptyLoader empty = new EmptyLoader();
		empty.load(input);
		return empty;
	}

	public static DataLoader findLoaderFor(String inputString) {
		for (LoaderPriority priority : LoaderPriority.values())
			for (DataLoaderConstructor constructor : DataLoader.dataLoaders.get(priority)) {
				DataLoader loader = constructor.construct();
				loader.load(inputString);
				if (loader.isLoaded())
					return loader;
			}
		return null;
	}

	@Override
	public abstract DataLoader clone();
}
