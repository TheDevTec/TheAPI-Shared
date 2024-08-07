package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.devtec.shared.Pair;
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
	private StringContainer lines;

	private final int[] readLine() {
		try {
			return startIndex == -1 ? null : endIndex == -1 ? new int[] { startIndex, lines.length() } : new int[] { startIndex, endIndex };
		} finally {
			startIndex = endIndex == -1 ? -1 : endIndex + 1;
			endIndex = -1;
			if (startIndex != -1)
				for (int i = startIndex; i < lines.length(); ++i) {
					char c = lines.charAt(i);
					if (c == '\r' || c == '\n') {
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

	@SuppressWarnings({ "unchecked" })
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
				if (i == startIndex) {
					++startIndex;
					continue;
				}
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

		List<String> comments = null;
		List<Map<Object, Object>> list = null;
		Map<Object, Object> map = null;
		int mode = 0;
		StringContainer mainPath = null;

		while (!readingReachedEnd || !lines.isEmpty()) {
			if (lines.isEmpty())
				continue;
			int[] line = lines.poll();
			int[] trimmed = YamlLoader.trim(this.lines, line);
			// Comments
			if (trimmed[0] == trimmed[1] || this.lines.charAt(trimmed[0]) == '#') {
				if (comments == null)
					comments = new ArrayList<>();
				comments.add(trimmed[0] == trimmed[1] ? "" : this.lines.substring(trimmed[0], trimmed[1]));
				continue;
			}
			int[][] parts = readConfigLine(this.lines, trimmed);
			if (parts == null) { // Didn't find = symbol.. Maybe this is YAML file.
				data.clear();
				comments = null;
				break;
			}

			if (parts.length == 1) {
				if (comments != null)
					if (mode == 0 || mode != 1)
						set(this.lines.substring(parts[0][0], parts[0][1]), DataValue.of(null, "", null, comments));
					else {
						CharSequence seq = this.lines.subSequence(parts[0][0], parts[0][1]);
						mainPath.append('.').append(seq);
						set(mainPath.toString(), DataValue.of(null, "", null, comments));
						mainPath.delete(mainPath.length() - seq.length() - 1, mainPath.length());
					}
				continue;
			}

			if (parts.length == 3) { // section or array with maps
				mainPath = (StringContainer) this.lines.subSequence(parts[0][0], parts[0][1]);
				mode = parts[2][0];
				String inString = mainPath.toString();
				DataValue probablyCreated = get(inString);
				if (mode != 2 && probablyCreated == null && comments != null)
					set(inString, DataValue.of(null, null, null, comments));
				map = null;
				list = null;

				if (mode == 2) {
					list = probablyCreated != null && probablyCreated.value != null ? (List<Map<Object, Object>>) probablyCreated.value : new ArrayList<>();
					if (probablyCreated == null || probablyCreated.value == null)
						set(inString, DataValue.of(null, list, null, comments));
					map = new HashMap<>();
					list.add(map);
				}
				comments = null;
				continue;
			}
			Object readerValueParsed = YamlLoader.splitFromComment(this.lines, 0, parts[1]);
			int[][] readerValue = readerValueParsed instanceof Pair ? (int[][]) ((Pair) readerValueParsed).getValue() : (int[][]) readerValueParsed;
			int[] value = readerValue[0];
			int[] indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
			String comment = readerValue.length == 1 ? null : this.lines.substring(readerValue[1][0], readerValue[1][1]);

			if (mode == 0 || mode != 1)
				set(this.lines.substring(parts[0][0], parts[0][1]),
						DataValue.of(indexes == null ? this.lines.substring(value[0], value[1]) : YamlLoader.removeCharsAt(this.lines.subSequence(value[0], value[1]), indexes),
								Json.reader().read(this.lines.substring(value[0], value[1])), comment, comments));
			else {
				CharSequence seq = this.lines.subSequence(parts[0][0], parts[0][1]);
				mainPath.append('.').append(seq);
				set(mainPath.toString(), DataValue.of(indexes == null ? this.lines.substring(value[0], value[1]) : YamlLoader.removeCharsAt(this.lines.subSequence(value[0], value[1]), indexes),
						Json.reader().read(this.lines.substring(value[0], value[1])), comment, comments));
				mainPath.delete(mainPath.length() - seq.length() - 1, mainPath.length());
			}
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
		Iterator<CharSequence> itr = saveAsIterator(config, markSaved);
		while (itr.hasNext())
			builder.append(itr.next());
		return builder;
	}

	@Override
	public boolean supportsIteratorMode() {
		return true;
	}

	@Override
	public Iterator<CharSequence> saveAsIterator(@Nonnull Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
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
					if (config.getDataLoader().getFooter().isEmpty() || config.getDataLoader().getFooter().size() == posInPhase)
						return false;
					return true;
				}
				return false;
			}
		};
	}

	protected static int[][] readConfigLine(StringContainer input, int[] index) {
		int quoteCount = 0;
		char currentQueto = 0;

		for (int i = index[0]; i < index[1]; ++i) {
			char c = input.charAt(i);
			switch (c) {
			case ':':
				if (quoteCount != 0)
					break;
				if (i + 1 < index[1] && input.charAt(i + 1) == ' ')
					return null; // Hey! This is YAML file.
				break;
			case '=':
				if (quoteCount != 0)
					break;
				if (i == index[0])
					return null; // Invalid TOML file.
				int[][] result = new int[2][];
				result[0] = YamlLoader.getFromQuotes(input, YamlLoader.trim(input, index[0], i));
				result[1] = YamlLoader.trim(input, i + 1, index[1]);
				return result;
			case '[':
				if (quoteCount != 0)
					break;
				if (index[1] > i + 1 && input.charAt(i + 1) == '[') {
					int mapEndIndex = input.lastIndexOf(']');
					if (mapEndIndex != -1 && mapEndIndex - 1 > i && input.charAt(mapEndIndex - 1) == ']')
						return new int[][] { YamlLoader.getFromQuotes(input, index[0] + 2, index[1] - 2), null, new int[] { 2 } }; // maps
				}
				int mapEndIndex = input.lastIndexOf(']');
				if (mapEndIndex != -1)
					return new int[][] { YamlLoader.getFromQuotes(input, index[0] + 1, index[1] - 1), null, new int[] { 1 } }; // sub keys
				break;
			case '\\':
				++i;
				break;
			case '"':
			case '\'':
				if (quoteCount == 0) {
					++quoteCount;
					currentQueto = c;
				} else if (c == currentQueto)
					--quoteCount;
				break;
			default:
				break;
			}
		}
		int[][] result = new int[1][];
		result[0] = YamlLoader.getFromQuotes(input, YamlLoader.trim(input, index[0], index[1]));
		return result;
	}

	@Override
	public String name() {
		return "toml";
	}
}
