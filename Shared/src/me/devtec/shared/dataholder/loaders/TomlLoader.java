package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.loaders.toml.TomlSectionBuilderHelper;
import me.devtec.shared.json.Json;

public class TomlLoader extends EmptyLoader {

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

		List<String> comments = null;
		List<Map<Object, Object>> list = null;
		Map<Object, Object> map = null;
		int mode = 0;
		String line;
		String mainPath = "";

		while ((line = readLine()) != null) {
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
					if (list == null) {
						list = probablyCreated != null && probablyCreated.value != null ? (List<Map<Object, Object>>) probablyCreated.value : new ArrayList<>();
						if (probablyCreated == null || probablyCreated.value == null)
							set(mainPath, DataValue.of(null, list, null, comments));
					}
					if (map == null) {
						map = new HashMap<>();
						list.add(map);
					}
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
		if (config.getDataLoader().getHeader() != null)
			try {
				for (String h : config.getDataLoader().getHeader())
					builder.append(h).append(System.lineSeparator());
			} catch (Exception er) {
				er.printStackTrace();
			}

		// BUILD KEYS & SECTIONS
		TomlSectionBuilderHelper.write(builder, config.getDataLoader().getPrimaryKeys(), config.getDataLoader(), markSaved);

		if (config.getDataLoader().getFooter() != null)
			try {
				for (String h : config.getDataLoader().getFooter())
					builder.append(h).append(System.lineSeparator());
			} catch (Exception er) {
				er.printStackTrace();
			}
		return builder;
	}

	protected static String[] readConfigLine(String input) {
		int yamlIndex = input.indexOf(':');
		int index = input.indexOf('=');
		int ignoreChar;
		if (yamlIndex < index && ((ignoreChar = input.indexOf('"')) == -1 || ignoreChar > yamlIndex))
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
