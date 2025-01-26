package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import me.devtec.shared.Pair;
import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.loaders.toml.TomlSectionBuilderHelper;
import me.devtec.shared.dataholder.loaders.yaml.YamlSectionBuilderHelper;
import me.devtec.shared.json.Json;

public class YamlLoader extends EmptyLoader {

	static final byte READ_SECTION = 0;
	static final byte READ_LIST = 1;
	static final byte READ_SIMPLE_STRING = 2;
	static final byte READ_MAP_LIST = 3;

	private StringContainer lines;

	@SuppressWarnings("unchecked")
	@Override
	public void load(StringContainer container, List<int[]> input) {
		reset();

		lines = container;

		// Temp values
		List<String> comments = null;
		StringContainer stringContainer = null;
		StringContainer key = new StringContainer(48);
		int depth = 0;
		int lastIndexOfDot = 0;

		byte readerMode = READ_SECTION;

		// Temporary arrays to avoid repeated allocations
		int[] trimmed = new int[2];
		int[][] parts = new int[2][];
		int[] value = new int[2];
		int[] indexes = null;

		for (int pos = 0; pos < input.size(); ++pos) {
			int[] line = input.get(pos);
			int currentDepth = getDepth(line);
			trim(line, trimmed);
			// Comments
			if (trimmed[0] == trimmed[1] || lines.charAt(trimmed[0]) == '#') {
				if (comments == null)
					comments = new ArrayList<>();
				comments.add(trimmed[0] == trimmed[1] ? "" : lines.substring(trimmed[0], trimmed[1]));
				continue;
			}

			switch (readerMode) {
			case READ_SECTION: {
				parts = readConfigLine(lines, trimmed);
				if (parts == null)
					continue;

				// Key
				lastIndexOfDot = buildKey(lines, key, parts[0], depth, currentDepth, lastIndexOfDot);
				depth = currentDepth;

				if (parts.length == 1) {
					readerMode = READ_LIST;
					if (comments != null) {
						DataValue val = getOrCreate(key.toString());
						val.comments = comments;
						comments = null;
					}
					continue;
				}

				// Value
				Object readerValueParsed = splitFromComment(lines, 0, parts[1]);
				int[][] readerValue = readerValueParsed instanceof Pair
						? (int[][]) ((Pair) readerValueParsed).getValue()
						: (int[][]) readerValueParsed;
				value = readerValue[0];
				indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
				String comment = readerValue.length == 1 || readerValue[1] == null ? null
						: lines.substring(readerValue[1][0], readerValue[1][1]);
				if (value[1] - value[0] > 0) {
					if (value[1] - value[0] == 1 && parts[1][1] - parts[1][0] == 1 && lines.charAt(value[0]) == '|') {
						readerMode = READ_SIMPLE_STRING;
						set(key.toString(), DataValue.of(null, "|", comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					set(key.toString(),
							DataValue.of(
									indexes == null ? lines.substring(value[0], value[1])
											: removeCharsAt(lines.subSequence(value[0], value[1]), indexes).toString(),
									Json.reader().read(lines.substring(value[0], value[1])), comment, comments));
				} else
					set(key.toString(),
							DataValue.of(
									parts[1][1] - parts[1][0] >= 1 && lines.charAt(parts[1][0]) != '"'
											&& lines.charAt(parts[1][0]) != '\'' ? null : "",
									parts[1][1] - parts[1][0] >= 1 && lines.charAt(parts[1][0]) != '"'
											&& lines.charAt(parts[1][0]) != '\'' ? null : "",
									comment, comments));
				comments = null;
			}
				break;
			case READ_LIST: {
				if (isList(trimmed, trimmed, false)) {
					Pair pair = readMapList(pos, input, trimmed, parts, value, indexes);
					pos = (int) pair.getKey();
					List<Object> list = (List<Object>) pair.getValue();
					DataValue val = getOrCreate(key.toString());
					val.value = list;
					continue;
				}
				parts = readConfigLine(lines, trimmed);
				if (parts == null)
					continue;

				readerMode = READ_SECTION;

				lastIndexOfDot = buildKey(lines, key, parts[0], depth, currentDepth, lastIndexOfDot);
				depth = currentDepth;
				if (parts.length == 1) {
					readerMode = READ_LIST;
					if (comments != null) {
						DataValue val = getOrCreate(key.toString());
						val.comments = comments;
						comments = null;
					}
					continue;
				}
				// Value
				Object readerValueParsed = splitFromComment(lines, 0, parts[1]);
				int[][] readerValue = readerValueParsed instanceof Pair
						? (int[][]) ((Pair) readerValueParsed).getValue()
						: (int[][]) readerValueParsed;
				value = readerValue[0];
				indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
				String comment = readerValue.length == 1 || readerValue[1] == null ? null
						: lines.substring(readerValue[1][0], readerValue[1][1]);
				if (value[1] - value[0] > 0) {
					if (value[1] - value[0] == 1 && parts[1][1] - parts[1][0] == 1 && lines.charAt(value[0]) == '|') {
						readerMode = READ_SIMPLE_STRING;
						set(key.toString(), DataValue.of(null, "|", comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					set(key.toString(),
							DataValue.of(
									indexes == null ? lines.substring(value[0], value[1])
											: removeCharsAt(lines.subSequence(value[0], value[1]), indexes).toString(),
									Json.reader().read(lines.substring(value[0], value[1])), comment, comments));
				} else
					set(key.toString(),
							DataValue.of(
									parts[1][1] - parts[1][0] >= 1 && lines.charAt(parts[1][0]) != '"'
											&& lines.charAt(parts[1][0]) != '\'' ? null : "",
									parts[1][1] - parts[1][0] >= 1 && lines.charAt(parts[1][0]) != '"'
											&& lines.charAt(parts[1][0]) != '\'' ? null : "",
									comment, comments));
				comments = null;
			}
				break;
			case READ_SIMPLE_STRING: {
				parts = readConfigLine(lines, trimmed);
				if (parts == null) {
					Object readerValueParsed = splitFromComment(lines, 0, trimmed);
					int[][] readerValue = readerValueParsed instanceof Pair
							? (int[][]) ((Pair) readerValueParsed).getValue()
							: (int[][]) readerValueParsed;
					indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
					stringContainer.append(indexes == null ? lines.substring(readerValue[0][0], readerValue[0][1])
							: removeCharsAt(lines.subSequence(readerValue[0][0], readerValue[0][1]), indexes));
					continue;
				}

				readerMode = READ_SECTION;

				if (stringContainer != null) {
					String writtenValue = stringContainer.toString();
					DataValue val = getOrCreate(key.toString());
					val.value = writtenValue;
					val.writtenValue = writtenValue;
				}

				lastIndexOfDot = buildKey(lines, key, parts[0], depth, currentDepth, lastIndexOfDot);
				depth = currentDepth;
				if (parts.length == 1) {
					readerMode = READ_LIST; // List or Section to break
					if (comments != null) {
						DataValue val = getOrCreate(key.toString());
						val.comments = comments;
						comments = null;
					}
					continue;
				}
				// Value
				Object readerValueParsed = splitFromComment(lines, 0, parts[1]);
				int[][] readerValue = readerValueParsed instanceof Pair
						? (int[][]) ((Pair) readerValueParsed).getValue()
						: (int[][]) readerValueParsed;
				value = readerValue[0];
				indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
				String comment = readerValue.length == 1 || readerValue[1] == null ? null
						: lines.substring(readerValue[1][0], readerValue[1][1]);
				if (value[1] - value[0] > 0) {
					if (value[1] - value[0] == 1 && parts[1][1] - parts[1][0] == 1 && lines.charAt(value[0]) == '|') {
						readerMode = READ_SIMPLE_STRING;
						set(key.toString(), DataValue.of(null, "|", comment, comments));
						stringContainer = new StringContainer(64);
						comments = null;
						continue;
					}
					set(key.toString(),
							DataValue.of(
									indexes == null ? lines.substring(value[0], value[1])
											: removeCharsAt(lines.subSequence(value[0], value[1]), indexes).toString(),
									Json.reader().read(lines.substring(value[0], value[1])), comment, comments));
				} else
					set(key.toString(),
							DataValue.of(
									parts[1][1] - parts[1][0] >= 1 && lines.charAt(parts[1][0]) != '"'
											&& lines.charAt(parts[1][0]) != '\'' ? null : "",
									parts[1][1] - parts[1][0] >= 1 && lines.charAt(parts[1][0]) != '"'
											&& lines.charAt(parts[1][0]) != '\'' ? null : "",
									comment, comments));
				comments = null;
			}
				break;
			}
		}
		switch (readerMode) {
		case READ_SIMPLE_STRING:
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

	/**
	 *
	 * bez hodnoty -> možná list/map pokud v listu je yaml sekce a není v ", tak
	 * load mapy -> odeberu první -, return bude list s mapy, mapa bude mít konec na
	 * začátku jiné / main sekce mapy / celého filu
	 *
	 * pokud mezera bude jiná -> break pokud hodnota v listu nebude mapa -> udělat z
	 * toho Object list?
	 *
	 */

	private Pair readMapList(int pos, List<int[]> input, int[] trimmed, int[][] parts, int[] value, int[] indexes) {
		int listDepth = 0;
		int sectionDepth = 0;
		List<Object> list = new ArrayList<>();

		// Temp values
		Map<Object, Object> map = null;
		Map<Object, Object> originMap = null;
		StringContainer stringContainer = null;
		String key = null;
		Stack<Map<Object, Object>> stack = null;

		byte readerMode = READ_LIST;

		for (; pos < input.size(); ++pos) {
			int[] line = input.get(pos);
			int currentDepth = getDepth(line);
			trim(line, trimmed);
			// Comments
			if (trimmed[0] == trimmed[1] || lines.charAt(trimmed[0]) == '#')
				continue;

			if (currentDepth < listDepth)
				break;

			if (readerMode == READ_MAP_LIST && currentDepth == listDepth)
				if (lines.charAt(trimmed[0]) == '-' && lines.charAt(trimmed[0] + 1) == ' ') {
					readerMode = READ_LIST;
					map = null;
					stack = null;
					originMap = null;
					key = null;
				} else
					break;

			switch (readerMode) {
			case READ_LIST: {
				// If value size is above 2 chars & starts with "- "
				if (isList(trimmed, trimmed, false)) {
					listDepth = currentDepth;

					char firstChar = lines.charAt(trimmed[0] + 2);
					boolean notInQueto = firstChar != '\'' && firstChar != '"';
					Object readerValueParsed = splitFromComment(lines, 2, trimmed);
					int[][] readerValue = readerValueParsed instanceof Pair
							? (int[][]) ((Pair) readerValueParsed).getValue()
							: (int[][]) readerValueParsed;
					indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
					StringContainer valueString = indexes == null
							? (StringContainer) lines.subSequence(readerValue[0][0], readerValue[0][1])
							: removeCharsAt(lines.subSequence(readerValue[0][0], readerValue[0][1]), indexes);
					parts = notInQueto ? readConfigLine(valueString, null) : null;
					if (parts != null && parts.length > 1) {
						if (map == null)
							map = new HashMap<>();
						// Value
						readerValueParsed = splitFromComment(valueString, 0, parts[1]);
						readerValue = readerValueParsed instanceof Pair
								? (int[][]) ((Pair) readerValueParsed).getValue()
								: (int[][]) readerValueParsed;
						value = readerValue[0];
						indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey()
								: null;
						if (value[1] - value[0] > 0)
							map.put(valueString.substring(parts[0][0], parts[0][1]),
									Json.reader().read(valueString.substring(value[0], value[1])));
						else
							map.put(valueString.substring(parts[0][0], parts[0][1]),
									parts[1][1] - parts[1][0] >= 1 && valueString.charAt(parts[1][0]) != '"'
											&& valueString.charAt(parts[1][0]) != '\'' ? null : "");
						list.add(map);
						readerMode = READ_MAP_LIST;
						sectionDepth = currentDepth + 1;
					} else
						list.add(Json.reader().read(valueString.toString()));
					break;
				}
				parts = readConfigLine(lines, trimmed);
				if (parts == null) {
					Object readerValueParsed = splitFromComment(lines, 0, trimmed);
					int[][] readerValue = readerValueParsed instanceof Pair
							? (int[][]) ((Pair) readerValueParsed).getValue()
							: (int[][]) readerValueParsed;
					indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
					stringContainer.append(indexes == null ? lines.substring(readerValue[0][0], readerValue[0][1])
							: removeCharsAt(lines.subSequence(readerValue[0][0], readerValue[0][1]), indexes));
					break;
				}

				readerMode = READ_LIST;

				if (map == null)
					map = new HashMap<>();
				if (list != null && key != null) {
					map.put(key, list);
					list = null;
				}

				key = lines.substring(parts[0][0], parts[0][1]);
				if (parts.length == 1) {
					if (isList(input.get(pos + 1), trimmed, true)) {
						Pair pair = readMapList(pos + 1, input, trimmed, parts, value, indexes);
						pos = (int) pair.getKey() - 1;
						@SuppressWarnings("unchecked")
						List<Object> listVal = (List<Object>) pair.getValue();
						map.put(key, listVal);
						continue;
					}
					if (stack == null) {
						stack = new Stack<>();
						originMap = map;
					} else if (currentDepth == sectionDepth) {
						map = originMap;
						stack.clear();
					} else
						for (int i = 0; i < currentDepth - sectionDepth - 1; ++i)
							map = stack.pop();
					map.put(key, map = new HashMap<>());
					stack.add(map);
					break;
				}

				if (stack != null)
					if (currentDepth == sectionDepth) {
						map = originMap;
						stack.clear();
					} else
						for (int i = 0; i < currentDepth - sectionDepth - 1; ++i)
							map = stack.pop();

				// Value
				Object readerValueParsed = splitFromComment(lines, 0, parts[1]);
				int[][] readerValue = readerValueParsed instanceof Pair
						? (int[][]) ((Pair) readerValueParsed).getValue()
						: (int[][]) readerValueParsed;
				value = readerValue[0];
				if (value[1] - value[0] > 0) {
					if (value[1] - value[0] == 1 && parts[1][1] - parts[1][0] == 1 && lines.charAt(value[0]) == '|') {
						readerMode = READ_SIMPLE_STRING;
						map.put(key, null);
						stringContainer = new StringContainer(64);
						break;
					}
					map.put(key, Json.reader().read(lines.substring(value[0], value[1])));
				} else
					map.put(key, parts[1][1] - parts[1][0] >= 1 && lines.charAt(parts[1][0]) != '"'
							&& lines.charAt(parts[1][0]) != '\'' ? null : "");
			}
				break;
			case READ_SIMPLE_STRING: {
				parts = readConfigLine(lines, trimmed);
				if (parts == null) {
					Object readerValueParsed = splitFromComment(lines, 0, trimmed);
					int[][] readerValue = readerValueParsed instanceof Pair
							? (int[][]) ((Pair) readerValueParsed).getValue()
							: (int[][]) readerValueParsed;
					indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
					stringContainer.append(indexes == null ? lines.substring(readerValue[0][0], readerValue[0][1])
							: removeCharsAt(lines.subSequence(readerValue[0][0], readerValue[0][1]), indexes));
					break;
				}

				readerMode = READ_LIST;

				if (map == null)
					map = new HashMap<>();
				if (stringContainer != null && key != null)
					map.put(key, stringContainer.toString());

				key = lines.substring(parts[0][0], parts[0][1]);
				if (parts.length == 1) {
					if (isList(input.get(pos + 1), trimmed, true)) {
						Pair pair = readMapList(pos + 1, input, trimmed, parts, value, indexes);
						pos = (int) pair.getKey() - 1;
						@SuppressWarnings("unchecked")
						List<Object> listVal = (List<Object>) pair.getValue();
						map.put(key, listVal);
						continue;
					}
					if (stack == null) {
						stack = new Stack<>();
						originMap = map;
					} else if (currentDepth == sectionDepth) {
						map = originMap;
						stack.clear();
					} else
						for (int i = 0; i < currentDepth - sectionDepth; ++i)
							map = stack.pop();
					map.put(key, map = new HashMap<>());
					stack.add(map);
					break;
				}

				if (stack != null)
					if (currentDepth == sectionDepth) {
						map = originMap;
						stack.clear();
					} else
						for (int i = 0; i < currentDepth - sectionDepth; ++i)
							map = stack.pop();

				// Value
				Object readerValueParsed = splitFromComment(lines, 0, parts[1]);
				int[][] readerValue = readerValueParsed instanceof Pair
						? (int[][]) ((Pair) readerValueParsed).getValue()
						: (int[][]) readerValueParsed;
				value = readerValue[0];
				if (value[1] - value[0] > 0) {
					if (value[1] - value[0] == 1 && parts[1][1] - parts[1][0] == 1 && lines.charAt(value[0]) == '|') {
						readerMode = READ_SIMPLE_STRING;
						map.put(key, null);
						stringContainer = new StringContainer(64);
						break;
					}
					map.put(key, Json.reader().read(lines.substring(value[0], value[1])));
				} else
					map.put(key.toString(), parts[1][1] - parts[1][0] >= 1 && lines.charAt(parts[1][0]) != '"'
							&& lines.charAt(parts[1][0]) != '\'' ? null : "");
			}
				break;
			case READ_MAP_LIST: {
				parts = readConfigLine(lines, trimmed);
				if (parts == null) {
					Object readerValueParsed = splitFromComment(lines, 0, trimmed);
					int[][] readerValue = readerValueParsed instanceof Pair
							? (int[][]) ((Pair) readerValueParsed).getValue()
							: (int[][]) readerValueParsed;
					indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
					stringContainer.append(indexes == null ? lines.substring(readerValue[0][0], readerValue[0][1])
							: removeCharsAt(lines.subSequence(readerValue[0][0], readerValue[0][1]), indexes));
					break;
				}
				key = lines.substring(parts[0][0], parts[0][1]);
				if (parts.length == 1) {
					if (isList(input.get(pos + 1), trimmed, true)) {
						Pair pair = readMapList(pos + 1, input, trimmed, parts, value, indexes);
						pos = (int) pair.getKey() - 1;
						@SuppressWarnings("unchecked")
						List<Object> listVal = (List<Object>) pair.getValue();
						map.put(key, listVal);
						break;
					}
					if (stack == null) {
						stack = new Stack<>();
						originMap = map;
					} else if (sectionDepth == currentDepth) {
						stack.clear();
						map = originMap;
					} else {
						int minusCount = currentDepth - sectionDepth - stack.size();
						if (minusCount != 0)
							for (int i = 0; i < minusCount * -1; ++i)
								map = stack.pop();
					}
					map.put(key, map = new HashMap<>());
					stack.add(map);
					break;
				}

				if (stack != null)
					if (sectionDepth == currentDepth) {
						stack.clear();
						map = originMap;
					} else {
						int minusCount = currentDepth - sectionDepth - stack.size();
						if (minusCount != 0)
							for (int i = 0; i < minusCount * -1; ++i)
								map = stack.pop();
					}

				// Value
				Object readerValueParsed = splitFromComment(lines, 0, parts[1]);
				int[][] readerValue = readerValueParsed instanceof Pair
						? (int[][]) ((Pair) readerValueParsed).getValue()
						: (int[][]) readerValueParsed;
				value = readerValue[0];
				if (value[1] - value[0] > 0)
					map.put(key, Json.reader().read(lines.substring(value[0], value[1])));
				else
					map.put(key, parts[1][1] - parts[1][0] >= 1 && lines.charAt(parts[1][0]) != '"'
							&& lines.charAt(parts[1][0]) != '\'' ? null : "");
			}
				break;
			}
		}
		return Pair.of(pos, list);
	}

	private boolean isList(int[] line, int[] trimmed, boolean trim) {
		if (trim)
			trim(line, trimmed);
		return trimmed[1] - trimmed[0] > 2 && lines.charAt(trimmed[0]) == '-' && lines.charAt(trimmed[0] + 1) == ' ';
	}

	@Override
	public void load(String input) {
		if (input == null)
			return;
		StringContainer container = new StringContainer(input, 0, 0);
		load(container, LoaderReadUtil.readLinesFromContainer(container));
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

	protected static Iterator<CharSequence> saveAsIteratorAs(@Nonnull Config config, boolean markSaved,
			boolean asYaml) {
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
					return config.getDataLoader().getHeader() instanceof List
							? ((List<String>) config.getDataLoader().getHeader()).get(posInPhase++)
									+ System.lineSeparator()
							: config.getDataLoader().getHeader().toArray(new String[0])[posInPhase++]
									+ System.lineSeparator();
				case 1:
					return list.next();
				case 2:
					return config.getDataLoader().getFooter() instanceof List
							? ((List<String>) config.getDataLoader().getFooter()).get(posInPhase++)
									+ System.lineSeparator()
							: config.getDataLoader().getFooter().toArray(new String[0])[posInPhase++]
									+ System.lineSeparator();
				}
				return null;
			}

			@Override
			public boolean hasNext() {
				switch (phase) {
				case 0:
					if (config.getDataLoader().getHeader().isEmpty()
							|| config.getDataLoader().getHeader().size() == posInPhase) {
						phase = 1;
						posInPhase = 0;
						if (asYaml)
							list = YamlSectionBuilderHelper.prepareBuilder(config.getDataLoader().getPrimaryKeys(),
									config.getDataLoader(), markSaved);
						else
							list = TomlSectionBuilderHelper.prepareBuilder(config.getDataLoader().getPrimaryKeys(),
									config.getDataLoader(), markSaved);
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
					return !config.getDataLoader().getFooter().isEmpty()
							&& config.getDataLoader().getFooter().size() != posInPhase;
				}
				return false;
			}
		};
	}

	protected static StringContainer removeCharsAt(CharSequence value, int[] indexes) {
		for (int i = indexes.length - 1; i > -1; --i)
			((StringContainer) value).deleteCharAt(indexes[i]);
		return (StringContainer) value;
	}

	private void trim(int[] line, int[] trimmed) {
		int len = line[1] - line[0];
		int st = 0;
		while (st < len && lines.charAt(line[0] + st) <= ' ')
			st++;
		while (st < len && lines.charAt(line[0] + len - 1) <= ' ')
			len--;
		trimmed[0] = line[0] + st;
		trimmed[1] = line[0] + len;
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

	private int buildKey(StringContainer lines, StringContainer key, int[] currentKey, int depth, int currentDepth,
			int lastIndexOfDot) {
		if (currentDepth == 0)
			key.clear();
		else if (currentDepth > depth) { // Up
			key.append('.');
			lastIndexOfDot = key.length();
		} else if (currentDepth < depth) { // Down
			key.delete(key.lastIndexOf('.', lastIndexOfDot, depth - currentDepth + 1) + 1, key.length()); // Don't
																											// remove
																											// dot
			lastIndexOfDot = key.length();
		} else
			key.delete(lastIndexOfDot, key.length()); // Don't remove dot
		key.append(lines.subSequence(currentKey[0], currentKey[1]));
		return lastIndexOfDot;
	}

	protected static int[][] readConfigLine(StringContainer input, int[] index) {
		int charIndex = -1;

		int start = index == null ? 0 : index[0];
		int end = index == null ? input.length() : index[1];

		for (int i = start; i < end; ++i)
			if (input.charAt(i) == ':') {
				charIndex = i;
				if (i + 1 < end && input.charAt(i + 1) == ' ') {
					int[][] result = new int[2][];
					result[0] = getFromQuotes(input, start, i);
					result[1] = trim(input, i + 2, end);
					return result;
				}
			}

		if (charIndex != -1) {
			int[][] result = new int[1][];
			result[0] = getFromQuotes(input, start, charIndex);
			return result;
		}
		return null;
	}

	protected static int[] getFromQuotes(StringContainer input, int start, int end) {
		int len = end - start;
		if (len <= 2)
			return new int[] { start, end };
		char firstChar = input.charAt(start);
		char lastChar = input.charAt(end - 1);
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
			result = endOfString == -1 ? new int[][] { container }
					: new int[][] { new int[] { container[0], endOfString } };
		else
			result = new int[][] {
					endOfString == -1 && splitIndexStart == 0 ? container
							: endOfString == -1 ? new int[] { container[0], splitIndexStart }
									: new int[] { container[0], endOfString },
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
			result[1] = new int[] { i, input[1] };
			return result;
		}
		int[][] result = new int[1][];
		result[0] = trimmedValue;
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

	private int getDepth(int[] line) {
		int depth = 0;
		for (int i = line[0]; i < line[1] && lines.charAt(i) <= ' '; i++)
			if (lines.charAt(i) == ' ' || lines.charAt(i) == '\t')
				depth++;
		return depth / 2;
	}

	@Override
	public boolean supportsReadingLines() {
		return true;
	}

	@Override
	public String name() {
		return "yaml";
	}
}
