package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.loaders.toml.TomlSectionBuilderHelper;
import me.devtec.shared.json.Json;
import me.devtec.shared.scheduler.Scheduler;
import me.devtec.shared.scheduler.Tasker;

public class TomlLoader extends EmptyLoader {

	private boolean readingReachedEnd;
	private int startIndex;
	private int endIndex;
	private String lines;

	private final String readLine() {
		try {
			return startIndex == -1 ? null : endIndex == -1 ? lines.substring(startIndex) : lines.substring(startIndex, endIndex);
		} finally {
			startIndex = endIndex == -1 ? -1 : endIndex + 1;
			if (startIndex < lines.length() && startIndex != -1 && lines.charAt(startIndex) == '\r')
				++startIndex;
			endIndex = startIndex == -1 ? -1 : lines.indexOf('\n', startIndex);
			if (endIndex < lines.length() && endIndex != -1 && lines.charAt(endIndex) == '\r')
				++endIndex;
		}
	}

	@Override
	public void reset() {
		super.reset();
		readingReachedEnd = false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void load(String input) {
		if (input == null)
			return;
		reset();
		lines = input;
		if (lines == null)
			startIndex = -1;
		else {
			startIndex = 0;
			endIndex = lines.indexOf('\n');
			if (endIndex < lines.length() && endIndex != -1 && lines.charAt(endIndex) == '\r')
				++endIndex;
		}

		Queue<String> lines = new ConcurrentLinkedQueue<>();
		int task = -1;
		if (input.length() >= 35000)
			task = new Tasker() {

				@Override
				public void run() {
					String line;
					while (!isCancelled() && (line = readLine()) != null)
						lines.add(line);
					readingReachedEnd = true;
				}
			}.runTask();
		else {
			String line;
			while ((line = readLine()) != null)
				lines.add(line);
			readingReachedEnd = true;
		}

		List<String> comments = null;
		List<Map<Object, Object>> list = null;
		Map<Object, Object> map = null;
		int mode = 0;
		String mainPath = "";

		while (!readingReachedEnd || !lines.isEmpty()) {
			if (lines.isEmpty())
				continue;
			String line = lines.poll();
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
				if (comments == null)
					comments = new ArrayList<>();
				comments.add(trimmed);
				continue;
			}
			String[] parts = readConfigLine(trimmed);
			if (parts == null) { // Didn't find = symbol.. Maybe this is YAML file.
				data.clear();
				comments = null;
				break;
			}
			String[] value = YamlLoader.splitFromComment(0, parts[1]);

			if (parts.length == 3) { // section or array with maps
				mainPath = parts[0];
				mode = parts[2].charAt(0) != 'a' ? 1 : 2;
				DataValue probablyCreated = get(mainPath);
				if (mode != 2 && probablyCreated == null && comments != null)
					set(mainPath, DataValue.of(null, null, null, comments));
				map = null;
				list = null;

				if (mode == 2) {
					list = probablyCreated != null && probablyCreated.value != null ? (List<Map<Object, Object>>) probablyCreated.value : new ArrayList<>();
					if (probablyCreated == null || probablyCreated.value == null)
						set(mainPath, DataValue.of(null, list, null, comments));
					map = new HashMap<>();
					list.add(map);
				}
				comments = null;
				continue;
			}
			if (mode == 0)
				set(parts[0], DataValue.of(value[0], Json.reader().read(value[0]), value.length == 2 ? value[1] : null, comments));
			else // sub
			if (mode == 1)
				set(mainPath + '.' + parts[0], DataValue.of(value[0], Json.reader().read(value[0]), value.length >= 2 ? value[1] : null, comments));
			else
				map.put(parts[0], Json.reader().read(value[0]));
			comments = null;
			continue;
		}
		if (task != -1)
			Scheduler.cancelTask(task);
		if (comments != null) {
			if (comments.get(comments.size() - 1).isEmpty()) {
				comments.remove(comments.size() - 1); // just empty line
				if (comments.isEmpty())
					comments = null;
			}
			if (comments != null)
				if (data.isEmpty())
					header = comments;
				else
					footer = comments;
		}
		loaded = comments != null || !data.isEmpty();
	}

	@Override
	public String saveAsString(Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		return saveAsContainer(config, markSaved).toString();
	}

	@Override
	public byte[] save(Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		return saveAsContainer(config, markSaved).getBytes();
	}

	public StringContainer saveAsContainer(Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		int size = config.getDataLoader().get().size();
		StringContainer builder = new StringContainer(size * 20);
		Iterator<String> itr = saveAsIterator(config, markSaved);
		while (itr.hasNext())
			builder.append(itr.next());
		return builder;
	}

	@Override
	public boolean supportsIteratorMode() {
		return true;
	}

	@Override
	public Iterator<String> saveAsIterator(@Nonnull Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		return new Iterator<String>() {
			// 0=header
			// 1=lines
			// 2=footer
			byte phase = 0;
			int posInPhase = 0;
			List<String> list;

			@Override
			public String next() {
				switch (phase) {
				case 0:
					return config.getDataLoader().getHeader() instanceof List ? ((List<String>) config.getDataLoader().getHeader()).get(posInPhase++) + System.lineSeparator()
							: config.getDataLoader().getHeader().toArray(new String[0])[posInPhase++] + System.lineSeparator();
				case 1:
					return list.get(posInPhase++);
				case 2:
					return config.getDataLoader().getFooter() instanceof List ? ((List<String>) config.getDataLoader().getFooter()).get(posInPhase++) + System.lineSeparator()
							: config.getDataLoader().getFooter().toArray(new String[0])[posInPhase++] + System.lineSeparator();
				}
				return null;
			}

			@Override
			public boolean hasNext() {
				switch (phase) {
				case 0:
					if (config.getDataLoader().getHeader().isEmpty() || config.getDataLoader().getHeader().size() == posInPhase) {
						phase = 1;
						posInPhase = 0;
						list = TomlSectionBuilderHelper.prepareBuilder(config.getDataLoader().getPrimaryKeys(), config.getDataLoader(), markSaved);
						return hasNext();
					}
					return true;
				case 1:
					if (list.size() != posInPhase)
						return true;
					phase = 2;
					posInPhase = 0;
					return hasNext();
				case 2:
					if (config.getDataLoader().getFooter().isEmpty() || config.getDataLoader().getFooter().size() == posInPhase)
						return false;
					return true;
				}
				return false;
			}
		};
	}

	protected static String[] readConfigLine(String input) {
		int yamlIndex = input.indexOf(':');
		int index = input.indexOf('=');
		int ignoreChar;
		if (yamlIndex != -1 && yamlIndex < index && ((ignoreChar = input.indexOf('"')) == -1 || ignoreChar > yamlIndex))
			return null;
		int mapIndex = input.indexOf('[');
		if (index == -1 || index > mapIndex) {
			if (mapIndex != -1 && input.length() > mapIndex + 1 && input.charAt(mapIndex + 1) == '[') { // array of maps
				int mapEndIndex = input.lastIndexOf(']');
				if (mapEndIndex != -1 && mapEndIndex - 1 > mapIndex && input.charAt(mapEndIndex - 1) == ']')
					return new String[] { getFromQuotes(input.substring(2, input.length() - 2)), "", "a" };
			}
			if (mapIndex != -1) { // map
				int mapEndIndex = input.lastIndexOf(']');
				if (mapEndIndex != -1)
					return new String[] { getFromQuotes(input.substring(1, input.length() - 1)), "", "b" };
			}
		}
		if (index == -1 || index == 0)
			return null;
		if (input.length() - index > 0) {
			String[] result = new String[2];
			result[0] = getFromQuotes(input.substring(0, index).trim());
			result[1] = input.substring(index + 1).trim();
			return result;
		}
		String[] result = new String[2];
		result[0] = getFromQuotes(input.trim());
		result[1] = "";
		return result;
	}

	protected static String getFromQuotes(String input) {
		int len = input.length();
		if (len <= 2)
			return input;
		char firstChar = input.charAt(0);
		char lastChar = input.charAt(input.length() - 1);
		if (firstChar == '\'' && lastChar == '\'' || firstChar == '"' && lastChar == '"')
			return input.substring(1, input.length() - 1);
		return input;
	}

	@Override
	public String name() {
		return "toml";
	}
}
