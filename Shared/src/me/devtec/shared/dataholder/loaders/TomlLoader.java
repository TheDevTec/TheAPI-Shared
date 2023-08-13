package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
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
		if (input == null || input.length() == 0)
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
			if (line.charAt(0) == ' ') { // S-s-space?! Maybe.. this is YAML file.
				data.clear();
				comments = null;
				break;
			}
			String[] parts = readConfigLine(trimmed);
			if (parts == null) { // Didn't find = symbol.. Maybe this is YAML file.
				data.clear();
				comments = null;
				break;
			}
			String[] value = YamlLoader.splitFromComment(0, parts[1]);

			if (parts.length == 3) { // map or array
				mainPath = parts[0];
				if (comments != null)
					set(mainPath, DataValue.of(null, null, null, comments));
				comments = null;
				mode = parts[2].charAt(0) == 'a' ? 1 : 2;
				map = null;
				list = null;
				continue;
			}
			if (mode == 0) // root
				set(parts[0], DataValue.of(value[0], Json.reader().read(value[0]), value.length == 2 ? value[1] : null, comments));
			else // sub
			if (mode == 1)
				set(mainPath + '.' + parts[0], DataValue.of(value[0], Json.reader().read(value[0]), value.length >= 2 ? value[1] : null, comments));
			else {
				if (list == null) {
					DataValue probablyCreated = get(mainPath);
					list = probablyCreated != null && probablyCreated.value instanceof List ? (List<Map<Object, Object>>) probablyCreated.value : new ArrayList<>();
					set(mainPath, DataValue.of(null, list, value.length >= 2 ? value[1] : null, comments));
				}
				if (map == null) {
					map = new HashMap<>();
					list.add(map);
				}
				map.put(parts[0], value);
			}
			comments = null;
			continue;
		}
		if (comments != null)
			if (data.isEmpty())
				header = comments;
			else
				footer = comments;
		loaded = comments != null || !data.isEmpty();
	}

	@Override
	public String saveAsString(Config config, boolean markSaved) {
		return saveAsContainer(config, markSaved).toString();
	}

	@Override
	public byte[] save(Config config, boolean markSaved) {
		return saveAsContainer(config, markSaved).getBytes();
	}

	// TODO fix
	public StringContainer saveAsContainer(Config config, boolean markSaved) {
		int size = config.getDataLoader().get().size();
		StringContainer builder = new StringContainer(size * 20);
		if (config.getDataLoader().getHeader() != null)
			try {
				for (String h : config.getDataLoader().getHeader())
					builder.append(h).append(System.lineSeparator());
			} catch (Exception er) {
				er.printStackTrace();
			}
		boolean first = true;
		Iterator<Entry<String, DataValue>> iterator = config.getDataLoader().entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<String, DataValue> key = iterator.next();
			if (first)
				first = false;
			else
				builder.append(System.lineSeparator());
			if (markSaved)
				key.getValue().modified = false;
			if (key.getValue().value == null) {
				if (key.getValue().commentAfterValue != null)
					builder.append(key.getKey()).append(':').append(' ').append(key.getValue().commentAfterValue);
				continue;
			}
			builder.append(key.getKey()).append(':').append(' ').append(Json.writer().write(key.getValue().value));
			if (key.getValue().commentAfterValue != null)
				builder.append(' ').append(key.getValue().commentAfterValue);
		}
		if (config.getDataLoader().getFooter() != null)
			try {
				for (String h : config.getDataLoader().getFooter())
					builder.append(h).append(System.lineSeparator());
			} catch (Exception er) {
				er.printStackTrace();
			}
		return builder;
	}

	public static String[] readConfigLine(String input) {
		int mapIndex = input.indexOf('[');
		if (mapIndex != -1 && input.length() > mapIndex + 1 && input.indexOf(mapIndex + 1) == '[') { // array
			int mapEndIndex = input.indexOf(mapIndex, ']');
			if (mapEndIndex != -1 && input.length() > mapEndIndex + 1 && input.charAt(mapEndIndex + 1) == ']')
				return new String[] { input.substring(2, input.length() - 2), "", "a" };
		}
		if (mapIndex != -1) { // map
			int mapEndIndex = input.indexOf(mapIndex, ']');
			if (mapEndIndex != -1)
				return new String[] { input.substring(1, input.length() - 1), "", "b" };
		}
		int index = input.indexOf('=');
		int colorIndex = input.indexOf(':');
		if (index == -1 || colorIndex != -1 && colorIndex < index)
			return null;
		if (input.length() - index > 0) {
			String[] result = new String[2];
			result[0] = input.substring(0, index);
			result[1] = input.substring(index + 1);
			return result;
		}
		String[] result = new String[2];
		result[0] = input;
		result[1] = "";
		return result;
	}

	@Override
	public String name() {
		return "toml";
	}
}
