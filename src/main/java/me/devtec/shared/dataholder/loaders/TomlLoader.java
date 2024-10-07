package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import me.devtec.shared.API;
import me.devtec.shared.Pair;
import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class TomlLoader extends EmptyLoader {
	
	private static final int CHUNK_SIZE = 1024*16;

	private StringContainer lines;
	
	private List<int[]> readLinesFromContainer(StringContainer lines) {
	    ExecutorService executor = Executors.newFixedThreadPool(API.THREAD_COUNT);
	    List<Future<List<int[]>>> futures = new ArrayList<>();
	    List<int[]> allLinePositions = new ArrayList<>();

	    int totalLength = lines.length();
	    int chunkCount = (totalLength + CHUNK_SIZE - 1) / CHUNK_SIZE;
	    int lastEnd = 0;

	    for (int i = 0; i < chunkCount; i++) {
	        int start = i * CHUNK_SIZE;
	        int end = Math.min(start + CHUNK_SIZE, totalLength);
	        
	        if (lastEnd > 0) {
	            start = lastEnd;
	        }
	        if(i+1==chunkCount) {
		        end = lines.length();
	        }else
	        	end = adjustEndPosition(lines, end);
	        int fStart = start;
	        int fEnd = end;
	        futures.add(executor.submit(() -> readLinesInRange(lines, fStart, fEnd)));
	        
	        lastEnd = end;
	    }
	    for (Future<List<int[]>> future : futures) {
	        try {
	            List<int[]> chunkLines = future.get();
	            allLinePositions.addAll(chunkLines);
	        } catch (InterruptedException | ExecutionException e) {
	        }
	    }
	    executor.shutdown();
	    allLinePositions.sort((a, b) -> Integer.compare(a[0], b[0]));
	    return allLinePositions;
	}

    private int adjustEndPosition(StringContainer lines, int end) {
        for (int i = end - 1; i >= 0; i--) {
            if (isLineSeparator(lines, i)) {
                return i + getLineSeparatorLength(lines, i);
            }
        }
        return end;
    }

    private List<int[]> readLinesInRange(StringContainer lines, int start, int end) {
        List<int[]> resultLines = new ArrayList<>();
        int currentStart = start;

        for (int i = start; i < end; i++) {
            if (isLineSeparator(lines, i)) {
                if (currentStart < i) {
                    resultLines.add(new int[] { currentStart, i });
                }
                currentStart = i + getLineSeparatorLength(lines, i);
            }
        }
        if (currentStart < end) {
            if (currentStart < lines.length()) {
                resultLines.add(new int[] { currentStart, end });
            }
        }
        return resultLines;
    }

    private boolean isLineSeparator(StringContainer lines, int index) {
        return (lines.charAt(index) == '\n') || (lines.charAt(index) == '\r');
    }

    private int getLineSeparatorLength(StringContainer lines, int index) {
        if (lines.charAt(index) == '\r' && index + 1 < lines.length() && lines.charAt(index + 1) == '\n') {
            return 2;
        }
        return 1;
    }

	@SuppressWarnings({ "unchecked" })
	@Override
	public void load(String input) {
		if (input == null)
			return;
		reset();

		lines = new StringContainer(input, 0, 0);

		List<String> comments = null;
		List<Map<Object, Object>> list;
		Map<Object, Object> map;
		int mode = 0;
		StringContainer mainPath = null;

        for (int[] line : readLinesFromContainer(lines)) {
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
					if (mode != 1)
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

			if (mode != 1)
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
		return YamlLoader.saveAsIteratorAs(config, markSaved, false);
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
