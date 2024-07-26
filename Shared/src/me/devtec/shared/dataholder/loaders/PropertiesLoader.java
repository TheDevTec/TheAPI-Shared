package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import me.devtec.shared.Pair;
import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;
import me.devtec.shared.scheduler.Scheduler;
import me.devtec.shared.scheduler.Tasker;

public class PropertiesLoader extends EmptyLoader {

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

		while (!readingReachedEnd || !lines.isEmpty()) {
			if (lines.isEmpty())
				continue;

			int[] line = lines.poll();

			if (this.lines.charAt(line[0]) == '\r' ? this.lines.charAt(line[0] + 1) == ' ' : this.lines.charAt(line[0]) == ' ') { // S-s-space?! Maybe.. this is YAML file.
				data.clear();
				comments = null;
				break;
			}

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
				if (comments != null) {
					DataValue val = getOrCreate(this.lines.substring(parts[0][0], parts[0][1]));
					val.comments = comments;
					val.value = "";
					comments = null;
				}
				continue;
			}

			Object readerValueParsed = YamlLoader.splitFromComment(this.lines, 0, parts[1]);
			int[][] readerValue = readerValueParsed instanceof Pair ? (int[][]) ((Pair) readerValueParsed).getValue() : (int[][]) readerValueParsed;
			int[] value = readerValue[0];
			int[] indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
			String comment = readerValue.length == 1 ? null : this.lines.substring(readerValue[1][0], readerValue[1][1]);
			set(this.lines.substring(parts[0][0], parts[0][1]),
					DataValue.of(indexes == null ? this.lines.substring(value[0], value[1]) : YamlLoader.removeCharsAt(this.lines.subSequence(value[0], value[1]), indexes),
							Json.reader().read(this.lines.substring(value[0], value[1])), comment, comments));
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
		try {
			for (String h : config.getDataLoader().getHeader())
				builder.append(h).append(System.lineSeparator());
		} catch (Exception er) {
			er.printStackTrace();
		}
		boolean first = true;
		for (Entry<String, DataValue> key : config.getDataLoader().entrySet()) {
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

	protected static int[][] readConfigLine(StringContainer input, int[] index) {
		boolean foundYamlIndexChar = false;
		for (int i = index[0]; i < index[1]; ++i) {
			char c = input.charAt(i);
			switch (c) {
			case ':':
				foundYamlIndexChar = true;
				break;
			case '=':
				if (i == index[0])
					return null; // Invalid PROPERTIES file.
				int[][] result = new int[2][];
				result[0] = YamlLoader.getFromQuotes(input, YamlLoader.trim(input, index[0], i));
				result[1] = YamlLoader.trim(input, i + 1, index[1]);
				return result;
			default:
				break;
			}
		}
		if (foundYamlIndexChar)
			return null; // Hey! This is YAML file.
		int[][] result = new int[1][];
		result[0] = YamlLoader.getFromQuotes(input, YamlLoader.trim(input, index[0], index[1]));
		return result;
	}

	@Override
	public String name() {
		return "properties";
	}
}
