package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class YamlLoader extends EmptyLoader {
	private static final int READER_TYPE_NONE = 0;
	private static final int READER_TYPE_STRING = 1;
	private static final int READER_TYPE_LIST = 2;
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
		List<Object> list = null;
		int readerType = READER_TYPE_NONE;
		StringContainer stringContainer = null;

		StringContainer key = new StringContainer(32);
		int depth = 0;
		String line;
		while ((line = readLine()) != null) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
				if (comments == null)
					comments = new ArrayList<>();
				comments.add(trimmed);
				continue;
			}
			// list
			if (trimmed.length() > 2 && trimmed.charAt(0) == '-' && trimmed.charAt(1) == ' ') {
				if (list == null)
					list = new ArrayList<>();
				list.add(Json.reader().read(splitFromComment(2, trimmed)[0]));
				continue;
			}
			String[] parts = readConfigLine(trimmed);
			if (parts == null) {
				switch (readerType) {
				case READER_TYPE_NONE:
					break;
				case READER_TYPE_STRING:
					stringContainer.append(splitFromComment(0, trimmed)[0]);
					break;
				case READER_TYPE_LIST:
					if (list == null)
						list = new ArrayList<>();
					list.add(Json.reader().read(splitFromComment(0, trimmed)[0]));
					break;
				}
				continue;
			}
			int currentDepth = getDepth(line);
			String currentKey = parts[0];
			if (list != null) {
				readerType = READER_TYPE_NONE;
				DataValue data = this.data.get(key.toString());
				if (data == null)
					this.data.put(key.toString(), data = DataValue.of(null, null, null, comments));
				comments = null;
				data.value = list;
				list = null;
			} else if (stringContainer != null) {
				readerType = READER_TYPE_NONE;
				DataValue data = this.data.get(key.toString());
				if (data == null)
					this.data.put(key.toString(), data = DataValue.of(null, null, null, comments));
				comments = null;
				data.writtenValue = stringContainer.toString();
				data.value = data.writtenValue;
				stringContainer = null;
			}
			if (currentDepth > depth) // Up
				key.append('.');
			else if (currentDepth < depth) { // Down
				if (currentDepth == 0)
					key.clear();
				else
					key.delete(key.lastIndexOf('.', key.length(), depth - currentDepth + 1) + 1, key.length()); // Don't remove dot
			} else if (currentDepth == 0) {
				key.clear();
				primaryKeys.add(currentKey);
			} else
				key.delete(key.lastIndexOf('.') + 1, key.length()); // Don't remove dot
			key.append(currentKey);
			depth = currentDepth;
			if (parts.length == 1) {
				if (comments != null) {
					data.put(key.toString(), DataValue.of(null, null, null, comments));
					comments = null;
				}
				continue;
			}
			String[] readerValue = splitFromComment(0, parts[1]);
			String value = readerValue[0];
			String comment = readerValue.length == 1 ? null : readerValue[1];
			if (value.length() > 0) {
				if (value.length() == 1 && parts[1].length() == 1 && value.charAt(0) == '|') {
					readerType = READER_TYPE_STRING;
					data.put(key.toString(), DataValue.of(null, value, comment, comments));
					stringContainer = new StringContainer(64);
					comments = null;
					continue;
				}
				if (value.length() == 2 && parts[1].length() == 2 && value.charAt(0) == '|' && value.charAt(1) == '-') {
					readerType = READER_TYPE_LIST;
					data.put(key.toString(), DataValue.of(null, value, comment, comments));
					comments = null;
					continue;
				}
				data.put(key.toString(), DataValue.of(value, Json.reader().read(value), comment, comments));
			} else
				data.put(key.toString(), DataValue.of(parts[0].length() >= 1 && !(parts[0].charAt(0) == '"' || parts[0].charAt(0) == '\'') ? null : value,
						parts[0].length() >= 1 && !(parts[0].charAt(0) == '"' || parts[0].charAt(0) == '\'') ? null : value, comment, comments));
			comments = null;
		}
		if (list != null) {
			DataValue data = this.data.get(key.toString());
			if (data == null)
				this.data.put(key.toString(), data = DataValue.of(null, null, null, comments));
			data.value = list;
		} else if (stringContainer != null) {
			DataValue data = this.data.get(key.toString());
			if (data == null)
				this.data.put(key.toString(), data = DataValue.of(null, null, null, comments));
			data.writtenValue = stringContainer.toString();
			data.value = data.writtenValue;
		}
		if (comments != null)
			if (data.isEmpty())
				header = comments;
			else
				footer = comments;
		loaded = comments != null || !data.isEmpty();
	}

	public static String[] readConfigLine(String input) {
		int index = -1;

		for (int i = 0; i < input.length() - 1; i++)
			if (input.charAt(i) == ':' && input.charAt(i + 1) == ' ') {
				index = i;
				break;
			}

		if (index != -1) {
			String[] result = new String[2];
			result[0] = input.substring(0, index);
			result[1] = input.substring(index + 2);
			return result;
		}
		int length = input.length() - 1;
		if (input.charAt(length) == ':') {
			String[] result = new String[1];
			result[0] = input.substring(0, length);
			return result;
		}
		return null;
	}

	public static String[] splitFromComment(int posFromStart, String input) {
		int len = input.length();
		if (len <= 1)
			return new String[] { input };
		char firstChar = input.charAt(posFromStart);
		if (firstChar == '[' || firstChar == '{')
			return splitFromCommentJson(posFromStart, input);

		int i = posFromStart;
		int quoteCount = 0;
		char currentQueto = 0;
		boolean inQuotes = firstChar == '"' || firstChar == '\'';
		if (inQuotes) {
			currentQueto = firstChar;
			++quoteCount;
			++i;
		}
		boolean escaped = false;
		boolean foundHash = false;
		int lastQuotePos = 0;
		int splitIndexEnd = 0;
		int splitIndexStart = 0;
		while (i < len) {
			char c = input.charAt(i);
			if (!escaped && c == '\\')
				escaped = true;
			else if (inQuotes && c == currentQueto && !escaped) {
				lastQuotePos = i;
				quoteCount--;
				if (!(inQuotes = quoteCount > 0))
					splitIndexEnd = i;
			} else if (!inQuotes && c == '#' && !escaped) {
				foundHash = true;
				splitIndexStart = i;
				if (splitIndexEnd == 0)
					splitIndexEnd = i;
				break;
			} else
				escaped = false;
			i++;
		}
		if (!foundHash)
			return new String[] { lastQuotePos == 0 || quoteCount != 0 ? posFromStart == 0 ? input.trim() : input.substring(posFromStart).trim() : input.substring(1 + posFromStart, lastQuotePos) };
		return new String[] { currentQueto == 0 || quoteCount != 0 ? input.substring(posFromStart, splitIndexEnd).trim() : input.substring(1 + posFromStart, splitIndexEnd),
				input.substring(splitIndexStart) };
	}

	private static String[] splitFromCommentJson(int posFromStart, String input) {
		int len = input.length();
		int i = posFromStart;
		int braceCount = 0;
		int bracketCount = 0;
		boolean inQuotes = false;
		boolean escaped = false;
		int splitIndex = -1;
		while (i < len) {
			char c = input.charAt(i);
			if (!escaped && c == '\\')
				escaped = true;
			else if (!inQuotes && c == '{' && !escaped)
				braceCount++;
			else if (!inQuotes && c == '}' && !escaped) {
				braceCount--;
				if (braceCount == 0 && bracketCount == 0) {
					splitIndex = i + 1;
					break;
				}
			} else if (!inQuotes && c == '[' && !escaped)
				bracketCount++;
			else if (!inQuotes && c == ']' && !escaped) {
				bracketCount--;
				if (braceCount == 0 && bracketCount == 0) {
					splitIndex = i + 1;
					break;
				}
			} else if (!inQuotes && c == '#' && !escaped) {
				if (braceCount == 0 && bracketCount == 0) {
					splitIndex = i;
					break;
				}
			} else if ((c == '"' || c == '\'') && !escaped)
				inQuotes = !inQuotes;
			else if (escaped)
				escaped = false;
			i++;
		}
		String[] result = new String[2];
		if (splitIndex == -1)
			result[0] = input;
		else {
			result[0] = input.substring(posFromStart, splitIndex).trim();
			result[1] = input.substring(splitIndex);
		}
		return result;
	}

	private static int getDepth(String line) {
		int depth = 0;
		while (line.charAt(depth) == ' ')
			depth++;
		return depth / 2;
	}
}
