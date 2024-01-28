package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class PropertiesLoader extends EmptyLoader {

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

		String line;
		while ((line = readLine()) != null) {
			String trimmed = line.trim();

			// Comments
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
			set(parts[0], DataValue.of(value[0], Json.reader().read(value[0]), value.length == 2 ? value[1] : null, comments));
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
					builder.append(key.getKey()).append('=').append(key.getValue().commentAfterValue);
				continue;
			}
			builder.append(key.getKey()).append('=').append(Json.writer().write(key.getValue().value));
			if (key.getValue().commentAfterValue != null)
				builder.append(' ').append(key.getValue().commentAfterValue);
		}
		try {
			for (String h : config.getDataLoader().getFooter())
				builder.append(h).append(System.lineSeparator());
		} catch (Exception er) {
			er.printStackTrace();
		}
		return builder;
	}

	protected static String[] readConfigLine(String input) {
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
		return "properties";
	}
}
