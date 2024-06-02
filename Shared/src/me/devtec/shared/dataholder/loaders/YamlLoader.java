package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.loaders.yaml.YamlSectionBuilderHelper;
import me.devtec.shared.json.Json;
import me.devtec.shared.scheduler.Scheduler;
import me.devtec.shared.scheduler.Tasker;

public class YamlLoader extends EmptyLoader {

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

	@Override
	public void load(String input) {
		if (input == null)
			return;
		reset();

		lines = input;
		// Init
		startIndex = 0;
		endIndex = lines.indexOf('\n');
		if (endIndex < lines.length() && endIndex != -1 && lines.charAt(endIndex) == '\r')
			++endIndex;

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

		// Temp values
		List<String> comments = null;
		List<Object> list = null;
		StringContainer stringContainer = null;

		StringContainer key = new StringContainer(32);
		int depth = 0;
		int lastIndexOfDot = 0;

		byte readerMode = 0;
		while (!readingReachedEnd || !lines.isEmpty()) {
			if (lines.isEmpty())
				continue;
			String line = lines.poll();
			String trimmed = line.trim();
			// Comments
			if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
				if (comments == null)
					comments = new ArrayList<>();
				comments.add(trimmed);
				continue;
			}

			switch (readerMode) {
			case 0: { // Section: value/empty
				String[] parts = readConfigLine(trimmed);
				if (parts == null) // Invalid section
					continue;

				// Key
				int currentDepth = getDepth(line);
				lastIndexOfDot = buildKey(key, parts[0], depth, currentDepth, lastIndexOfDot);
				depth = currentDepth;

				if (parts.length == 1) {
					readerMode = 1; // List or Section to break
					if (comments != null) {
						DataValue val = getOrCreate(key.toString());
						val.comments = comments;
						comments = null;
					}
					continue;
				}

				// Value
				String[] readerValue = splitFromComment(0, parts[1]);
				String value = readerValue[0];
				String comment = readerValue.length == 1 ? null : readerValue[1];
				if (value.length() > 0) {
					if (value.length() == 1 && parts[1].length() == 1 && value.charAt(0) == '|') {
						readerMode = 2;
						set(key.toString(), DataValue.of(null, value, comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					if (value.length() == 2 && parts[1].length() == 2 && value.charAt(0) == '|' && value.charAt(1) == '-') {
						readerMode = 3;
						set(key.toString(), DataValue.of(null, value, comment, comments));
						comments = null;
						continue;
					}
					set(key.toString(), DataValue.of(value, Json.reader().read(value), comment, comments));
				} else
					set(key.toString(), DataValue.of(parts[1].length() >= 1 && parts[1].charAt(0) != '"' && parts[1].charAt(0) != '\'' ? null : value,
							parts[1].length() >= 1 && parts[1].charAt(0) != '"' && parts[1].charAt(0) != '\'' ? null : value, comment, comments));
				comments = null;
			}
				break;
			case 1: { // List or Section to break
				if (trimmed.length() > 2 && trimmed.charAt(0) == '-' && trimmed.charAt(1) == ' ') {
					if (list == null)
						list = new ArrayList<>();
					list.add(Json.reader().read(splitFromComment(2, trimmed)[0]));
					continue;
				}
				String[] parts = readConfigLine(trimmed);
				if (parts == null)
					continue;

				readerMode = 0;

				if (list != null) {
					DataValue val = getOrCreate(key.toString());
					val.value = list;
					list = null;
				}

				int currentDepth = getDepth(line);
				lastIndexOfDot = buildKey(key, parts[0], depth, currentDepth, lastIndexOfDot);
				depth = currentDepth;
				if (parts.length == 1) {
					readerMode = 1; // List or Section to break
					if (comments != null) {
						DataValue val = getOrCreate(key.toString());
						val.comments = comments;
						comments = null;
					}
					continue;
				}
				String[] readerValue = splitFromComment(0, parts[1]);
				String value = readerValue[0];
				String comment = readerValue.length == 1 ? null : readerValue[1];
				if (value.length() > 0) {
					if (value.length() == 1 && parts[1].length() == 1 && value.charAt(0) == '|') {
						readerMode = 2;
						set(key.toString(), DataValue.of(null, value, comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					if (value.length() == 2 && parts[1].length() == 2 && value.charAt(0) == '|' && value.charAt(1) == '-') {
						readerMode = 3;
						set(key.toString(), DataValue.of(null, value, comment, comments));
						comments = null;
						continue;
					}
					set(key.toString(), DataValue.of(value, Json.reader().read(value), comment, comments));
				} else
					set(key.toString(), DataValue.of(parts[1].length() >= 1 && parts[1].charAt(0) != '"' && parts[1].charAt(0) != '\'' ? null : value,
							parts[1].length() >= 1 && parts[1].charAt(0) != '"' && parts[1].charAt(0) != '\'' ? null : value, comment, comments));
				comments = null;
			}
				break;
			case 2: { // String reader
				String[] parts = readConfigLine(trimmed);
				if (parts == null) {
					stringContainer.append(splitFromComment(0, trimmed)[0]);
					continue;
				}

				readerMode = 0;

				if (stringContainer != null) {
					String value = stringContainer.toString();
					DataValue val = getOrCreate(key.toString());
					val.value = value;
					val.writtenValue = value;
				}

				int currentDepth = getDepth(line);
				lastIndexOfDot = buildKey(key, parts[0], depth, currentDepth, lastIndexOfDot);
				depth = currentDepth;
				if (parts.length == 1) {
					readerMode = 1; // List or Section to break
					if (comments != null) {
						DataValue val = getOrCreate(key.toString());
						val.comments = comments;
						comments = null;
					}
					continue;
				}
				String[] readerValue = splitFromComment(0, parts[1]);
				String value = readerValue[0];
				String comment = readerValue.length == 1 ? null : readerValue[1];
				if (value.length() > 0) {
					if (value.length() == 1 && parts[1].length() == 1 && value.charAt(0) == '|') {
						readerMode = 2;
						set(key.toString(), DataValue.of(null, value, comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					if (value.length() == 2 && parts[1].length() == 2 && value.charAt(0) == '|' && value.charAt(1) == '-') {
						readerMode = 3;
						set(key.toString(), DataValue.of(null, value, comment, comments));
						comments = null;
						continue;
					}
					set(key.toString(), DataValue.of(value, Json.reader().read(value), comment, comments));
				} else
					set(key.toString(), DataValue.of(parts[1].length() >= 1 && parts[1].charAt(0) != '"' && parts[1].charAt(0) != '\'' ? null : value,
							parts[1].length() >= 1 && parts[1].charAt(0) != '"' && parts[1].charAt(0) != '\'' ? null : value, comment, comments));
				comments = null;
			}
				break;
			case 3: { // List reader
				String[] parts = readConfigLine(trimmed);
				if (parts == null) {
					list.add(Json.reader().read(splitFromComment(0, trimmed)[0]));
					continue;
				}

				readerMode = 0;

				if (list != null) {
					DataValue val = getOrCreate(key.toString());
					val.value = list;
					list = null;
				}

				int currentDepth = getDepth(line);
				lastIndexOfDot = buildKey(key, parts[0], depth, currentDepth, lastIndexOfDot);
				depth = currentDepth;
				if (parts.length == 1) {
					readerMode = 1; // List or Section to break
					if (comments != null) {
						DataValue val = getOrCreate(key.toString());
						val.comments = comments;
						comments = null;
					}
					continue;
				}
				String[] readerValue = splitFromComment(0, parts[1]);
				String value = readerValue[0];
				String comment = readerValue.length == 1 ? null : readerValue[1];
				if (value.length() > 0) {
					if (value.length() == 1 && parts[1].length() == 1 && value.charAt(0) == '|') {
						readerMode = 2;
						set(key.toString(), DataValue.of(null, value, comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					if (value.length() == 2 && parts[1].length() == 2 && value.charAt(0) == '|' && value.charAt(1) == '-') {
						readerMode = 3;
						set(key.toString(), DataValue.of(null, value, comment, comments));
						comments = null;
						continue;
					}
					set(key.toString(), DataValue.of(value, Json.reader().read(value), comment, comments));
				} else
					set(key.toString(), DataValue.of(parts[1].length() >= 1 && parts[1].charAt(0) != '"' && parts[1].charAt(0) != '\'' ? null : value,
							parts[1].length() >= 1 && parts[1].charAt(0) != '"' && parts[1].charAt(0) != '\'' ? null : value, comment, comments));
				comments = null;
			}
				break;
			}
		}
		if (task != -1)
			Scheduler.cancelTask(task);
		switch (readerMode) {
		case 1:
		case 3:
			DataValue val = getOrCreate(key.toString());
			val.value = list;
			break;
		case 2:
			String writtenValue = stringContainer.toString();
			val = getOrCreate(key.toString());
			val.value = writtenValue;
			val.writtenValue = writtenValue;
			break;
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

	private int buildKey(StringContainer key, String currentKey, int depth, int currentDepth, int lastIndexOfDot) {
		if (currentDepth == 0)
			key.clear();
		else if (currentDepth > depth) { // Up
			key.append('.');
			lastIndexOfDot = key.length();
		} else if (currentDepth < depth) { // Down
			if (currentDepth == 0)
				key.clear();
			else {
				key.delete(key.lastIndexOf('.', lastIndexOfDot, depth - currentDepth + 1) + 1, key.length()); // Don't remove dot
				lastIndexOfDot = key.length();
			}
		} else
			key.delete(lastIndexOfDot, key.length()); // Don't remove dot
		key.append(currentKey);
		return lastIndexOfDot;
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
						list = YamlSectionBuilderHelper.prepareBuilder(config.getDataLoader().getPrimaryKeys(), config.getDataLoader(), markSaved);
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
		int charIndex = -1;

		for (int i = 0; i < input.length(); ++i)
			if (input.charAt(i) == ':') {
				charIndex = i;
				if (i + 1 != input.length() && input.charAt(i + 1) == ' ') {
					String[] result = new String[2];
					result[0] = getFromQuotes(input.substring(0, i));
					result[1] = input.substring(i + 2).trim();
					return result;
				}
			}

		if (charIndex != -1) {
			String[] result = new String[1];
			result[0] = getFromQuotes(input.substring(0, charIndex));
			return result;
		}
		return null;
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

	protected static String[] splitFromComment(int posFromStart, String input) {
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
		boolean foundHash = false;
		int splitIndexStart = 0;
		int endOfString = -1;

		StringContainer container = new StringContainer(input, i);
		i = 0;
		while (i < container.length()) {
			char c = container.charAt(i);
			if (c == '\\' && i + 1 < container.length() && container.charAt(i + 1) == currentQueto) {
				container.deleteCharAt(i++);
				continue;
			}
			if (c == '\'' && i + 1 < container.length() && container.charAt(i + 1) == '\'') {
				container.deleteCharAt(i++);
				continue;
			}
			if (inQuotes && c == currentQueto) {
				if (!(inQuotes = --quoteCount > 0)) {
					container.deleteCharAt(i);
					endOfString = i;
				}
			} else if (!inQuotes && c == '#') {
				foundHash = true;
				splitIndexStart = i;
				break;
			}
			++i;
		}
		if (!foundHash)
			return new String[] { endOfString == -1 ? container.toString() : container.substring(0, endOfString) };
		return new String[] {
				endOfString == -1 && splitIndexStart == 0 ? container.toString() : endOfString == -1 ? container.substring(0, splitIndexStart).trim() : container.substring(0, endOfString),
				container.substring(splitIndexStart) };
	}

	private static String[] splitFromCommentJson(int posFromStart, String input) {
		int len = input.length();
		int i = posFromStart;
		int braceCount = 0;
		int bracketCount = 0;
		boolean inQuotes = false;
		int splitIndex = -1;
		while (i < len) {
			char c = input.charAt(i);
			if (c == '\\' && i + 1 < len && isSkippableChar(input.charAt(i + 1)))
				++i;
			else if (!inQuotes && c == '{')
				braceCount++;
			else if (!inQuotes && c == '}') {
				braceCount--;
				if (braceCount == 0 && bracketCount == 0) {
					splitIndex = i + 1;
					break;
				}
			} else if (!inQuotes && c == '[')
				bracketCount++;
			else if (!inQuotes && c == ']') {
				bracketCount--;
				if (braceCount == 0 && bracketCount == 0) {
					splitIndex = i + 1;
					break;
				}
			} else if (!inQuotes && c == '#') {
				if (braceCount == 0 && bracketCount == 0) {
					splitIndex = i;
					break;
				}
			} else if (c == '"' || c == '\'')
				inQuotes = !inQuotes;
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

	private static boolean isSkippableChar(char charAt) {
		switch (charAt) {
		case '{':
		case '}':
		case '[':
		case ']':
		case '#':
		case '"':
		case '\'':
			return true;
		default:
			break;
		}
		return false;
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
