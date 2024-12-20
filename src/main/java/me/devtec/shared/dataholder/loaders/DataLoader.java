package me.devtec.shared.dataholder.loaders;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Comment;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.annotations.Nullable;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataLoaderConstructor;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.loaders.constructor.LoaderPriority;
import me.devtec.shared.utility.StreamUtils;

public abstract class DataLoader implements Cloneable {

	// Data loaders hierarchy
	public static final Map<LoaderPriority, List<DataLoaderConstructor>> dataLoaders = new HashMap<>();

	// Do not modify!
	private static boolean anyLoaderWhichAllowFiles;

	static {
		for (LoaderPriority priority : LoaderPriority.values()) {
			DataLoader.dataLoaders.put(priority, new ArrayList<>());
		}

		// BUILT-IN LOADERS
		DataLoader.dataLoaders.get(LoaderPriority.LOW).add(new DataLoaderConstructor() {

			@Override
			public DataLoader construct() {
				return new ByteLoader();
			}

			@Override
			public String name() {
				return "byte";
			}
		});
		DataLoader.dataLoaders.get(LoaderPriority.NORMAL).add(new DataLoaderConstructor() {

			@Override
			public DataLoader construct() {
				return new JsonLoader();
			}

			@Override
			public String name() {
				return "json";
			}
		});
		DataLoader.dataLoaders.get(LoaderPriority.NORMAL).add(new DataLoaderConstructor() {

			@Override
			public DataLoader construct() {
				return new TomlLoader();
			}

			@Override
			public String name() {
				return "toml";
			}
		});
		DataLoader.dataLoaders.get(LoaderPriority.NORMAL).add(new DataLoaderConstructor() {

			@Override
			public DataLoader construct() {
				return new PropertiesLoader();
			}

			@Override
			public String name() {
				return "properties";
			}
		});
		DataLoader.dataLoaders.get(LoaderPriority.HIGH).add(new DataLoaderConstructor() {

			@Override
			public DataLoader construct() {
				return new YamlLoader();
			}

			@Override
			public String name() {
				return "yaml";
			}
		});
		DataLoader.dataLoaders.get(LoaderPriority.HIGHEST).add(new DataLoaderConstructor() {

			@Override
			public String name() {
				return "empty";
			}

			@Override
			public DataLoader construct() {
				return new EmptyLoader();
			}
		});
	}

	@Comment(comment = "Registers DataLoaderConstructor under specified priority. Lower priority means that this DataConstructor will be retrieved earlier.")
	public static void register(@Nonnull LoaderPriority priority, @Nonnull DataLoaderConstructor constructor) {
		Checkers.nonNull(priority, "LoaderPriority");
		Checkers.nonNull(constructor, "DataLoaderConstructor");
		DataLoader.dataLoaders.get(priority).add(constructor);
		if (constructor.construct().loadingFromFile()) {
			anyLoaderWhichAllowFiles = true;
		}
	}

	@Comment(comment = "Unregisters DataLoaderConstructor.")
	public void unregister(@Nonnull DataLoaderConstructor constructor) {
		Checkers.nonNull(constructor, "DataLoaderConstructor");
		for (List<DataLoaderConstructor> entry : DataLoader.dataLoaders.values()) {
			if (entry.remove(constructor)) {
				break;
			}
		}
	}

	@Comment(comment = "Checks if it can read the contents of the file directly from File. If not, StreamUtils will be used to read the contents.")
	public abstract boolean loadingFromFile();

	@Comment(comment = "Gets the primary keys.")
	@Nonnull
	public abstract Set<String> getPrimaryKeys();

	@Comment(comment = "Gets the entire stored structure in memory")
	@Nonnull
	public abstract Map<String, DataValue> get();

	@Comment(comment = "Creates a section with the object.")
	public abstract void set(@Nonnull String key, @Nonnull DataValue value);

	@Comment(comment = "Removes a section. If boolean is set to true, it also removes all subsections with this section.")
	public abstract boolean remove(@Nonnull String key, boolean withSubKeys);

	@Comment(comment = "Removes a section.")
	public boolean remove(@Nonnull String key) {
		return remove(key, false);
	}

	@Comment(comment = "Gets the set header lines. This collection can be edited.")
	@Nonnull
	public abstract Collection<String> getHeader();

	@Comment(comment = "Gets the set footer lines. This collection can be edited.")
	@Nonnull
	public abstract Collection<String> getFooter();

	@Comment(comment = "Gets all the keys.")
	@Nonnull
	public abstract Set<String> getKeys();

	@Comment(comment = "Clears all keys, sections and all settings.")
	public abstract void reset();

	@Comment(comment = "Loads the contents of the file.")
	public abstract void load(StringContainer container, @Nonnull List<int[]> input);

	@Comment(comment = "Loads the contents of the file.")
	public abstract void load(@Nullable String input);

	public abstract boolean supportsReadingLines();

	@Comment(comment = "Loads the file. If the class doesn't override this method on its own, StreamUtils will be used to read the contents of the file.")
	public void load(@Nonnull File file) {
		this.load(StreamUtils.fromStream(file));
	}

	@Comment(comment = "Checks if the file was loaded according to class after calling the load method.")
	public abstract boolean isLoaded();

	@Comment(comment = "Gets stored data from a collection on a specific section.")
	@Nullable
	public abstract DataValue get(@Nonnull String key);

	@Comment(comment = "Gets stored data from a collection on a specific section. If there are none, creates a section with empty data.")
	@Nonnull
	public abstract DataValue getOrCreate(@Nonnull String key);

	@Comment(comment = "Saves the entire structure to single String")
	@Nonnull
	public String saveAsString(Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		return saveAsContainer(config, markSaved).toString();
	}

	@Comment(comment = "Saves the entire structure to byte[]")
	@Nonnull
	public byte[] save(Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		return saveAsContainer(config, markSaved).getBytes();
	}

	@Comment(comment = "Saves the entire structure to StringContainer")
	@Nonnull
	public StringContainer saveAsContainer(Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		int size = config.getDataLoader().get().size();
		StringContainer builder = new StringContainer(size * 20);
		Iterator<CharSequence> itr = saveAsIterator(config, markSaved);
		while (itr != null && itr.hasNext()) {
			builder.append(itr.next());
		}
		return builder;
	}

	@Comment(comment = "Saves the entire structure to Iterator<byte[]> which prevent from overload")
	@Nullable
	public Iterator<CharSequence> saveAsIterator(@Nonnull Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		return null;
	}

	@Comment(comment = "Returns status of iterator mode of this DataLoader type")
	public boolean supportsIteratorMode() {
		return false;
	}

	@Comment(comment = "Gets the name of this DataLoader")
	@Nonnull
	public abstract String name();

	@Comment(comment = "Gets the entire structure from the Map and converts it to EntrySet")
	@Nonnull
	public abstract Set<Entry<String, DataValue>> entrySet();

	@Comment(comment = "Gets all subsection names under a specific section. If boolean is set to true, it gets absolutely all subsections with full names.")
	@Nonnull
	public abstract Set<String> keySet(@Nonnull String key, boolean subkeys);

	@Comment(comment = "Creates an Iterator that will retrieve subsections under a specific section. If boolean is set to true, it gets absolutely all subsections with full names.")
	@Nonnull
	public abstract Iterator<String> keySetIterator(@Nonnull String key, boolean subkeys);

	@Comment(comment = "Clones the entire DataLoader")
	@Override
	@Nonnull
	public abstract DataLoader clone();

	@Comment(comment = "Finds DataLoader by its name")
	@Nonnull
	public static DataLoader findLoaderByName(@Nonnull String type) {
		Checkers.nonNull(type, "DataLoader Type Name");
		for (LoaderPriority priority : LoaderPriority.values()) {
			for (DataLoaderConstructor constructor : DataLoader.dataLoaders.get(priority)) {
				if (constructor.isConstructorOf(type)) {
					return constructor.construct();
				}
			}
		}
		return new EmptyLoader();
	}

	@Comment(comment = "It finds the correct DataLoader according to the contents of the file and reads it.")
	@Nonnull
	public static DataLoader findLoaderFor(@Nonnull File input) {
		Checkers.nonNull(input, "Input File");
		if (!anyLoaderWhichAllowFiles) {
			return findLoaderFor(StreamUtils.fromStream(input));
		}
		if (input.length() > 0L) {
			String inputString = null;
			List<int[]> inputLines = null;
			StringContainer container = null;
			loadersLoop: for (LoaderPriority priority : LoaderPriority.values()) {
				for (DataLoaderConstructor constructor : DataLoader.dataLoaders.get(priority)) {
					DataLoader loader = constructor.construct();
					if (inputString == null && loader.loadingFromFile()) {
						loader.load(input);
					} else {
						if (inputString == null) {
							inputString = StreamUtils.fromStream(input);
							if (inputString == null) {
								break loadersLoop;
							}
						}
						if (loader.supportsReadingLines()) {
							if (container == null) {
								container = new StringContainer(inputString, 0, 0);
								inputLines = LoaderReadUtil.readLinesFromContainer(container);
							}
							loader.load(container, inputLines);
						} else {
							loader.load(inputString);
						}
					}
					if (loader.isLoaded()) {
						return loader;
					}
				}
			}
		}
		EmptyLoader empty = new EmptyLoader();
		empty.load(input);
		return empty;
	}

	@Comment(comment = "It finds the correct DataLoader according to the contents and reads it.")
	@Nonnull
	public static DataLoader findLoaderFor(@Nullable String inputString) {
		if (inputString != null && !inputString.isEmpty()) {
			for (LoaderPriority priority : LoaderPriority.values()) {
				for (DataLoaderConstructor constructor : DataLoader.dataLoaders.get(priority)) {
					DataLoader loader = constructor.construct();
					if (loader.supportsReadingLines()) {
						StringContainer container = new StringContainer(inputString, 0, 0);
						List<int[]> inputLines = LoaderReadUtil.readLinesFromContainer(container);
						loader.load(container, inputLines);
					} else {
						loader.load(inputString);
					}
					if (loader.isLoaded()) {
						return loader;
					}
				}
			}
		}
		EmptyLoader empty = new EmptyLoader();
		empty.load(inputString);
		return empty;
	}
}
