package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import me.devtec.shared.API;
import me.devtec.shared.dataholder.StringContainer;

public class LoaderReadUtil {
	private static final int CHUNK_SIZE = 1024 * 16;

	public static List<int[]> readLinesFromContainer(StringContainer lines) {
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
			if (i + 1 >= chunkCount)
				end = totalLength;
			else
				end = Math.min(adjustEndPosition(lines, end), totalLength);
			int fStart = start;
			int fEnd = end;
			futures.add(API.getExecutor().submit(() -> readLinesInRange(lines, fStart, fEnd, totalLength)));

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

	private static int adjustEndPosition(StringContainer lines, int end) {
		for (int i = end - 1; i >= 0; i--)
			if (isLineSeparator(lines, i))
				return i + getLineSeparatorLength(lines, i);
		return end;
	}

	private static List<int[]> readLinesInRange(StringContainer lines, int start, int end, int maxLength) {
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
				resultLines.add(new int[] { currentStart, Math.min(end, maxLength) });
		return resultLines;
	}

	private static boolean isLineSeparator(StringContainer lines, int index) {
		return lines.charAt(index) == '\n' || lines.charAt(index) == '\r';
	}

	private static int getLineSeparatorLength(StringContainer lines, int index) {
		if (lines.charAt(index) == '\r' && index + 1 < lines.length() && lines.charAt(index + 1) == '\n')
			return 2;
		return 1;
	}
}
