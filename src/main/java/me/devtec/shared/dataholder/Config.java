package me.devtec.shared.dataholder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.*;
import java.util.Map.Entry;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.annotations.Nullable;
import me.devtec.shared.dataholder.loaders.DataLoader;
import me.devtec.shared.dataholder.loaders.EmptyLoader;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.merge.MergeSetting;
import me.devtec.shared.dataholder.merge.MergeStandards;
import me.devtec.shared.json.Json;
import me.devtec.shared.scheduler.Scheduler;
import me.devtec.shared.scheduler.Tasker;
import me.devtec.shared.utility.ParseUtils;
import me.devtec.shared.utility.StreamUtils;

public class Config {
	protected DataLoader loader;
	protected File file;
	protected transient boolean isSaving; // LOCK
	protected boolean requireSave;

	// Config updater
	protected int updaterTask;
	protected Runnable updaterWatcher;
	protected List<Runnable> runnablesOnReload;

	public static Config loadFromInput(@Nonnull InputStream input) {
		Checkers.nonNull(input, "InputStream");
		return new Config().reload(StreamUtils.fromStream(input));
	}

	public static Config loadFromInput(@Nonnull InputStream input, @Nonnull String outputFile,
			@Nonnull MergeSetting... settings) {
		Checkers.nonNull(input, "InputStream");
		Checkers.nonNull(outputFile, "Output File Name");
		Checkers.nonNull(settings, "MergeSetting");
		return Config.loadFromInput(input, new File(outputFile), settings);
	}

	public static Config loadFromInput(@Nonnull InputStream input, @Nonnull File outputFile,
			@Nonnull MergeSetting... settings) {
		Checkers.nonNull(input, "InputStream");
		Checkers.nonNull(outputFile, "Output File");
		Checkers.nonNull(settings, "MergeSetting");
		Config config = new Config(outputFile);
		config.merge(new Config().reload(StreamUtils.fromStream(input)), settings);
		return config;
	}

	public static Config loadFromPlugin(@Nonnull Class<?> mainClass, @Nonnull String pathToFile,
			@Nonnull File outputFile, @Nonnull MergeSetting... settings) {
		Checkers.nonNull(mainClass, "Plugin class");
		Checkers.nonNull(outputFile, "Output File");
		Checkers.nonNull(settings, "MergeSetting");
		return Config.loadFromInput(mainClass.getClassLoader().getResourceAsStream(pathToFile), outputFile, settings);
	}

	public static Config loadFromPlugin(@Nonnull Class<?> mainClass, @Nonnull String pathToFile,
			@Nonnull String outputFile, @Nonnull MergeSetting... settings) {
		Checkers.nonNull(mainClass, "Plugin class");
		Checkers.nonNull(outputFile, "Output File Name");
		Checkers.nonNull(settings, "MergeSetting");
		return Config.loadFromInput(mainClass.getClassLoader().getResourceAsStream(pathToFile), new File(outputFile),
				settings);
	}

	public static Config loadFromInput(@Nonnull InputStream input, @Nonnull String outputFile) {
		Checkers.nonNull(input, "InputStream");
		Checkers.nonNull(outputFile, "Output File Name");
		return loadFromInput(input, outputFile, MergeStandards.DEFAULT);
	}

	public static Config loadFromInput(@Nonnull InputStream input, @Nonnull File outputFile) {
		Checkers.nonNull(input, "InputStream");
		Checkers.nonNull(outputFile, "Output File");
		return loadFromInput(input, outputFile, MergeStandards.DEFAULT);
	}

	public static Config loadFromPlugin(@Nonnull Class<?> mainClass, @Nonnull String pathToFile,
			@Nonnull File outputFile) {
		Checkers.nonNull(mainClass, "Plugin class");
		Checkers.nonNull(pathToFile, "Path To File");
		Checkers.nonNull(outputFile, "Output File");
		return loadFromPlugin(mainClass, pathToFile, outputFile, MergeStandards.DEFAULT);
	}

	public static Config loadFromPlugin(@Nonnull Class<?> mainClass, @Nonnull String pathToFile,
			@Nonnull String outputFile) {
		Checkers.nonNull(mainClass, "Plugin class");
		Checkers.nonNull(pathToFile, "Path To File");
		Checkers.nonNull(outputFile, "Output File Name");
		return loadFromPlugin(mainClass, pathToFile, outputFile, MergeStandards.DEFAULT);
	}

	public static Config loadFromFile(@Nonnull File file) {
		Checkers.nonNull(file, "File");
		return new Config(file);
	}

	public static Config loadFromFile(@Nonnull String filePath) {
		Checkers.nonNull(filePath, "File Path");
		return new Config(filePath);
	}

	public static Config loadFromString(@Nonnull String input) {
		Checkers.nonNull(input, "Contents");
		return new Config().reload(input);
	}

	public Config() {
		loader = new EmptyLoader();
	}

	public Config(@Nonnull DataLoader dataLoader) {
		Checkers.nonNull(dataLoader, "DataLoader");
		loader = dataLoader;
		requireSave = true;
	}

	public Config(@Nonnull String filePath) {
		this(filePath, true);
	}

	public Config(@Nonnull String filePath, boolean load) {
		Checkers.nonNull(filePath, "File Path");
		file = new File(filePath.charAt(0) == '/' ? filePath.substring(1) : filePath);
		if (load)
			loader = DataLoader.findLoaderFor(file); // get & load
		else
			loader = new EmptyLoader();
	}

	public Config(@Nonnull File file) {
		this(file, true);
	}

	public Config(@Nonnull File file, boolean load) {
		Checkers.nonNull(file, "File");
		this.file = file;
		if (load)
			loader = DataLoader.findLoaderFor(file); // get & load
		else
			loader = new EmptyLoader();
	}

	// CLONE
	public Config(@Nonnull Config data) {
		Checkers.nonNull(data, "Config");
		file = data.file;
		loader = data.getDataLoader().clone();
	}

	public boolean isModified() {
		return requireSave;
	}

	public void markModified() {
		requireSave = true;
	}

	public void markNonModified() {
		requireSave = false;
	}

	public boolean exists(@Nonnull String key) {
		return isKey(key);
	}

	public boolean existsKey(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		return getDataLoader().get().containsKey(key);
	}

	public Config setFile(@Nullable File file) {
		if (Objects.equals(file, this.file))
			return this;
		markModified();
		this.file = file;
		if (updaterTask != 0) {
			Scheduler.cancelTask(updaterTask);
			Scheduler.getManager().unregister(updaterWatcher);
			updaterWatcher.run();
			updaterTask = 0;
			updaterWatcher = null;
		}
		return this;
	}

	public Config setFile(@Nullable String filePath) {
		return setFile(filePath == null ? null : new File(filePath));
	}

	public boolean setIfAbsent(@Nonnull String key, @Nonnull Object value) {
		return setIfAbsent(key, value, null);
	}

	public boolean setIfAbsent(@Nonnull String key, @Nonnull Object value, @Nullable List<String> comments) {
		Checkers.nonNull(key, "Key");
		Checkers.nonNull(value, "Value");
		if (!existsKey(key)) {
			DataValue val = getDataLoader().getOrCreate(key);
			val.value = value;
			val.comments = comments;
			val.modified = true;
			markModified();
			return true;
		}
		return false;
	}

	public Config set(@Nonnull String key, @Nullable Object value) {
		Checkers.nonNull(key, "Key");
		if (value == null) {
			if (getDataLoader().remove(key))
				markModified();
			return this;
		}
		DataValue val = getDataLoader().get(key);
		if (val == null) {
			getDataLoader().set(key, val = DataValue.of(value));
			val.modified = true;
			markModified();
		} else if (val.value == null || !val.value.equals(value)) {
			val.value = value;
			val.writtenValue = null;
			val.modified = true;
			markModified();
		}
		return this;
	}

	public Config remove(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		if (getDataLoader().remove(key, true))
			markModified();
		return this;
	}

	@Nullable
	public List<String> getComments(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		DataValue val = getDataLoader().get(key);
		if (val != null)
			return val.comments;
		return null;
	}

	public Config setComments(@Nonnull String key, @Nullable List<String> value) {
		Checkers.nonNull(key, "Key");
		if (value == null || value.isEmpty()) {
			DataValue val = getDataLoader().get(key);
			if (val != null && val.comments != null && !val.comments.isEmpty()) {
				val.comments = null;
				val.modified = true;
				markModified();
			}
			return this;
		}
		DataValue val = getDataLoader().getOrCreate(key);
		if (val.comments == null || !value.containsAll(val.comments)) {
			val.comments = value;
			val.modified = true;
			markModified();
		}
		return this;
	}

	@Nullable
	public String getCommentAfterValue(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		DataValue val = getDataLoader().getOrCreate(key);
		if (val != null)
			return val.commentAfterValue;
		return null;
	}

	public Config setCommentAfterValue(@Nonnull String key, @Nullable String comment) {
		Checkers.nonNull(key, "Key");
		if (comment == null || comment.isEmpty()) {
			DataValue val = getDataLoader().get(key);
			if (val == null || val.commentAfterValue == null)
				return this;
			val.commentAfterValue = null;
			val.modified = true;
			markModified();
			return this;
		}
		DataValue val = getDataLoader().getOrCreate(key);
		if (!comment.equals(val.commentAfterValue)) {
			val.commentAfterValue = comment;
			val.modified = true;
			markModified();
		}
		return this;
	}

	@Nullable
	public File getFile() {
		return file;
	}

	public Config setHeader(@Nullable Collection<String> lines) {
		getDataLoader().getHeader().clear();
		if (lines != null)
			getDataLoader().getHeader().addAll(lines);
		markModified();
		return this;
	}

	public Config setFooter(@Nullable Collection<String> lines) {
		getDataLoader().getFooter().clear();
		if (lines != null)
			getDataLoader().getFooter().addAll(lines);
		markModified();
		return this;
	}

	@Nonnull
	public Collection<String> getHeader() {
		return getDataLoader().getHeader();
	}

	@Nonnull
	public Collection<String> getFooter() {
		return getDataLoader().getFooter();
	}

	public Config reload(@Nullable String input) {
		loader = DataLoader.findLoaderFor(input);
		markModified();
		return this;
	}

	public Config reload() {
		return this.reload(getFile());
	}

	public Config reload(@Nonnull File file) {
		Checkers.nonNull(file, "File");
		clear();
		loader = DataLoader.findLoaderFor(file);
		markNonModified();
		if (runnablesOnReload != null)
			synchronized (runnablesOnReload) {
				for (Runnable runnable : runnablesOnReload)
					runnable.run();
			}
		return this;
	}

	@Nullable
	public Object get(@Nonnull String key) {
		return get(key, null);
	}

	@Nullable
	public Object get(@Nonnull String key, @Nullable Object defaultValue) {
		Checkers.nonNull(key, "Key");
		DataValue val = getDataLoader().get(key);
		if (val == null || val.value == null)
			return defaultValue;
		return val.value;
	}

	@Nullable
	public <E> E getAs(@Nonnull String key, @Nonnull Class<? extends E> clazz) {
		return getAs(key, clazz, null);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	public <E> E getAs(@Nonnull String key, @Nonnull Class<? extends E> clazz, @Nullable E defaultValue) {
		Checkers.nonNull(key, "Key");
		Checkers.nonNull(clazz, "Class");
		try {
			if (clazz == UUID.class)
				return (E) UUID.fromString(getString(key));
			if (clazz == String.class || clazz == CharSequence.class)
				return clazz.cast(getString(key));
		} catch (Exception ignored) {
		}
		try {
			return clazz.cast(get(key, defaultValue));
		} catch (Exception ignored) {
		}
		return defaultValue;
	}

	@Nullable
	public String getString(@Nonnull String key) {
		return getString(key, null);
	}

	@Nullable
	public String getString(@Nonnull String key, @Nullable String defaultValue) {
		Checkers.nonNull(key, "Key");
		DataValue val = getDataLoader().get(key);
		if (val == null || val.value == null)
			return defaultValue;
		if (val.writtenValue != null)
			return val.writtenValue;
		return val.value instanceof String ? (String) val.value : val.value + "";
	}

	public boolean isJson(@Nonnull String key) {
		DataValue val = getDataLoader().get(key);
		if (val == null || val.value == null)
			return false;
		if (val.writtenValue != null && val.writtenValue.length() > 1) {
			char firstChar = val.writtenValue.charAt(0);
			char lastChar = val.writtenValue.charAt(val.writtenValue.length() - 1);
			return firstChar == '[' && lastChar == ']' || firstChar == '{' && lastChar == '}';
		}
		return false;
	}

	public int getInt(@Nonnull String key) {
		return getInt(key, 0);
	}

	public int getInt(@Nonnull String key, int defaultValue) {
		Checkers.nonNull(key, "Key");
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).intValue();
		return ParseUtils.getInt(getString(key));
	}

	public double getDouble(@Nonnull String key) {
		return getDouble(key, 0);
	}

	public double getDouble(@Nonnull String key, double defaultValue) {
		Checkers.nonNull(key, "Key");
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).doubleValue();
		return ParseUtils.getDouble(getString(key));
	}

	public long getLong(@Nonnull String key) {
		return getLong(key, 0);
	}

	public long getLong(@Nonnull String key, long defaultValue) {
		Checkers.nonNull(key, "Key");
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).longValue();
		return ParseUtils.getLong(getString(key));
	}

	public float getFloat(@Nonnull String key) {
		return getFloat(key, 0);
	}

	public float getFloat(@Nonnull String key, float defaultValue) {
		Checkers.nonNull(key, "Key");
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).floatValue();
		return ParseUtils.getFloat(getString(key));
	}

	public byte getByte(@Nonnull String key) {
		return getByte(key, (byte) 0);
	}

	public byte getByte(@Nonnull String key, byte defaultValue) {
		Checkers.nonNull(key, "Key");
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).byteValue();
		return ParseUtils.getByte(getString(key));
	}

	public short getShort(@Nonnull String key) {
		return getShort(key, (short) 0);
	}

	public short getShort(@Nonnull String key, short defaultValue) {
		Checkers.nonNull(key, "Key");
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).shortValue();
		return ParseUtils.getShort(getString(key));
	}

	public boolean getBoolean(@Nonnull String key) {
		return getBoolean(key, false);
	}

	public boolean getBoolean(@Nonnull String key, boolean defaultValue) {
		Checkers.nonNull(key, "Key");
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Boolean)
			return (Boolean) value;
		return ParseUtils.getBoolean(getString(key));
	}

	@Nullable
	public Collection<Object> getList(@Nonnull String key) {
		return getList(key, null);
	}

	@Nullable
	public Collection<Object> getList(@Nonnull String key, @Nullable Collection<Object> defaultValue) {
		Checkers.nonNull(key, "Key");
		Object value = get(key);
		if (!(value instanceof Collection))
			return defaultValue;
		return new ArrayList<>((Collection<?>) value);
	}

	@SuppressWarnings("unchecked")
	@Nonnull
	public <E> List<E> getListAs(@Nonnull String key, @Nonnull Class<? extends E> clazz) {
		Checkers.nonNull(key, "Key");
		Checkers.nonNull(clazz, "Class");
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<E> list = new ArrayList<>(collection.size());
		if (clazz == UUID.class) {
			for (Object o : collection)
				try {
					list.add(o == null ? null : o instanceof UUID ? (E) o : (E) UUID.fromString(o.toString()));
				} catch (Exception ignored) {
				}
			return list;
		}
		for (Object o : collection)
			try {
				list.add(o == null ? null : clazz.cast(o));
			} catch (Exception ignored) {
			}
		return list;
	}

	@Nonnull
	public List<String> getStringList(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<String> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			if (o != null)
				list.add("" + o);
			else
				list.add(null);
		return list;
	}

	@Nonnull
	public List<Boolean> getBooleanList(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Boolean> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o != null && (o instanceof Boolean ? (Boolean) o : ParseUtils.getBoolean(o.toString())));
		return list;
	}

	@Nonnull
	public List<Integer> getIntegerList(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Integer> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0 : o instanceof Number ? ((Number) o).intValue() : ParseUtils.getInt(o.toString()));
		return list;
	}

	@Nonnull
	public List<Double> getDoubleList(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Double> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0.0
					: o instanceof Number ? ((Number) o).doubleValue() : ParseUtils.getDouble(o.toString()));
		return list;
	}

	@Nonnull
	public List<Short> getShortList(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Short> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0
					: o instanceof Number ? ((Number) o).shortValue() : ParseUtils.getShort(o.toString()));
		return list;
	}

	@Nonnull
	public List<Byte> getByteList(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Byte> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0 : o instanceof Number ? ((Number) o).byteValue() : ParseUtils.getByte(o.toString()));
		return list;
	}

	@Nonnull
	public List<Float> getFloatList(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Float> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0
					: o instanceof Number ? ((Number) o).floatValue() : ParseUtils.getFloat(o.toString()));
		return list;
	}

	@Nonnull
	public List<Long> getLongList(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Long> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0 : ParseUtils.getLong(o.toString()));
		return list;
	}

	@Nonnull
	@SuppressWarnings("unchecked")
	public <K, V> List<Map<K, V>> getMapList(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Map<K, V>> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			if (o == null)
				list.add(null);
			else if (o instanceof Map)
				list.add((Map<K, V>) o);
			else {
				Object re = Json.reader().read(o.toString());
				list.add(re instanceof Map ? (Map<K, V>) re : null);
			}
		return list;
	}

	public synchronized boolean isSaving() {
		return isSaving;
	}

	public Config save(DataType type) {
		Checkers.nonNull(type, "DataType");
		return save(type.name());
	}

	public Config save(@Nonnull String dataTypeName) {
		Checkers.nonNull(dataTypeName, "DataType Name");
		if (file == null || isSaving() || !isModified())
			return this;
		isSaving = true;
		if (!file.exists()) {
			File folder = file.getParentFile();
			if (folder != null)
				folder.mkdirs();
			try {
				file.createNewFile();
			} catch (Exception e) {
				isSaving = false;
				e.printStackTrace();
				return this;
			}
		}
		DataLoader writer;
		if (getDataLoader().name().equalsIgnoreCase(dataTypeName))
			writer = getDataLoader();
		else
			writer = DataLoader.findLoaderByName(dataTypeName);
		if (writer != null)
			if (writer.supportsIteratorMode()) {
				Iterator<CharSequence> iterator = writer.saveAsIterator(this, true);
				try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING)) {
					while (iterator.hasNext()) {
						CharSequence next = iterator.next();
						channel.write(ByteBuffer.wrap(next instanceof StringContainer
								? ((StringContainer) next).getBytes()
								: next instanceof String ? ((String) next).getBytes() : next.toString().getBytes()));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else
				try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING)) {
					channel.write(ByteBuffer.wrap(writer.save(this, true)));
				} catch (Exception e) {
					e.printStackTrace();
				}
		markNonModified();
		isSaving = false;
		return this;
	}

	public void save() {
		if ("empty".equals(getDataLoader().name()))
			this.save("yaml");
		else
			save(getDataLoader().name());
	}

	@Nonnull
	public Set<String> getKeys() {
		return getDataLoader().getPrimaryKeys();
	}

	@Nonnull
	public Set<String> getKeys(boolean subkeys) {
		return subkeys ? getDataLoader().getKeys() : getKeys();
	}

	@Nonnull
	public Set<String> getKeys(@Nonnull String key) {
		return this.getKeys(key, false);
	}

	@Nonnull
	public Iterator<String> getIteratorKeys(@Nonnull String key) {
		return this.getIteratorKeys(key, false);
	}

	public boolean isKey(@Nonnull String key) {
		Checkers.nonNull(key, "Key");
		for (String section : getDataLoader().getKeys())
			if (section.startsWith(key))
				if (section.length() == key.length() || section.charAt(key.length()) == '.')
					return true;
		return false;
	}

	@Nonnull
	public Set<String> getKeys(@Nonnull String key, boolean subkeys) {
		Checkers.nonNull(key, "Key");
		return getDataLoader().keySet(key, subkeys);
	}

	@Nonnull
	public Iterator<String> getIteratorKeys(@Nonnull String key, boolean subkeys) {
		Checkers.nonNull(key, "Key");
		return getDataLoader().keySetIterator(key, subkeys);
	}

	@Nonnull
	@Override
	public String toString() {
		return toString("empty".equals(getDataLoader().name()) ? DataType.BYTE.name() : getDataLoader().name(), false);
	}

	@Nonnull
	public String toString(String dataTypeName) {
		Checkers.nonNull(dataTypeName, "DataType Name");
		return toString(dataTypeName, false);
	}

	@Nonnull
	public String toString(DataType type) {
		Checkers.nonNull(type, "DataType");
		return toString(type.name(), false);
	}

	@Nonnull
	public String toString(String dataTypeName, boolean markSaved) {
		Checkers.nonNull(dataTypeName, "DataType Name");
		if (getDataLoader().name().equalsIgnoreCase(dataTypeName))
			return getDataLoader().saveAsString(this, markSaved);
		DataLoader loader = DataLoader.findLoaderByName(dataTypeName);
		if (loader != null)
			return loader.saveAsString(this, markSaved);
		return null;
	}

	@Nonnull
	public byte[] toByteArray(String dataTypeName) {
		Checkers.nonNull(dataTypeName, "DataType Name");
		return toByteArray(dataTypeName, false);
	}

	@Nonnull
	public byte[] toByteArray(DataType type) {
		Checkers.nonNull(type, "DataType");
		return toByteArray(type.name(), false);
	}

	@Nonnull
	public byte[] toByteArray(DataType type, boolean markSaved) {
		Checkers.nonNull(type, "DataType");
		return toByteArray(type.name(), markSaved);
	}

	@Nonnull
	public byte[] toByteArray(String dataTypeName, boolean markSaved) {
		Checkers.nonNull(dataTypeName, "DataType Name");
		if (getDataLoader().name().equalsIgnoreCase(dataTypeName))
			return getDataLoader().save(this, markSaved);
		DataLoader loader = DataLoader.findLoaderByName(dataTypeName);
		if (loader != null)
			return loader.save(this, markSaved);
		return null;
	}

	public Config clear() {
		boolean shouldSave = !getDataLoader().get().isEmpty() || !getDataLoader().getHeader().isEmpty()
				|| !getDataLoader().getFooter().isEmpty();
		getDataLoader().reset();
		requireSave = shouldSave;
		return this;
	}

	public Config reset() {
		file = null;
		requireSave = false;
		getDataLoader().reset();
		if (updaterTask != 0) {
			Scheduler.cancelTask(updaterTask);
			Scheduler.getManager().unregister(updaterWatcher);
			updaterWatcher.run();
			updaterTask = 0;
			updaterWatcher = null;
		}
		runnablesOnReload = null;
		return this;
	}

	public boolean merge(Config merge) {
		Checkers.nonNull(merge, "Config");
		return merge(merge, MergeStandards.DEFAULT);
	}

	public boolean merge(Config merge, MergeSetting... settings) {
		Checkers.nonNull(merge, "Config");
		Checkers.nonNull(settings, "MergeSetting");
		for (MergeSetting setting : settings)
			if (setting.merge(this, merge))
				markModified();
		return isModified();
	}

	@Nonnull
	public DataLoader getDataLoader() {
		return loader;
	}

	public boolean isAutoUpdating() {
		return updaterTask != 0;
	}

	public Config addRunnableOnReload(Runnable runnable) {
		synchronized (runnablesOnReload) {
			if (runnablesOnReload == null)
				runnablesOnReload = new ArrayList<>();
			runnablesOnReload.add(runnable);
		}
		return this;
	}

	public Config removeRunnableOnReload(Runnable runnable) {
		synchronized (runnablesOnReload) {
			if (runnablesOnReload != null)
				runnablesOnReload.remove(runnable);
		}
		return this;
	}

	public Config setAutoUpdating(long checkEvery) {
		if (file == null)
			return this;
		if (checkEvery <= 0) {
			if (updaterTask != 0) {
				Scheduler.cancelTask(updaterTask);
				Scheduler.getManager().unregister(updaterWatcher);
				updaterWatcher.run();
				updaterTask = 0;
				updaterWatcher = null;
			}
		} else
			try {
				if (!file.exists())
					return this;
				WatchService watchService = FileSystems.getDefault().newWatchService();
				Path path = file.getAbsoluteFile().toPath();

				Scheduler.getManager().register(updaterWatcher = () -> {
					try {
						watchService.close();
					} catch (IOException ignored) {
					}
				});

				path.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
				updaterTask = new Tasker() {
					long lastUpdateAt;

					@Override
					public void run() {
						WatchKey wkey;
						try {
							wkey = watchService.poll();
						} catch (Exception e) {
							return;
						}
						while (wkey != null) {
							for (WatchEvent<?> event : wkey.pollEvents())
								if (((Path) event.context()).toAbsolutePath().equals(path)) {
									if (lastUpdateAt - System.currentTimeMillis() / 1000 <= 0) {
										lastUpdateAt = System.currentTimeMillis() / 1000 + 1;
										processAutoUpdate();
									}
									wkey.reset();
									return;
								}
							wkey.reset();
							try {
								wkey = watchService.poll();
							} catch (Exception e) {
								return;
							}
						}
					}
				}.runRepeating(checkEvery, checkEvery);
			} catch (IOException e) {
				e.printStackTrace();
			}
		return this;
	}

	public void processAutoUpdate() {
		DataLoader read = DataLoader.findLoaderFor(file);
		Iterator<Entry<String, DataValue>> iterator = read.entrySet().iterator();

		// Add added sections & modified values
		while (iterator.hasNext()) {
			Entry<String, DataValue> key = iterator.next();
			DataValue val = getDataLoader().getOrCreate(key.getKey());
			if (val.modified)
				continue;
			val.value = key.getValue().value;
			val.writtenValue = key.getValue().writtenValue;
			val.comments = key.getValue().comments;
			val.commentAfterValue = key.getValue().commentAfterValue;
		}

		iterator = getDataLoader().entrySet().iterator();

		// Remove removed sections

		Set<String> sectionsToRemove = null;

		while (iterator.hasNext()) {
			Entry<String, DataValue> key = iterator.next();
			if (key.getValue().modified)
				continue;
			if (read.get(key.getKey()) == null) {
				if (sectionsToRemove == null)
					sectionsToRemove = new HashSet<>();
				sectionsToRemove.add(key.getKey());
			}
		}

		if (sectionsToRemove != null)
			for (String section : sectionsToRemove)
				getDataLoader().remove(section);
		if (runnablesOnReload != null)
			synchronized (runnablesOnReload) {
				for (Runnable runnable : runnablesOnReload)
					runnable.run();
			}
	}
}