package me.devtec.shared.utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import me.devtec.shared.Ref;
import me.devtec.shared.dataholder.StringContainer;

public class StringUtils {

	// DO NOT TOUCH
	public static final Random random = new Random();

	private static final long[] POWERS_OF_TEN = { 1L, 10L, 100L, 1000L, 10000L, 100000L, 1000000L, 10000000L,
			100000000L, 1000000000L, 10000000000L, 100000000000L, 1000000000000L, 10000000000000L, 100000000000000L,
			1000000000000000L, 10000000000000000L, 100000000000000000L, 1000000000000000000L };

	public enum FormatType {
		BASIC, NORMAL, COMPLEX
	}

	public static String formatDouble(FormatType type, double value) {
		switch (type) {
		case BASIC:
			return formatBasic(value);
		case NORMAL:
			return formatNormal(value);
		case COMPLEX: {
			double testValue = value < 0 ? -value : value;

			if (testValue >= 1.0E63)
				return value < 0 ? "-∞" : "∞";

			if (testValue >= 1.0E60)
				return formatNormal(value / 1.0E60) + "NOV";
			if (testValue >= 1.0E57)
				return formatNormal(value / 1.0E57) + "OCT";
			if (testValue >= 1.0E54)
				return formatNormal(value / 1.0E54) + "SEP";
			if (testValue >= 1.0E51)
				return formatNormal(value / 1.0E51) + "SED";
			if (testValue >= 1.0E48)
				return formatNormal(value / 1.0E48) + "QUI";
			if (testValue >= 1.0E45)
				return formatNormal(value / 1.0E45) + "QUA";
			if (testValue >= 1.0E42)
				return formatNormal(value / 1.0E42) + "tre";
			if (testValue >= 1.0E39)
				return formatNormal(value / 1.0E39) + "duo";
			if (testValue >= 1.0E36)
				return formatNormal(value / 1.0E36) + "und";
			if (testValue >= 1.0E33)
				return formatNormal(value / 1.0E33) + "dec";
			if (testValue >= 1.0E30)
				return formatNormal(value / 1.0E30) + "non";
			if (testValue >= 1.0E27)
				return formatNormal(value / 1.0E27) + "oct";
			if (testValue >= 1.0E24)
				return formatNormal(value / 1.0E24) + "sep";
			if (testValue >= 1.0E21)
				return formatNormal(value / 1.0E21) + "sex";
			if (testValue >= 1.0E18)
				return formatNormal(value / 1.0E18) + "qui";
			if (testValue >= 1.0E15)
				return formatNormal(value / 1.0E15) + "qua";
			if (testValue >= 1.0E12)
				return formatNormal(value / 1.0E12) + "t";
			if (testValue >= 1.0E9)
				return formatNormal(value / 1.0E9) + "b";
			if (testValue >= 1.0E6)
				return formatNormal(value / 1.0E6) + "m";
			if (testValue >= 1000)
				return formatNormal(value / 1000) + "k";

			return formatNormal(value);
		}
		default:
			return String.valueOf(value);
		}
	}

	private static String formatBasic(double value) {
		boolean minus = value < 0;

		if (minus)
			value = -value;

		long integerPart = (long) value;
		long decimalPart = Math.round(Math.abs(value - integerPart) * 100);

		if (decimalPart == 100) {
			++integerPart;
			decimalPart = 0;
		}

		StringContainer builder = new StringContainer(MathUtils.getLongLength(integerPart) + 4);

		if (minus)
			builder.append('-');

		builder.append(integerPart);
		appendDecimal(builder, decimalPart);
		return builder.toString();
	}

	private static String formatNormal(double value) {
		boolean minus = value < 0;

		if (minus)
			value = -value;

		long integerPart = (long) value;
		long decimalPart = Math.round(Math.abs(value - integerPart) * 100);

		if (decimalPart == 100) {
			++integerPart;
			decimalPart = 0;
		}

		int width = MathUtils.getLongLength(integerPart);
		StringContainer builder = new StringContainer(width + width / 3 + 4);

		if (minus)
			builder.append('-');

		appendGroupedLong(builder, integerPart, width);
		appendDecimal(builder, decimalPart);

		return builder.toString();
	}

	private static void appendGroupedLong(StringContainer builder, long value, int width) {
		long divisor = POWERS_OF_TEN[width - 1];

		for (int remaining = width; remaining > 0; --remaining) {
			int digit = (int) (value / divisor);

			builder.append((char) ('0' + digit));

			value -= digit * divisor;

			if (remaining > 1 && (remaining - 1) % 3 == 0)
				builder.append(',');

			divisor /= 10;
		}
	}

	private static void appendDecimal(StringContainer builder, long decimalPart) {
		if (decimalPart == 0)
			return;

		builder.append('.');

		if (decimalPart < 10) {
			builder.append('0');
			builder.append(decimalPart);
			return;
		}

		if (decimalPart % 10 == 0)
			builder.append(decimalPart / 10);
		else
			builder.append(decimalPart);
	}

	public static List<String> fixedSplit(String text, int lengthOfSplit) {
		if (text == null)
			return null;

		if (lengthOfSplit <= 0)
			throw new IllegalArgumentException("lengthOfSplit must be greater than 0");

		int textLength = text.length();
		int estimated = textLength / lengthOfSplit + 1;
		List<String> result = new ArrayList<>(estimated);

		if (textLength <= lengthOfSplit) {
			result.add(text);
			return result;
		}

		boolean hex = !Ref.type().isBukkit() || Ref.isAtLeast(16, 0);
		int start = 0;

		while (start < textLength) {
			int end = Math.min(start + lengthOfSplit, textLength);

			if (end < textLength) {
				if (end > start && text.charAt(end - 1) == '§')
					--end;

				if (hex)
					end = avoidHexSplit(text, start, end);
			}

			if (end <= start)
				end = Math.min(start + lengthOfSplit, textLength);

			result.add(text.substring(start, end));
			start = end;
		}

		return result;
	}

	private static int avoidHexSplit(String text, int start, int end) {
		int from = Math.max(start, end - 13);

		for (int i = end - 1; i >= from; --i) {
			if (text.charAt(i) != '§' || i + 1 >= text.length())
				continue;

			char x = text.charAt(i + 1);

			if (x != 'x' && x != 'X' || !isHexSequence(text, i))
				continue;

			if (end > i && end < i + 14 && i > start)
				return i;

			break;
		}

		return end;
	}

	private static boolean isHexSequence(String text, int start) {
		if (start + 13 >= text.length() || text.charAt(start) != '§')
			return false;

		char x = text.charAt(start + 1);

		if (x != 'x' && x != 'X')
			return false;

		for (int i = start + 2; i < start + 14; i += 2)
			if (text.charAt(i) != '§')
				return false;

		return true;
	}

	public static List<String> copyPartialMatches(String prefix, Iterable<String> originals) {
		if (originals == null)
			return Collections.emptyList();

		if (prefix == null)
			prefix = "";

		int capacity = 8;

		if (originals instanceof Collection) {
			int size = ((Collection<?>) originals).size();
			capacity = prefix.isEmpty() ? size : Math.min(size, 16);
		}

		List<String> matches = new ArrayList<>(capacity);
		copyPartialMatches(prefix, originals, matches);
		return matches;
	}

	public static void copyPartialMatches(String prefix, Iterable<String> originals, Collection<String> output) {
		if (originals == null || output == null)
			return;

		if (prefix == null)
			prefix = "";

		if (prefix.isEmpty()) {
			for (String completion : originals)
				if (completion != null)
					output.add(completion);

			return;
		}

		for (String completion : originals)
			if (completion != null && containsIgnoreCase(completion, prefix))
				output.add(completion);
	}

	private static boolean containsIgnoreCase(String text, String search) {
		int searchLength = search.length();
		int textLength = text.length();

		if (searchLength == 0)
			return true;

		if (searchLength > textLength)
			return false;

		if (searchLength == 1) {
			char searchChar = search.charAt(0);

			for (int i = 0; i < textLength; ++i)
				if (equalsIgnoreCase(text.charAt(i), searchChar))
					return true;

			return false;
		}

		char first = search.charAt(0);
		char last = search.charAt(searchLength - 1);
		int lastOffset = searchLength - 1;
		int max = textLength - searchLength;

		for (int i = 0; i <= max; ++i) {
			if (!equalsIgnoreCase(text.charAt(i), first) || !equalsIgnoreCase(text.charAt(i + lastOffset), last))
				continue;

			int j = 1;

			while (j < lastOffset && equalsIgnoreCase(text.charAt(i + j), search.charAt(j)))
				++j;

			if (j == lastOffset)
				return true;
		}

		return false;
	}

	private static boolean equalsIgnoreCase(char first, char second) {
		if (first == second)
			return true;

		if (first < 128 && second < 128) {
			if (first >= 'A' && first <= 'Z')
				first = (char) (first + 32);

			if (second >= 'A' && second <= 'Z')
				second = (char) (second + 32);

			return first == second;
		}

		return Character.toUpperCase(first) == Character.toUpperCase(second);
	}

	public static List<String> copySortedPartialMatches(String prefix, Iterable<String> originals) {
		List<String> result = copyPartialMatches(prefix, originals);

		if (result.size() > 1)
			Collections.sort(result);

		return result;
	}

	public static String join(Iterable<?> args, String split) {
		return join(args, split, 0, -1);
	}

	public static String join(Iterable<?> args, String split, int start) {
		return join(args, split, start, -1);
	}

	public static String join(Iterable<?> args, String split, int start, int end) {
		if (args == null || split == null)
			return null;

		if (start < 0)
			start = 0;

		if (end != -1 && end <= start)
			return "";

		Iterator<?> iterator = args.iterator();

		int skipped = 0;

		while (skipped < start && iterator.hasNext()) {
			iterator.next();
			++skipped;
		}

		if (!iterator.hasNext())
			return "";

		int estimated = 32 + split.length() * 4;

		if (args instanceof Collection) {
			int size = ((Collection<?>) args).size();
			int amount = size - start;

			if (end != -1)
				amount = Math.min(amount, end - start);

			if (amount > 0)
				estimated = Math.max(16, amount * 8 + Math.max(0, amount - 1) * split.length());
		}

		StringContainer result = new StringContainer(estimated);

		int index = start;
		boolean first = true;

		while (iterator.hasNext() && (end == -1 || index < end)) {
			if (!first)
				result.append(split);
			else
				first = false;

			appendObject(result, iterator.next());
			++index;
		}

		return result.toString();
	}

	public static String join(Object[] args, String split) {
		return args == null ? null : join(args, split, 0, args.length);
	}

	public static String join(Object[] args, String split, int start) {
		return args == null ? null : join(args, split, start, args.length);
	}

	public static String join(Object[] args, String split, int start, int end) {
		if (args == null || split == null)
			return null;

		if (start < 0)
			start = 0;

		if (end > args.length)
			end = args.length;

		if (start >= end || start >= args.length)
			return "";

		int amount = end - start;
		int capacity = amount * 8 + (amount - 1) * split.length();

		if (capacity < 16)
			capacity = 16;

		StringContainer result = new StringContainer(capacity);

		appendObject(result, args[start]);

		for (int i = start + 1; i < end; ++i) {
			result.append(split);
			appendObject(result, args[i]);
		}

		return result.toString();
	}

	private static void appendObject(StringContainer container, Object value) {
		if (value == null) {
			container.appendNull();
			return;
		}

		if (value instanceof String) {
			container.append((String) value);
			return;
		}

		if (value instanceof StringContainer) {
			container.append((StringContainer) value);
			return;
		}

		if (value instanceof CharSequence) {
			container.append((CharSequence) value);
			return;
		}

		if (value instanceof Character) {
			container.append(((Character) value).charValue());
			return;
		}

		if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
			container.append(((Number) value).longValue());
			return;
		}

		container.append(String.valueOf(value));
	}

	public static String buildString(String[] args) {
		return args == null ? null : join(args, " ", 0, args.length);
	}

	public static String buildString(int start, String[] args) {
		return args == null ? null : join(args, " ", start, args.length);
	}

	public static String buildString(int start, int end, String[] args) {
		return args == null ? null : join(args, " ", start, end);
	}

	public static <T> T randomFromList(List<T> list) {
		if (list == null || list.isEmpty())
			return null;

		return list.get(random.nextInt(list.size()));
	}

	public static <T> T randomFromCollection(Collection<T> collection) {
		if (collection == null || collection.isEmpty())
			return null;

		if (collection instanceof List)
			return randomFromList((List<T>) collection);

		int size = collection.size();

		if (size == 1)
			return collection.iterator().next();

		int target = random.nextInt(size);
		Iterator<T> iterator = collection.iterator();

		while (target-- > 0)
			iterator.next();

		return iterator.next();
	}
}