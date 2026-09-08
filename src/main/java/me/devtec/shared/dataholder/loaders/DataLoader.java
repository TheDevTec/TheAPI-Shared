package me.devtec.shared.dataholder.loaders;

import java.io.*;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.codec.ConfigBufferedWriter;
import me.devtec.shared.dataholder.codec.ConfigWriter;
import me.devtec.shared.dataholder.codec.FormatRegistry;
import me.devtec.shared.dataholder.loaders.constructor.DataLoaderConstructor;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.loaders.constructor.LoaderPriority;
import me.devtec.shared.dataholder.store.ConfigMapView;
import me.devtec.shared.dataholder.store.ConfigStore;
import me.devtec.shared.dataholder.store.NodeMetadata;

/**
 * Compatibility facade. Format codecs and the canonical node store have
 * independent lifecycles.
 */
public class DataLoader implements Cloneable {
	protected ConfigDocument document;
	protected boolean loaded;
	private final String format;
	private ConfigMapView mapView;
	private ConfigDocument mapDocument;
	public static volatile Throwable lastLoadError;
	private static final List<DataLoaderConstructor> CUSTOM = new java.util.concurrent.CopyOnWriteArrayList<>();

	public DataLoader() {
		this("empty");
	}

	protected DataLoader(String format) {
		this.format = format;
		document = new ConfigDocument();
		document.format = format;
	}

	private DataLoader(ConfigDocument d) {
		format = d.format;
		document = d;
		loaded = true;
	}

	public ConfigDocument document() {
		return document;
	}

	public boolean loadingFromFile() {
		return false;
	}

	public Set<String> getPrimaryKeys() {
		return document.storage.store().keys(null, false, false);
	}

	public ConfigMapView get() {
		if (mapDocument != document) {
			mapDocument = document;
			mapView = new ConfigMapView(document);
		}
		return mapView;
	}

	public boolean hasValue(String key) {
		return document.storage.store().hasValue(key);
	}

	public Object getValue(String key) {
		final ConfigStore store = document.storage.store();
		return store.externalValue(document, key, store.getValue(key));
	}

	public String getStringValue(String key) {
		return document.storage.store().getStringValue(key);
	}

	public String getWrittenValue(String key) {
		return document.storage.store().getWrittenValue(key);
	}

	public List<String> getComments(String key) {
		return document.storage.store().getComments(key);
	}

	public String getComment(String key) {
		return document.storage.store().getComment(key);
	}

	public boolean setValue(String key, Object value) {
		if (value == null)
			return remove(key);

		final ConfigStore store = document.storage.store();
		final int node = store.resolve(key, true);

		final NodeMetadata metadata;

		if (store.hasValue(node)) {
			// Memory backend může levně porovnat hodnotu.
			// Disk backend kvůli tomu nedekóduje potenciálně obří starou hodnotu.
			if (!store.disk() && java.util.Objects.equals(store.getValue(node), value))
				return false;

			metadata = new NodeMetadata(null, store.getComment(node), store.getComments(node));
		} else
			metadata = new NodeMetadata();

		document.put(node, value, metadata, true);
		return true;
	}

	public boolean setIfAbsent(String key, Object value, List<String> comments) {
		final ConfigStore store = document.storage.store();
		final int node = store.resolve(key, true);

		if (store.hasValue(node))
			return false;

		document.put(node, value, new NodeMetadata(null, null, comments), true);

		return true;
	}

	public boolean setComments(String key, List<String> comments) {
		final ConfigStore store = document.storage.store();

		if (comments == null || comments.isEmpty()) {
			final int node = store.resolve(key, false);

			if (node == 0)
				return false;

			final List<String> previous = store.getComments(node);

			if (previous == null || previous.isEmpty())
				return false;

			final NodeMetadata metadata = new NodeMetadata(store.getWrittenValue(node), store.getComment(node), null);

			document.put(node, store.value(node), metadata, true);
			return true;
		}

		final int node = store.resolve(key, true);
		final List<String> previous = store.getComments(node);

		if (comments.equals(previous))
			return false;

		final NodeMetadata metadata = new NodeMetadata(store.getWrittenValue(node), store.getComment(node),
				new ArrayList<>(comments));

		document.put(node, store.value(node), metadata, true);
		return true;
	}

	public boolean setComment(String key, String comment) {
		final ConfigStore store = document.storage.store();

		if (comment == null || comment.isEmpty()) {
			final int node = store.resolve(key, false);

			if (node == 0 || store.getComment(node) == null)
				return false;

			final NodeMetadata metadata = new NodeMetadata(store.getWrittenValue(node), null, store.getComments(node));

			document.put(node, store.value(node), metadata, true);
			return true;
		}

		final int node = store.resolve(key, true);
		final String previous = store.getComment(node);

		if (comment.equals(previous))
			return false;

		final NodeMetadata metadata = new NodeMetadata(store.getWrittenValue(node), comment, store.getComments(node));

		document.put(node, store.value(node), metadata, true);
		return true;
	}

	public void set(String key, DataValue value) {
		final ConfigStore store = document.storage.store();
		final int node = store.resolve(key, true);

		document.put(node, value.value, new NodeMetadata(value.writtenValue, value.commentAfterValue, value.comments),
				value.modified);
	}

	public DataValue get(String key) {
		return get().get(key);
	}

	public DataValue getOrCreate(String key) {
		DataValue v = get(key);
		if (v == null) {
			v = DataValue.empty();
			set(key, v);
		}
		return v;
	}

	public boolean remove(String key) {
		return remove(key, false);
	}

	public boolean remove(String key, boolean subtree) {
		final ConfigStore store = document.storage.store();

		final boolean removed = store.remove(key, subtree);

		if (removed)
			document.storage.beforeMutation(0);

		return removed;
	}

	public Collection<String> getHeader() {
		return document.header;
	}

	public Collection<String> getFooter() {
		return document.footer;
	}

	public Set<String> getKeys() {
		return document.storage.store().keys(null, false, true);
	}

	public Set<Map.Entry<String, DataValue>> entrySet() {
		return get().entrySet();
	}

	public Set<String> keySet(String key, boolean subkeys) {
		return document.storage.store().keys(key, subkeys, false);
	}

	public Iterator<String> keySetIterator(String key, boolean subkeys) {
		return keySet(key, subkeys).iterator();
	}

	public boolean hasKeyOrSection(String key) {
		return document.storage.store().hasKeyOrSection(key);
	}

	public boolean isLoaded() {
		return loaded;
	}

	public String name() {
		return document.format;
	}

	public void reset() {
		document.close();
		document = new ConfigDocument();
		document.format = format;
		loaded = false;
	}

	public void load(StringContainer container, List<int[]> lines) {
		load(container.toString());
	}

	public void load(String input) {
		try {
			ConfigDocument next = parse(new StringReader(input == null ? "" : input), format,
					input == null ? 0 : input.length() * 2L);
			document.close();
			document = next;
			loaded = true;
		} catch (IOException | RuntimeException e) {
			lastLoadError = e;
			loaded = false;
		}
	}

	public void load(File file) {
		try {
			ConfigDocument next = read(file, format);
			document.close();
			document = next;
			loaded = true;
		} catch (IOException | RuntimeException e) {
			lastLoadError = e;
			e.printStackTrace();
			loaded = false;
		}
	}

	public boolean supportsReadingLines() {
		return false;
	}

	public boolean supportsLargeFiles() {
		return true;
	}

	public boolean canLoadLargeFile(File file) {
		return true;
	}

	public void loadLargeFile(File file) {
		load(file);
	}

	public boolean supportsIteratorMode() {
		return false;
	}

	public boolean supportsStreamingSave() {
		return true;
	}

	public void markSavedValues() {
		document.storage.store().markSaved();
	}

	@Override
	public DataLoader clone() {
		return new DataLoader(document.copy());
	}

	public void saveTo(Config config, OutputStream output, boolean markSaved) throws IOException {
		saveTo(config, format, output, markSaved);
	}

	public static boolean supportsFormat(String name) {
		if (name == null)
			return false;

		switch (name.toLowerCase(Locale.ROOT)) {
		case "yaml":
		case "json":
		case "toml":
		case "properties":
		case "byte":
		case "empty":
			return true;
		default:
			return false;
		}
	}

	public static void saveTo(Config config, String format, OutputStream output, boolean markSaved) throws IOException {
		format = format.toLowerCase(Locale.ROOT);
		ConfigDocument d = config.getDataLoader().document;
		if ("byte".equals(format))
			FormatRegistry.writeByte(d, output);
		else {
			Writer writer = new ConfigBufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8.newEncoder()
					.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
			ConfigWriter.INSTANCE.write(d, format, writer);
			writer.flush();
		}
		if (markSaved)
			d.storage.store().markSaved();
	}

	public byte[] save(Config config, boolean markSaved) {
		return save(config, format, markSaved);
	}

	public static byte[] save(Config config, String format, boolean markSaved) {
		final long limit = Math.min(Integer.MAX_VALUE - 8, Runtime.getRuntime().maxMemory() / 8);
		if (config.getDataLoader().document.storage.store().estimatedHeap() > limit * 4)
			throw new IllegalStateException("Config output is too large for a byte array; use save() or saveTo()");
		ByteArrayOutputStream bytes = new ByteArrayOutputStream() {
			private void check(int n) {
				if ((long) count + n > limit)
					throw new IllegalStateException("Config output exceeds in-memory limit; use save() or saveTo()");
			}

			@Override
			public synchronized void write(int b) {
				check(1);
				super.write(b);
			}

			@Override
			public synchronized void write(byte[] b, int off, int len) {
				check(len);
				super.write(b, off, len);
			}
		};
		try {
			saveTo(config, format, bytes, markSaved);
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Config serialization failed", e);
		}
	}

	public String saveAsString(Config config, boolean markSaved) {
		return saveAsString(config, format, markSaved);
	}

	public static String saveAsString(Config config, String format, boolean markSaved) {
		format = format.toLowerCase(Locale.ROOT);
		if ("byte".equals(format))
			return new String(save(config, format, markSaved), StandardCharsets.US_ASCII);
		ConfigDocument document = config.getDataLoader().document;
		me.devtec.shared.dataholder.codec.StringContainerWriter writer = new me.devtec.shared.dataholder.codec.StringContainerWriter();
		try {
			ConfigWriter.INSTANCE.write(document, format, writer);
			String result = writer.toString();
			if (markSaved)
				document.storage.store().markSaved();
			return result;
		} catch (IOException e) {
			throw new IllegalStateException("Config serialization failed", e);
		}
	}

	public StringContainer saveAsContainer(Config config, boolean markSaved) {
		if ("byte".equals(format))
			return new StringContainer(saveAsString(config, markSaved));
		me.devtec.shared.dataholder.codec.StringContainerWriter writer = new me.devtec.shared.dataholder.codec.StringContainerWriter();
		try {
			ConfigWriter.INSTANCE.write(config.getDataLoader().document, format, writer);
			if (markSaved)
				config.getDataLoader().markSavedValues();
			return writer.container();
		} catch (IOException e) {
			throw new IllegalStateException("Config serialization failed", e);
		}
	}

	public Iterator<CharSequence> saveAsIterator(Config config, boolean markSaved) {
		return Collections.<CharSequence>singletonList(saveAsString(config, markSaved)).iterator();
	}

	public static void register(LoaderPriority priority, DataLoaderConstructor constructor) {
		CUSTOM.add(constructor);
	}

	public static boolean unregister(DataLoaderConstructor constructor) {
		return CUSTOM.remove(constructor);
	}

	public static DataLoader findLoaderByName(String name) {
		if (name == null)
			return null;

		for (DataLoaderConstructor constructor : CUSTOM)
			if (constructor.isConstructorOf(name))
				return constructor.construct();

		String format = name.toLowerCase(Locale.ROOT);

		switch (format) {
		case "yaml":
		case "json":
		case "toml":
		case "properties":
		case "byte":
		case "empty":
			return new DataLoader(format);
		default:
			return null;
		}
	}

	public static DataLoader findLoaderFor(String input) {
		try {
			if (input == null || input.isEmpty())
				return new DataLoader(new ConfigDocument());

			return new DataLoader(parse(new StringReader(input), null, input.length() * 2L));
		} catch (IOException | RuntimeException e) {
			lastLoadError = e;
			return failed();
		}
	}

	public static DataLoader findLoaderFor(File file) {
		try {
			return new DataLoader(read(file, null));
		} catch (IOException | RuntimeException e) {
			lastLoadError = e;

			e.printStackTrace();

			return failed();
		}
	}

	public static DataLoader findLoaderFor(InputStream input) {
		try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
			return new DataLoader(parse(reader, null, 0));
		} catch (IOException | RuntimeException e) {
			lastLoadError = e;
			return failed();
		}
	}

	private static DataLoader failed() {
		DataLoader d = new EmptyLoader();
		d.loaded = false;
		return d;
	}

	private static ConfigDocument read(File file, String format) throws IOException {
		final long sourceBytes = file.length();

		if (sourceBytes == 0) {
			ConfigDocument document = new ConfigDocument();
			document.format = format == null ? "empty" : format;
			return document;
		}

		try (FileInputStream input = new FileInputStream(file)) {
			if (sourceBytes <= 64 * 1024) {
				byte[] bytes = new byte[(int) sourceBytes + 1];
				int count = 0;

				while (count < bytes.length) {
					int read = input.read(bytes, count, bytes.length - count);

					if (read < 0) {
						java.nio.CharBuffer text = StandardCharsets.UTF_8.newDecoder()
								.onMalformedInput(CodingErrorAction.REPORT)
								.onUnmappableCharacter(CodingErrorAction.REPORT)
								.decode(java.nio.ByteBuffer.wrap(bytes, 0, count));

						if (text.hasArray())
							return parse(new CharArrayReader(text.array(), text.arrayOffset() + text.position(),
									text.remaining()), format, count);

						char[] chars = new char[text.remaining()];
						text.get(chars);
						return parse(new CharArrayReader(chars), format, count);
					}

					if (read != 0)
						count += read;
				}

				try (Reader reader = new InputStreamReader(
						new SequenceInputStream(new ByteArrayInputStream(bytes), input),
						StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
								.onUnmappableCharacter(CodingErrorAction.REPORT))) {
					return parse(reader, format, sourceBytes);
				}
			}

			try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
				return parse(reader, format, sourceBytes);
			}
		}
	}

	private static ConfigDocument parse(Reader source, String format, long bytes) throws IOException {
		char[] sample = new char[4096];
		int count = source.read(sample);
		int start = count > 0 && sample[0] == '\uFEFF' ? 1 : 0;
		int length = count > start ? count - start : 0;

		if (format == null)
			format = FormatRegistry.detect(sample, start, length);

		PushbackReader reader = new PushbackReader(source, sample.length);

		if (length != 0)
			reader.unread(sample, start, length);

		ConfigDocument document = new ConfigDocument();

		try {
			document.storage.preflight(bytes);
			document.storage.beginBulkLoad(bytes);

			try {
				if ("byte".equals(format))
					FormatRegistry.parse(new BufferedReader(reader, 8192), format, document);
				else
					FormatRegistry.parse(reader, format, document);
			} finally {
				document.storage.endBulkLoad();
			}

			return document;
		} catch (IOException | RuntimeException e) {
			document.close();
			throw e;
		}
	}
}
