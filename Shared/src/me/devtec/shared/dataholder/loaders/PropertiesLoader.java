package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class PropertiesLoader extends EmptyLoader {
	private static int LINE_SEPARATOR_LENGTH = System.lineSeparator().length();
	private static String LINE_SEPARATOR = System.lineSeparator();

	private int startIndex;
	private int endIndex;
	private String lines;

	private final String readLine() {
		try {
			return startIndex == -1 ? null : endIndex == -1 ? lines.substring(startIndex) : lines.substring(startIndex, endIndex);
		} finally {
			startIndex = endIndex == -1 ? -1 : endIndex + LINE_SEPARATOR_LENGTH;
			endIndex = startIndex == -1 ? -1 : lines.indexOf(LINE_SEPARATOR, startIndex);
		}
	}

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
			endIndex = lines.indexOf(LINE_SEPARATOR);
		}

		List<String> comments = null;

		String line;
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
			String[] value = YamlLoader.splitFromComment(0, parts[1]);
			primaryKeys.add(parts[0]);
			data.put(parts[0], DataValue.of(value[0], Json.reader().read(value[0]), value.length == 2 ? value[1] : null, comments));
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

	public static String[] readConfigLine(String input) {
		int index = input.indexOf('=');

		if (index != -1 && input.length() - index > 0) {
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
}
