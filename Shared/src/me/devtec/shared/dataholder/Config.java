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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
	protected boolean isSaving; // LOCK
	protected boolean requireSave;

	// Config updater
	protected int updaterTask;
	protected Runnable updaterWatcher;

	public static Config loadFromInput(InputStream input) {
		return new Config().reload(StreamUtils.fromStream(input));
	}

	public static Config loadFromInput(InputStream input, String outputFile, MergeSetting... settings) {
		return Config.loadFromInput(input, new File(outputFile), settings);
	}

	public static Config loadFromInput(InputStream input, File outputFile, MergeSetting... settings) {
		Config config = new Config(outputFile);
		config.merge(new Config().reload(StreamUtils.fromStream(input)), settings);
		return config;
	}

	public static Config loadFromPlugin(Class<?> mainClass, String pathToFile, File outputFile, MergeSetting... settings) {
		return Config.loadFromInput(mainClass.getClassLoader().getResourceAsStream(pathToFile), outputFile, settings);
	}

	public static Config loadFromPlugin(Class<?> mainClass, String pathToFile, String outputFile, MergeSetting... settings) {
		return Config.loadFromInput(mainClass.getClassLoader().getResourceAsStream(pathToFile), new File(outputFile), settings);
	}

	public static Config loadFromInput(InputStream input, String outputFile) {
		return loadFromInput(input, outputFile, MergeStandards.DEFAULT);
	}

	public static Config loadFromInput(InputStream input, File outputFile) {
		return loadFromInput(input, outputFile, MergeStandards.DEFAULT);
	}

	public static Config loadFromPlugin(Class<?> mainClass, String pathToFile, File outputFile) {
		return loadFromPlugin(mainClass, pathToFile, outputFile, MergeStandards.DEFAULT);
	}

	public static Config loadFromPlugin(Class<?> mainClass, String pathToFile, String outputFile) {
		return loadFromPlugin(mainClass, pathToFile, outputFile, MergeStandards.DEFAULT);
	}

	public static Config loadFromFile(File file) {
		return new Config(file);
	}

	public static Config loadFromFile(String filePath) {
		return new Config(filePath);
	}

	public static Config loadFromString(String input) {
		return new Config().reload(input);
	}

	public Config() {
		loader = new EmptyLoader();
	}

	public Config(DataLoader loaded) {
		loader = loaded;
		requireSave = true;
	}

	public Config(String filePath) {
		this(new File(filePath.charAt(0) == '/' ? filePath.substring(1) : filePath), true);
	}

	public Config(String filePath, boolean load) {
		this(new File(filePath.charAt(0) == '/' ? filePath.substring(1) : filePath), load);
	}

	public Config(File file) {
		this(file, true);
	}

	public Config(File file, boolean load) {
		this.file = file;
		if (load)
			loader = DataLoader.findLoaderFor(file); // get & load
		else
			loader = new EmptyLoader();
	}

	// CLONE
	public Config(Config data) {
		file = data.file;
		loader = data.loader.clone();
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

	public boolean exists(String path) {
		return isKey(path);
	}

	public boolean existsKey(String path) {
		return loader.get().containsKey(path);
	}

	public Config setFile(File file) {
		if (file == this.file)
			return this;
		markModified();
		this.file = file;
		return this;
	}

	public boolean setIfAbsent(String key, Object value) {
		return setIfAbsent(key, value, null);
	}

	public boolean setIfAbsent(String key, Object value, List<String> comments) {
		if (value == null)
			return false;
		if (!existsKey(key)) {
			DataValue val = loader.getOrCreate(key);
			val.value = value;
			val.comments = comments;
			val.modified = true;
			markModified();
			return true;
		}
		return false;
	}

	public Config set(String key, Object value) {
		if (value == null) {
			if (loader.remove(key))
				markModified();
			return this;
		}
		DataValue val = loader.get(key);
		if (val == null) {
			loader.set(key, val = DataValue.of(value));
			val.modified = true;
			markModified();
		} else if (val.value == null && value != null || val.value != null && !val.value.equals(value)) {
			val.value = value;
			val.writtenValue = null;
			val.modified = true;
			markModified();
		}
		return this;
	}

	public Config remove(String key) {
		if (loader.remove(key, true))
			markModified();
		return this;
	}

	public List<String> getComments(String key) {
		DataValue val = loader.get(key);
		if (val != null)
			return val.comments;
		return null;
	}

	public Config setComments(String key, List<String> value) {
		if (value == null || value.isEmpty()) {
			DataValue val = loader.get(key);
			if (val != null && val.comments != null && !val.comments.isEmpty()) {
				val.comments = null;
				val.modified = true;
				markModified();
			}
			return this;
		}
		DataValue val = loader.getOrCreate(key);
		if (val.comments == null || !value.containsAll(val.comments)) {
			val.comments = value;
			val.modified = true;
			markModified();
		}
		return this;
	}

	public String getCommentAfterValue(String key) {
		DataValue val = loader.getOrCreate(key);
		if (val != null)
			return val.commentAfterValue;
		return null;
	}

	public Config setCommentAfterValue(String key, String comment) {
		if (comment == null || comment.isEmpty()) {
			DataValue val = loader.get(key);
			if (val == null || val.commentAfterValue == null)
				return this;
			val.commentAfterValue = null;
			val.modified = true;
			markModified();
			return this;
		}
		DataValue val = loader.getOrCreate(key);
		if (val.commentAfterValue == null || !comment.equals(val.commentAfterValue)) {
			val.commentAfterValue = comment;
			val.modified = true;
			markModified();
		}
		return this;
	}

	public File getFile() {
		return file;
	}

	public Config setHeader(Collection<String> lines) {
		loader.getHeader().clear();
		if (lines != null)
			loader.getHeader().addAll(lines);
		markModified();
		return this;
	}

	public Config setFooter(Collection<String> lines) {
		loader.getFooter().clear();
		if (lines != null)
			loader.getFooter().addAll(lines);
		markModified();
		return this;
	}

	public Collection<String> getHeader() {
		return loader.getHeader();
	}

	public Collection<String> getFooter() {
		return loader.getFooter();
	}

	public Config reload(String input) {
		loader = DataLoader.findLoaderFor(input);
		markModified();
		return this;
	}

	public Config reload() {
		return this.reload(getFile());
	}

	public Config reload(File file) {
		if (file == null)
			return this;
		loader = DataLoader.findLoaderFor(file);
		markNonModified();
		return this;
	}

	public Object get(String key) {
		return get(key, null);
	}

	public Object get(String key, Object defaultValue) {
		DataValue val = loader.get(key);
		if (val == null || val.value == null)
			return defaultValue;
		return val.value;
	}

	public <E> E getAs(String key, Class<? extends E> clazz) {
		return getAs(key, clazz, null);
	}

	public <E> E getAs(String key, Class<? extends E> clazz, E defaultValue) {
		try {
			if (clazz == String.class || clazz == CharSequence.class)
				return clazz.cast(getString(key));
		} catch (Exception e) {
		}
		try {
			return clazz.cast(get(key, defaultValue));
		} catch (Exception e) {
		}
		return defaultValue;
	}

	public String getString(String key) {
		return getString(key, null);
	}

	public String getString(String key, String defaultValue) {
		DataValue val = loader.get(key);
		if (val == null || val.value == null)
			return defaultValue;
		if (val.writtenValue != null)
			return val.writtenValue;
		return val.value instanceof String ? (String) val.value : val.value + "";
	}

	public boolean isJson(String key) {
		DataValue val = loader.get(key);
		if (val == null || val.value == null)
			return false;
		if (val.writtenValue != null && val.writtenValue.length() > 1) {
			char firstChar = val.writtenValue.charAt(0);
			char lastChar = val.writtenValue.charAt(val.writtenValue.length() - 1);
			return firstChar == '[' && lastChar == ']' || firstChar == '{' && lastChar == '}';
		}
		return false;
	}

	public int getInt(String key) {
		return getInt(key, 0);
	}

	public int getInt(String key, int defaultValue) {
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).intValue();
		return ParseUtils.getInt(getString(key));
	}

	public double getDouble(String key) {
		return getDouble(key, 0);
	}

	public double getDouble(String key, double defaultValue) {
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).doubleValue();
		return ParseUtils.getDouble(getString(key));
	}

	public long getLong(String key) {
		return getLong(key, 0);
	}

	public long getLong(String key, long defaultValue) {
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).longValue();
		return ParseUtils.getLong(getString(key));
	}

	public float getFloat(String key) {
		return getFloat(key, 0);
	}

	public float getFloat(String key, float defaultValue) {
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).floatValue();
		return ParseUtils.getFloat(getString(key));
	}

	public byte getByte(String key) {
		return getByte(key, (byte) 0);
	}

	public byte getByte(String key, byte defaultValue) {
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).byteValue();
		return ParseUtils.getByte(getString(key));
	}

	public short getShort(String key) {
		return getShort(key, (short) 0);
	}

	public short getShort(String key, short defaultValue) {
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Number)
			return ((Number) value).shortValue();
		return ParseUtils.getShort(getString(key));
	}

	public boolean getBoolean(String key) {
		return getBoolean(key, false);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		Object value = get(key);
		if (value == null)
			return defaultValue;
		if (value instanceof Boolean)
			return (Boolean) value;
		return ParseUtils.getBoolean(getString(key));
	}

	public Collection<Object> getList(String key) {
		return getList(key, null);
	}

	public Collection<Object> getList(String key, Collection<Object> defaultValue) {
		Object value = get(key);
		if (value == null || !(value instanceof Collection))
			return defaultValue;
		return new ArrayList<>((Collection<?>) value);
	}

	public <E> List<E> getListAs(String key, Class<? extends E> clazz) {
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<E> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			try {
				list.add(o == null ? null : clazz.cast(o));
			} catch (Exception er) {
			}
		return list;
	}

	public List<String> getStringList(String key) {
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

	public List<Boolean> getBooleanList(String key) {
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Boolean> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o != null && (o instanceof Boolean ? (Boolean) o : ParseUtils.getBoolean(o.toString())));
		return list;
	}

	public List<Integer> getIntegerList(String key) {
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Integer> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0 : o instanceof Number ? ((Number) o).intValue() : ParseUtils.getInt(o.toString()));
		return list;
	}

	public List<Double> getDoubleList(String key) {
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Double> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0.0 : o instanceof Number ? ((Number) o).doubleValue() : ParseUtils.getDouble(o.toString()));
		return list;
	}

	public List<Short> getShortList(String key) {
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Short> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0 : o instanceof Number ? ((Number) o).shortValue() : ParseUtils.getShort(o.toString()));
		return list;
	}

	public List<Byte> getByteList(String key) {
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Byte> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0 : o instanceof Number ? ((Number) o).byteValue() : ParseUtils.getByte(o.toString()));
		return list;
	}

	public List<Float> getFloatList(String key) {
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Float> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0 : o instanceof Number ? ((Number) o).floatValue() : ParseUtils.getFloat(o.toString()));
		return list;
	}

	public List<Long> getLongList(String key) {
		Collection<Object> collection = getList(key, Collections.emptyList());
		if (collection.isEmpty())
			return Collections.emptyList();
		List<Long> list = new ArrayList<>(collection.size());
		for (Object o : collection)
			list.add(o == null ? 0 : ParseUtils.getLong(o.toString()));
		return list;
	}

	@SuppressWarnings("unchecked")
	public <K, V> List<Map<K, V>> getMapList(String key) {
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
		return save(type.name());
	}

	public Config save(String name) {
		if (name == null || file == null || isSaving() || !isModified())
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
		ByteBuffer bytes = ByteBuffer.wrap(toByteArray(name, true));
		try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
			channel.write(bytes);
		} catch (Exception e) {
			e.printStackTrace();
		}
		markNonModified();
		isSaving = false;
		return this;
	}

	public void save() {
		if (getDataLoader().name().equals("empty"))
			this.save("yaml");
		else
			save(getDataLoader().name());
	}

	public Set<String> getKeys() {
		return loader.getPrimaryKeys();
	}

	public Set<String> getKeys(boolean subkeys) {
		return subkeys ? loader.getKeys() : getKeys();
	}

	public Set<String> getKeys(String key) {
		return this.getKeys(key, false);
	}

	public Iterator<String> getIteratorKeys(String key) {
		return this.getIteratorKeys(key, false);
	}

	public boolean isKey(String key) {
		for (String section : loader.getKeys())
			if (section.startsWith(key))
				if (section.length() == key.length() || section.charAt(key.length()) == '.')
					return true;
		return false;
	}

	public Set<String> getKeys(String key, boolean subkeys) {
		return getDataLoader().keySet(key, subkeys);
	}

	public Iterator<String> getIteratorKeys(String key, boolean subkeys) {
		return getDataLoader().keySetIterator(key, subkeys);
	}

	@Override
	public String toString() {
		return toString(getDataLoader().name().equals("empty") ? DataType.BYTE.name() : getDataLoader().name(), false);
	}

	public String toString(String type) {
		return toString(type, false);
	}

	public String toString(DataType type) {
		return toString(type.name(), false);
	}

	public String toString(String type, boolean markSaved) {
		if (getDataLoader().name().equalsIgnoreCase(type))
			return getDataLoader().saveAsString(this, markSaved);
		DataLoader loader = DataLoader.findLoaderByName(type);
		if (loader != null)
			return loader.saveAsString(this, markSaved);
		return null;
	}

	public byte[] toByteArray(String type) {
		return toByteArray(type, false);
	}

	public byte[] toByteArray(DataType type) {
		return toByteArray(type.name(), false);
	}

	public byte[] toByteArray(DataType type, boolean markSaved) {
		return toByteArray(type.name(), markSaved);
	}

	public byte[] toByteArray(String type, boolean markSaved) {
		if (getDataLoader().name().equalsIgnoreCase(type))
			return getDataLoader().save(this, markSaved);
		DataLoader loader = DataLoader.findLoaderByName(type);
		if (loader != null)
			return loader.save(this, markSaved);
		return null;
	}

	public Config clear() {
		loader.getPrimaryKeys().clear();
		loader.get().clear();
		loader.getHeader().clear();
		loader.getFooter().clear();
		return this;
	}

	public Config reset() {
		loader.reset();
		return this;
	}

	public boolean merge(Config merge) {
		return merge(merge, MergeStandards.DEFAULT);
	}

	public boolean merge(Config merge, MergeSetting... settings) {
		for (MergeSetting setting : settings)
			if (setting.merge(this, merge))
				markModified();
		return isModified();
	}

	public DataLoader getDataLoader() {
		return loader;
	}

	public boolean isAutoUpdating() {
		return updaterTask != 0;
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
				System.out.println(file.getAbsoluteFile().toPath().getParent());
				Path path = file.getAbsoluteFile().toPath();

				Scheduler.getManager().register(updaterWatcher = () -> {
					try {
						watchService.close();
					} catch (IOException e) {
					}
				});

				path.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
				updaterTask = new Tasker() {
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
									processAutoUpdate();
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
			DataValue val = loader.getOrCreate(key.getKey());
			if (val.modified)
				continue;
			val.value = key.getValue().value;
			val.writtenValue = key.getValue().writtenValue;
			val.comments = key.getValue().comments;
			val.commentAfterValue = key.getValue().commentAfterValue;
		}

		iterator = loader.entrySet().iterator();

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
				loader.remove(section);
	}
}