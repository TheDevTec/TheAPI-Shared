package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.devtec.shared.Pair;
import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.loaders.toml.TomlSectionBuilderHelper;
import me.devtec.shared.dataholder.loaders.yaml.YamlSectionBuilderHelper;
import me.devtec.shared.json.Json;
import me.devtec.shared.scheduler.Scheduler;
import me.devtec.shared.scheduler.Tasker;

public class YamlLoader extends EmptyLoader {

	private boolean readingReachedEnd;
	private int startIndex;
	private int endIndex;
	private StringContainer lines;

	private int[] readLine() {
		try {
			return startIndex == -1 ? null : endIndex == -1 ? new int[] { startIndex, lines.length() } : new int[] { startIndex, endIndex };
		} finally {
			startIndex = endIndex == -1 ? -1 : endIndex + 1;
			endIndex = -1;
			if (startIndex != -1)
				for (int i = startIndex; i < lines.length(); ++i) {
					char c = lines.charAt(i);
					if (c == '\r' || c == '\n') {
						if (i == startIndex) {
							++startIndex;
							continue;
						}
						endIndex = startIndex == -1 ? -1 : i;
						break;
					}
				}
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

		lines = new StringContainer(input, 0, 0);
		// Init
		startIndex = 0;
		endIndex = -1;
		for (int i = 0; i < lines.length(); ++i) {
			char c = lines.charAt(i);
			if (c == '\r' || c == '\n') {
				endIndex = i;
				break;
			}
		}

		Queue<int[]> lines = new ConcurrentLinkedQueue<>();
		int task = -1;
		if (input.length() >= 20000)
			task = new Tasker() {
				@Override
				public void run() {
					int[] line;
					while (!isCancelled() && (line = readLine()) != null)
						lines.add(line);
					readingReachedEnd = true;
				}
			}.runTask();
		else {
			int[] line;
			while ((line = readLine()) != null)
				lines.add(line);
			readingReachedEnd = true;
		}

		// Temp values
		List<String> comments = null;
		List<Object> list = null;
		StringContainer stringContainer = null;

		StringContainer key = new StringContainer(48);
		int depth = 0;
		int lastIndexOfDot = 0;

		byte readerMode = 0;
		while (!readingReachedEnd || !lines.isEmpty()) {
			if (lines.isEmpty())
				continue;
			int[] line = lines.poll();
			int currentDepth = getDepth(this.lines, line);
			int[] trimmed = trim(this.lines, line);
			// Comments
			if (trimmed[0] == trimmed[1] || this.lines.charAt(trimmed[0]) == '#') {
				if (comments == null)
					comments = new ArrayList<>();
				comments.add(trimmed[0] == trimmed[1] ? "" : this.lines.substring(trimmed[0], trimmed[1]));
				continue;
			}

			switch (readerMode) {
			case 0: { // Section: value/empty
				int[][] parts = readConfigLine(this.lines, trimmed);
				if (parts == null) // Invalid section
					continue;

				// Key
				lastIndexOfDot = buildKey(this.lines, key, parts[0], depth, currentDepth, lastIndexOfDot);
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
				Object readerValueParsed = splitFromComment(this.lines, 0, parts[1]);
				int[][] readerValue = readerValueParsed instanceof Pair ? (int[][]) ((Pair) readerValueParsed).getValue() : (int[][]) readerValueParsed;
				int[] value = readerValue[0];
				int[] indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
				String comment = readerValue.length == 1 || readerValue[1] == null ? null : this.lines.substring(readerValue[1][0], readerValue[1][1]);
				if (value[1] - value[0] > 0) {
					if (value[1] - value[0] == 1 && parts[1][1] - parts[1][0] == 1 && this.lines.charAt(value[0]) == '|') {
						readerMode = 2;
						set(key.toString(), DataValue.of(null, "|", comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					if (value[1] - value[0] == 2 && parts[1][1] - parts[1][0] == 2 && this.lines.charAt(value[0]) == '|' && this.lines.charAt(value[0] + 1) == '-') {
						readerMode = 3;
						set(key.toString(), DataValue.of(null, "|-", comment, comments));
						comments = null;
						continue;
					}
					set(key.toString(), DataValue.of(indexes == null ? this.lines.substring(value[0], value[1]) : removeCharsAt(this.lines.subSequence(value[0], value[1]), indexes),
							Json.reader().read(this.lines.substring(value[0], value[1])), comment, comments));
				} else
					set(key.toString(), DataValue.of(parts[1][1] - parts[1][0] >= 1 && this.lines.charAt(parts[1][0]) != '"' && this.lines.charAt(parts[1][0]) != '\'' ? null : "",
							parts[1][1] - parts[1][0] >= 1 && this.lines.charAt(parts[1][0]) != '"' && this.lines.charAt(parts[1][0]) != '\'' ? null : "", comment, comments));
				comments = null;
			}
				break;
			case 1: { // List or Section to break
				if (trimmed[1] - trimmed[0] > 2 && this.lines.charAt(trimmed[0]) == '-' && this.lines.charAt(trimmed[0] + 1) == ' ') {
					if (list == null)
						list = new ArrayList<>();
					Object readerValueParsed = splitFromComment(this.lines, 2, trimmed);
					int[][] readerValue = readerValueParsed instanceof Pair ? (int[][]) ((Pair) readerValueParsed).getValue() : (int[][]) readerValueParsed;
					int[] indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
					list.add(Json.reader()
							.read(indexes == null ? this.lines.substring(readerValue[0][0], readerValue[0][1]) : removeCharsAt(this.lines.subSequence(readerValue[0][0], readerValue[0][1]), indexes)));
					continue;
				}
				int[][] parts = readConfigLine(this.lines, trimmed);
				if (parts == null)
					continue;

				readerMode = 0;

				if (list != null) {
					DataValue val = getOrCreate(key.toString());
					val.value = list;
					list = null;
				}

				lastIndexOfDot = buildKey(this.lines, key, parts[0], depth, currentDepth, lastIndexOfDot);
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
				Object readerValueParsed = splitFromComment(this.lines, 0, parts[1]);
				int[][] readerValue = readerValueParsed instanceof Pair ? (int[][]) ((Pair) readerValueParsed).getValue() : (int[][]) readerValueParsed;
				int[] value = readerValue[0];
				int[] indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
				String comment = readerValue.length == 1 || readerValue[1] == null ? null : this.lines.substring(readerValue[1][0], readerValue[1][1]);
				if (value[1] - value[0] > 0) {
					if (value[1] - value[0] == 1 && parts[1][1] - parts[1][0] == 1 && this.lines.charAt(value[0]) == '|') {
						readerMode = 2;
						set(key.toString(), DataValue.of(null, "|", comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					if (value[1] - value[0] == 2 && parts[1][1] - parts[1][0] == 2 && this.lines.charAt(value[0]) == '|' && this.lines.charAt(value[0] + 1) == '-') {
						readerMode = 3;
						set(key.toString(), DataValue.of(null, "|-", comment, comments));
						comments = null;
						continue;
					}
					set(key.toString(), DataValue.of(indexes == null ? this.lines.substring(value[0], value[1]) : removeCharsAt(this.lines.subSequence(value[0], value[1]), indexes),
							Json.reader().read(this.lines.substring(value[0], value[1])), comment, comments));
				} else
					set(key.toString(), DataValue.of(parts[1][1] - parts[1][0] >= 1 && this.lines.charAt(parts[1][0]) != '"' && this.lines.charAt(parts[1][0]) != '\'' ? null : "",
							parts[1][1] - parts[1][0] >= 1 && this.lines.charAt(parts[1][0]) != '"' && this.lines.charAt(parts[1][0]) != '\'' ? null : "", comment, comments));
				comments = null;
			}
				break;
			case 2: { // String reader
				int[][] parts = readConfigLine(this.lines, trimmed);
				if (parts == null) {
					Object readerValueParsed = splitFromComment(this.lines, 0, trimmed);
					int[][] readerValue = readerValueParsed instanceof Pair ? (int[][]) ((Pair) readerValueParsed).getValue() : (int[][]) readerValueParsed;
					int[] indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
					stringContainer.append(
							indexes == null ? this.lines.substring(readerValue[0][0], readerValue[0][1]) : removeCharsAt(this.lines.subSequence(readerValue[0][0], readerValue[0][1]), indexes));
					continue;
				}

				readerMode = 0;

				if (stringContainer != null) {
					String value = stringContainer.toString();
					DataValue val = getOrCreate(key.toString());
					val.value = value;
					val.writtenValue = value;
				}

				lastIndexOfDot = buildKey(this.lines, key, parts[0], depth, currentDepth, lastIndexOfDot);
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
				Object readerValueParsed = splitFromComment(this.lines, 0, parts[1]);
				int[][] readerValue = readerValueParsed instanceof Pair ? (int[][]) ((Pair) readerValueParsed).getValue() : (int[][]) readerValueParsed;
				int[] value = readerValue[0];
				int[] indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
				String comment = readerValue.length == 1 || readerValue[1] == null ? null : this.lines.substring(readerValue[1][0], readerValue[1][1]);
				if (value[1] - value[0] > 0) {
					if (value[1] - value[0] == 1 && parts[1][1] - parts[1][0] == 1 && this.lines.charAt(value[0]) == '|') {
						readerMode = 2;
						set(key.toString(), DataValue.of(null, "|", comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					if (value[1] - value[0] == 2 && parts[1][1] - parts[1][0] == 2 && this.lines.charAt(value[0]) == '|' && this.lines.charAt(value[0] + 1) == '-') {
						readerMode = 3;
						set(key.toString(), DataValue.of(null, "|-", comment, comments));
						comments = null;
						continue;
					}
					set(key.toString(), DataValue.of(indexes == null ? this.lines.substring(value[0], value[1]) : removeCharsAt(this.lines.subSequence(value[0], value[1]), indexes),
							Json.reader().read(this.lines.substring(value[0], value[1])), comment, comments));
				} else
					set(key.toString(), DataValue.of(parts[1][1] - parts[1][0] >= 1 && this.lines.charAt(parts[1][0]) != '"' && this.lines.charAt(parts[1][0]) != '\'' ? null : "",
							parts[1][1] - parts[1][0] >= 1 && this.lines.charAt(parts[1][0]) != '"' && this.lines.charAt(parts[1][0]) != '\'' ? null : "", comment, comments));
				comments = null;
			}
				break;
			case 3: { // List reader
				int[][] parts = readConfigLine(this.lines, trimmed);
				if (parts == null) {
					Object readerValueParsed = splitFromComment(this.lines, 0, trimmed);
					int[][] readerValue = readerValueParsed instanceof Pair ? (int[][]) ((Pair) readerValueParsed).getValue() : (int[][]) readerValueParsed;
					int[] indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
					list.add(Json.reader()
							.read(indexes == null ? this.lines.substring(readerValue[0][0], readerValue[0][1]) : removeCharsAt(this.lines.subSequence(readerValue[0][0], readerValue[0][1]), indexes)));
					continue;
				}

				readerMode = 0;

				if (list != null) {
					DataValue val = getOrCreate(key.toString());
					val.value = list;
					list = null;
				}

				lastIndexOfDot = buildKey(this.lines, key, parts[0], depth, currentDepth, lastIndexOfDot);
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
				Object readerValueParsed = splitFromComment(this.lines, 0, parts[1]);
				int[][] readerValue = readerValueParsed instanceof Pair ? (int[][]) ((Pair) readerValueParsed).getValue() : (int[][]) readerValueParsed;
				int[] value = readerValue[0];
				int[] indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
				String comment = readerValue.length == 1 || readerValue[1] == null ? null : this.lines.substring(readerValue[1][0], readerValue[1][1]);
				if (value[1] - value[0] > 0) {
					if (value[1] - value[0] == 1 && parts[1][1] - parts[1][0] == 1 && this.lines.charAt(value[0]) == '|') {
						readerMode = 2;
						set(key.toString(), DataValue.of(null, "|", comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					if (value[1] - value[0] == 2 && parts[1][1] - parts[1][0] == 2 && this.lines.charAt(value[0]) == '|' && this.lines.charAt(value[0] + 1) == '-') {
						readerMode = 3;
						set(key.toString(), DataValue.of(null, "|-", comment, comments));
						comments = null;
						continue;
					}
					set(key.toString(), DataValue.of(indexes == null ? this.lines.substring(value[0], value[1]) : removeCharsAt(this.lines.subSequence(value[0], value[1]), indexes),
							Json.reader().read(this.lines.substring(value[0], value[1])), comment, comments));
				} else
					set(key.toString(), DataValue.of(parts[1][1] - parts[1][0] >= 1 && this.lines.charAt(parts[1][0]) != '"' && this.lines.charAt(parts[1][0]) != '\'' ? null : "",
							parts[1][1] - parts[1][0] >= 1 && this.lines.charAt(parts[1][0]) != '"' && this.lines.charAt(parts[1][0]) != '\'' ? null : "", comment, comments));
				comments = null;
			}
				break;
			}
		}
		if (task != -1)
			Scheduler.cancelTask(task);
		switch (readerMode) {
		case 1:
		case 3: {
			DataValue val = getOrCreate(key.toString());
			val.value = list;
		}
			break;
		case 2:
			String writtenValue = stringContainer.toString();
			DataValue val = getOrCreate(key.toString());
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

	@Override
	public boolean supportsIteratorMode() {
		return true;
	}

	@Override
	public Iterator<CharSequence> saveAsIterator(@Nonnull Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		return saveAsIteratorAs(config, markSaved, true);
	}

	protected static Iterator<CharSequence> saveAsIteratorAs(@Nonnull Config config, boolean markSaved, boolean asYaml){
		return new Iterator<CharSequence>() {
			// 0=header
			// 1=lines
			// 2=footer
			byte phase = 0;
			int posInPhase = 0;
			Iterator<CharSequence> list;

			@Override
			public CharSequence next() {
				switch (phase) {
					case 0:
						return config.getDataLoader().getHeader() instanceof List ? ((List<String>) config.getDataLoader().getHeader()).get(posInPhase++) + System.lineSeparator()
								: config.getDataLoader().getHeader().toArray(new String[0])[posInPhase++] + System.lineSeparator();
					case 1:
						return list.next();
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
							if(asYaml)
								list = YamlSectionBuilderHelper.prepareBuilder(config.getDataLoader().getPrimaryKeys(), config.getDataLoader(), markSaved);
							else
								list = TomlSectionBuilderHelper.prepareBuilder(config.getDataLoader().getPrimaryKeys(), config.getDataLoader(), markSaved);
							return hasNext();
						}
						return true;
					case 1:
						if (list.hasNext())
							return true;
						phase = 2;
						posInPhase = 0;
						return hasNext();
					case 2:
						return !config.getDataLoader().getFooter().isEmpty() && config.getDataLoader().getFooter().size() != posInPhase;
				}
				return false;
			}
		};
	}

	protected static String removeCharsAt(CharSequence value, int[] indexes) {
		for (int i = indexes.length - 1; i > -1; --i)
			((StringContainer) value).deleteCharAt(indexes[i]);
		return value.toString();
	}

	protected static int[] trim(StringContainer lines, int[] line) {
		int len = line[1] - line[0];
		int st = 0;

		while (st < len && lines.charAt(line[0] + st) <= ' ')
			st++;
		while (st < len && lines.charAt(line[0] + len - 1) <= ' ')
			len--;
		line[1] = line[0] + len;
		line[0] += st;
		return line;
	}

	protected static int[] trim(StringContainer lines, int start, int end) {
		int[] trimmed = new int[2];
		trimmed[0] = start;
		trimmed[1] = end;
		int len = trimmed[1] - trimmed[0];
		int st = 0;
		while (st < len && lines.charAt(start + st) <= ' ')
			st++;
		while (st < len && lines.charAt(start + len - 1) <= ' ')
			len--;
		trimmed[0] = start + st;
		trimmed[1] = start + len;
		return trimmed;
	}

	private int buildKey(StringContainer lines, StringContainer key, int[] currentKey, int depth, int currentDepth, int lastIndexOfDot) {
		if (currentDepth == 0)
			key.clear();
		else if (currentDepth > depth) { // Up
			key.append('.');
			lastIndexOfDot = key.length();
		} else if (currentDepth < depth) { // Down
			key.delete(key.lastIndexOf('.', lastIndexOfDot, depth - currentDepth + 1) + 1, key.length()); // Don't remove dot
			lastIndexOfDot = key.length();
		} else
			key.delete(lastIndexOfDot, key.length()); // Don't remove dot
		key.append(lines.subSequence(currentKey[0], currentKey[1]));
		return lastIndexOfDot;
	}

	protected static int[][] readConfigLine(StringContainer input, int[] index) {
		int charIndex = -1;

		for (int i = index[0]; i < index[1]; ++i)
			if (input.charAt(i) == ':') {
				charIndex = i;
				if (i + 1 < index[1] && input.charAt(i + 1) == ' ') {
					int[][] result = new int[2][];
					result[0] = getFromQuotes(input, index[0], i);
					result[1] = trim(input, i + 2, index[1]);
					return result;
				}
			}

		if (charIndex != -1) {
			int[][] result = new int[1][];
			result[0] = getFromQuotes(input, index[0], charIndex);
			return result;
		}
		return null;
	}

	protected static int[] getFromQuotes(StringContainer input, int start, int end) {
		int len = end - start;
		if (len <= 2)
			return new int[] { start, end };
		char firstChar = input.charAt(start);
		char lastChar = input.charAt(end-1);
		if (firstChar == '\'' && lastChar == '\'' || firstChar == '"' && lastChar == '"')
			return new int[] { start + 1, end - 1 };
		return new int[] { start, end };
	}

	protected static int[] getFromQuotes(StringContainer input, int[] line) {
		int len = line[1] - line[0];
		if (len <= 1)
			return line;
		char firstChar = input.charAt(line[0]);
		char lastChar = input.charAt(line[1] - 1);
		if (firstChar == '\'' && lastChar == '\'' || firstChar == '"' && lastChar == '"') {
			line[0] += 1;
			line[1] -= 1;
		}
		return line;
	}

	protected static Object splitFromComment(StringContainer lines, int posFromStart, int[] container) {
		if (container[1] - container[0] <= 1)
			return new int[][] { container };

		char firstChar = lines.charAt(container[0] + posFromStart);
		if (firstChar == '[' || firstChar == '{')
			return splitFromCommentJson(lines, posFromStart, container);

		boolean inQuotes = firstChar == '"' || firstChar == '\'';
		int endOfString = -1;
		int splitIndexStart = 0;
		boolean foundHash = false;

		posFromStart += inQuotes ? 1 : 0;
		container[0] += posFromStart;
		int length = 0;
		int[] indexes = null;
		for (int i = container[0]; i < container[1]; i++) {
			char c = lines.charAt(i);
			if (inQuotes) {
				if (c == firstChar) {
					inQuotes = false;
					endOfString = i;
				} else if (c == '\\' && i + 1 < container[1] && lines.charAt(i + 1) == firstChar) {
					if (indexes == null) {
						length = container[1] - container[0];
						indexes = new int[] { length - (container[1] - i) };
					} else {
						int[] copy = new int[indexes.length + 1];
						System.arraycopy(indexes, 0, copy, 0, indexes.length);
						copy[indexes.length] = length - (container[1] - i);
						indexes = copy;
					}
					++i;
				}
			} else if (c == '#' && !foundHash) {
				splitIndexStart = i;
				foundHash = true;
			}
		}
		int[][] result;
		if (!foundHash)
			result = endOfString == -1 ? new int[][] { container } : new int[][] { new int[] { container[0], endOfString } };
		else
			result = new int[][] { endOfString == -1 && splitIndexStart == 0 ? container : endOfString == -1 ? new int[] { container[0], splitIndexStart } : new int[] { container[0], endOfString },
					new int[] { splitIndexStart, container[1] } };
		return indexes != null ? Pair.of(indexes, result) : result;
	}

	private static int[][] splitFromCommentJson(StringContainer lines, int posFromStart, int[] input) {
		int i = input[0] + posFromStart;
		int braceCount = 0;
		int bracketCount = 0;
		boolean inQuotes = false;

		while (i < input[1]) {
			char c = lines.charAt(i);
			if (c == '\\' && i + 1 < input[1] && isSkippableChar(lines.charAt(i + 1)))
				++i;
			else if (!inQuotes)
				if (c == '{')
					braceCount++;
				else if (c == '}')
					braceCount--;
				else if (c == '[')
					bracketCount++;
				else if (c == ']')
					bracketCount--;
				else if (c == '#' && braceCount == 0 && bracketCount == 0)
					break;
				else if (c == '"' || c == '\'')
					inQuotes = true;
			i++;
		}

		int[] trimmedValue = trim(lines, input[0] + posFromStart, i);
		if (i < input[1]) {
			int[][] result = new int[2][];
			result[0] = trimmedValue;
			result[1] = new int[]{i, input[1]};
			return result;
		}
		int[][] result = new int[1][];
		result[0]=trimmedValue;
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

	private static int getDepth(StringContainer lines, int[] index) {
		int depth = 0;
		char c;
		for (int startAt = index[0]; startAt < index[1] && (c = lines.charAt(startAt)) <= ' '; ++startAt)
			if (c == ' ' || c == '	')
				++depth;
		return depth / 2;
	}

	@Override
	public String name() {
		return "yaml";
	}
}
