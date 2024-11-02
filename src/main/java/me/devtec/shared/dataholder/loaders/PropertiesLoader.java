package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import me.devtec.shared.API;
import me.devtec.shared.Pair;
import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class PropertiesLoader extends EmptyLoader {

	private static final int CHUNK_SIZE = 1024 * 16;

	private StringContainer lines;

	private List<int[]> readLinesFromContainer(StringContainer lines) {
		List<Future<List<int[]>>> futures = new ArrayList<>();
		List<int[]> allLinePositions = new ArrayList<>();

		int totalLength = lines.length();
		int chunkCount = (totalLength + CHUNK_SIZE - 1) / CHUNK_SIZE;
		int lastEnd = 0;

		for (int i = 0; i < chunkCount; i++) {
			int start = i * CHUNK_SIZE;
			int end = Math.min(start + CHUNK_SIZE, totalLength);

			if (lastEnd > 0)
				start = lastEnd;
			if (i + 1 == chunkCount)
				end = lines.length();
			else
				end = adjustEndPosition(lines, end);
			int fStart = start;
			int fEnd = end;
			futures.add(API.EXECUTOR.submit(() -> readLinesInRange(lines, fStart, fEnd)));

			lastEnd = end;
		}
		for (Future<List<int[]>> future : futures)
			try {
				List<int[]> chunkLines = future.get();
				allLinePositions.addAll(chunkLines);
			} catch (InterruptedException | ExecutionException e) {
			}
		allLinePositions.sort((a, b) -> Integer.compare(a[0], b[0]));
		int prev = 0;
		ListIterator<int[]> itr = allLinePositions.listIterator();
		while (itr.hasNext()) {
			int[] i = itr.next();
			if (prev - i[0] < -2) {
				itr.previous();
				itr.add(new int[] { prev, i[0] });
				itr.next();
			}
			prev = i[1];
		}
		return allLinePositions;
	}

	private int adjustEndPosition(StringContainer lines, int end) {
		for (int i = end - 1; i >= 0; i--)
			if (isLineSeparator(lines, i))
				return i + getLineSeparatorLength(lines, i);
		return end;
	}

	private List<int[]> readLinesInRange(StringContainer lines, int start, int end) {
		List<int[]> resultLines = new ArrayList<>();
		int currentStart = start;

		for (int i = start; i < end; i++)
			if (isLineSeparator(lines, i)) {
				if (currentStart < i)
					resultLines.add(new int[] { currentStart, i });
				currentStart = i + getLineSeparatorLength(lines, i);
			}
		if (currentStart < end)
			if (currentStart < lines.length())
				resultLines.add(new int[] { currentStart, end });
		return resultLines;
	}

	private boolean isLineSeparator(StringContainer lines, int index) {
		return lines.charAt(index) == '\n' || lines.charAt(index) == '\r';
	}

	private int getLineSeparatorLength(StringContainer lines, int index) {
		if (lines.charAt(index) == '\r' && index + 1 < lines.length() && lines.charAt(index + 1) == '\n')
			return 2;
		return 1;
	}

	@Override
	public void load(String input) {
		if (input == null)
			return;
		reset();

		lines = new StringContainer(input, 0, 0);

		List<String> comments = null;

		for (int[] line : readLinesFromContainer(lines)) {
			if (lines.charAt(line[0]) == '\r' ? lines.charAt(line[0] + 1) == ' ' : lines.charAt(line[0]) == ' ') { // S-s-space?! Maybe.. this is YAML file.
				data.clear();
				comments = null;
				break;
			}

			int[] trimmed = YamlLoader.trim(lines, line);
			// Comments
			if (trimmed[0] == trimmed[1] || lines.charAt(trimmed[0]) == '#') {
				if (comments == null)
					comments = new ArrayList<>();
				comments.add(trimmed[0] == trimmed[1] ? "" : lines.substring(trimmed[0], trimmed[1]));
				continue;
			}

			int[][] parts = readConfigLine(lines, trimmed);
			if (parts == null) { // Didn't find = symbol.. Maybe this is YAML file.
				data.clear();
				comments = null;
				break;
			}

			if (parts.length == 1) {
				if (comments != null) {
					DataValue val = getOrCreate(lines.substring(parts[0][0], parts[0][1]));
					val.comments = comments;
					val.value = "";
					comments = null;
				}
				continue;
			}

			Object readerValueParsed = YamlLoader.splitFromComment(lines, 0, parts[1]);
			int[][] readerValue = readerValueParsed instanceof Pair ? (int[][]) ((Pair) readerValueParsed).getValue() : (int[][]) readerValueParsed;
			int[] value = readerValue[0];
			int[] indexes = readerValueParsed instanceof Pair ? (int[]) ((Pair) readerValueParsed).getKey() : null;
			String comment = readerValue.length == 1 ? null : lines.substring(readerValue[1][0], readerValue[1][1]);
			set(lines.substring(parts[0][0], parts[0][1]),
					DataValue.of(indexes == null ? lines.substring(value[0], value[1]) : YamlLoader.removeCharsAt(lines.subSequence(value[0], value[1]), indexes),
							Json.reader().read(lines.substring(value[0], value[1])), comment, comments));
			comments = null;
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

	@Override
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
