package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.YamlSectionBuilderHelper;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class YamlLoader extends EmptyLoader {
	private static final int READER_TYPE_NONE = 0;
	private static final int READER_TYPE_STRING = 1;
	private static final int READER_TYPE_LIST = 2;

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
			if (parts[0].equals("noRegistration"))
				System.out.println(parts[1]);
			int currentDepth = getDepth(line);
			String currentKey = getFromQuotes(parts[0]);
			if (list != null) {
				readerType = READER_TYPE_NONE;
				DataValue data = getOrCreate(key.toString());
				data.value = list;
				list = null;
			} else if (stringContainer != null) {
				readerType = READER_TYPE_NONE;
				String writtenValue = stringContainer.toString();
				DataValue data = getOrCreate(key.toString());
				data.value = writtenValue;
				data.writtenValue = writtenValue;
				stringContainer = null;
			}
			if (currentDepth == 0)
				key.clear();
			else if (currentDepth > depth) // Up
				key.append('.');
			else if (currentDepth < depth) { // Down
				if (currentDepth == 0)
					key.clear();
				else
					key.delete(key.lastIndexOf('.', key.length(), depth - currentDepth + 1) + 1, key.length()); // Don't remove dot
			} else
				key.delete(key.lastIndexOf('.') + 1, key.length()); // Don't remove dot
			key.append(currentKey);
			depth = currentDepth;
			if (parts.length == 1) {
				if (comments != null) {
					DataValue data = getOrCreate(key.toString());
					data.comments = comments;
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
					set(key.toString(), DataValue.of(null, value, comment, comments));
					stringContainer = new StringContainer(64);
					comments = null;
					continue;
				}
				if (value.length() == 2 && parts[1].length() == 2 && value.charAt(0) == '|' && value.charAt(1) == '-') {
					readerType = READER_TYPE_LIST;
					set(key.toString(), DataValue.of(null, value, comment, comments));
					comments = null;
					continue;
				}
				set(key.toString(), DataValue.of(value, Json.reader().read(value), comment, comments));
			} else
				set(key.toString(), DataValue.of(parts[1].length() >= 1 && !(parts[1].charAt(0) == '"' || parts[1].charAt(0) == '\'') ? null : value,
						parts[1].length() >= 1 && !(parts[1].charAt(0) == '"' || parts[1].charAt(0) == '\'') ? null : value, comment, comments));
			comments = null;
		}
		if (list != null) {
			readerType = READER_TYPE_NONE;
			DataValue data = getOrCreate(key.toString());
			data.value = list;
			data.comments = comments;
		} else if (stringContainer != null) {
			readerType = READER_TYPE_NONE;
			String writtenValue = stringContainer.toString();
			DataValue data = getOrCreate(key.toString());
			data.value = writtenValue;
			data.writtenValue = writtenValue;
			data.comments = comments;
		} else if (comments != null)
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

		// BUILD KEYS & SECTIONS
		YamlSectionBuilderHelper.write(builder, config.getDataLoader().getPrimaryKeys(), config.getDataLoader(), markSaved);

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

	public static String getFromQuotes(String input) {
		int len = input.length();
		if (len <= 2)
			return input;
		char firstChar = input.charAt(0);
		char lastChar = input.charAt(input.length() - 1);
		if (firstChar == '\'' && lastChar == '\'' || firstChar == '"' && lastChar == '"')
			return input.substring(1, input.length() - 1);
		return input;
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
				if (currentQueto == c && i + 1 < len && input.charAt(i + 1) == c) {
					--len;
					input = input.substring(posFromStart, i) + input.substring(++i);
					continue;
				}
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
			}
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
			else
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
		char c;
		while ((c = line.charAt(depth)) == ' ' || c == '	')
			depth++;
		return depth / 2;
	}

	@Override
	public String name() {
		return "yaml";
	}
}
