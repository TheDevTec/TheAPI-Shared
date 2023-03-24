package me.devtec.shared.utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import me.devtec.shared.Ref;
import me.devtec.shared.annotations.ScheduledForRemoval;
import me.devtec.shared.dataholder.StringContainer;

public class StringUtils {

	// DO NOT TOUCH
	public static final Random random = new Random();

	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public enum TimeFormat {
		YEARS(31556952, 365, "y"), MONTHS(2629746, 12, "mon"), DAYS(86400, 31, "d"), HOURS(3600, 24, "h"), MINUTES(60, 60, "m"), SECONDS(1, 60, "s");

		private long seconds;
		private double cast;
		private String defaultSuffix;

		TimeFormat(long seconds, double cast, String defSuffix) {
			this.seconds = seconds;
			this.cast = cast;
			defaultSuffix = defSuffix;
		}

		public long seconds() {
			return seconds;
		}

		public double cast() {
			return cast;
		}

		public String getDefaultSuffix() {
			return defaultSuffix;
		}
	}

	public enum FormatType {
		BASIC, NORMAL, COMPLEX
	}

	/**
	 * @apiNote Format double and remove unused zeros on the end
	 * 
	 *          Format types: {@link FormatType#BASIC} 1000.01
	 *          {@link FormatType#NORMAL} 1,000.01 {@link FormatType#COMPLEX}
	 *          {Normal type} + BalanceType (k, m, b, t...)
	 */
	public static String formatDouble(FormatType type, double value) {
		switch (type) {
		case BASIC: {
			String formatted = String.format(Locale.ENGLISH, "%.2f", value);
			if (formatted.endsWith("00"))
				formatted = formatted.substring(0, formatted.length() - 3); // .00
			else if (formatted.endsWith("0"))
				formatted = formatted.substring(0, formatted.length() - 1); // .X0
			return formatted;
		}
		case NORMAL: {
			String formatted = String.format(Locale.ENGLISH, "%,.2f", value);
			if (formatted.endsWith("00"))
				formatted = formatted.substring(0, formatted.length() - 3); // .00
			else if (formatted.endsWith("0"))
				formatted = formatted.substring(0, formatted.length() - 1); // .X0
			return formatted;
		}
		case COMPLEX: {
			String formatted = String.format(Locale.ENGLISH, "%,.2f", value);
			String[] s = formatted.split(",");
			if (s.length >= 22) { // Why?...
				if (formatted.startsWith("-"))
					return "-∞";
				return "∞";
			}
			if (s.length >= 21)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E60) + "NOV";
			if (s.length >= 20)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E57) + "OCT";
			if (s.length >= 19)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E54) + "SEP";
			if (s.length >= 18)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E51) + "SED";
			if (s.length >= 17)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E48) + "QUI";
			if (s.length >= 16)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E45) + "QUA";
			if (s.length >= 15)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E42) + "tre";
			if (s.length >= 14)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E39) + "duo";
			if (s.length >= 13)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E36) + "und";
			if (s.length >= 12)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E33) + "dec";
			if (s.length >= 11)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E30) + "non";
			if (s.length >= 10)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E27) + "oct";
			if (s.length >= 9)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E24) + "sep";
			if (s.length >= 8) // No, it's not "sex"...
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E21) + "sex";
			if (s.length >= 7)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E18) + "qui";
			if (s.length >= 6)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E15) + "qua";
			if (s.length >= 5)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E12) + "t";
			if (s.length >= 4)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E9) + "b";
			if (s.length >= 3)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E6) + "m";
			if (s.length >= 2)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1000) + "k";
			return formatted;
		}
		default:
			break;
		}
		return value + "";
	}

	/**
	 * @apiNote Split text correctly with colors
	 */
	public static List<String> fixedSplit(String text, int lengthOfSplit) {
		if (text == null)
			return null;

		List<String> splitted = new ArrayList<>();
		if (text.length() <= lengthOfSplit)
			splitted.add(text);
		else if (Ref.serverType().isBukkit() && Ref.isOlderThan(16)) {
			int start = 0;
			for (int i = lengthOfSplit; i < text.length(); i += lengthOfSplit)
				if (text.charAt(i) == '§' || text.charAt(i - 1) == '§') {
					splitted.add(text.substring(start, i - 1));
					start = i - 1;
				} else {
					splitted.add(text.substring(start, i));
					start = i;
				}
			splitted.add(text.substring(start));
		} else { // Hex mode
			int start = 0;

			int steps = lengthOfSplit;
			for (int i = 0; i < text.length(); ++i) {
				char c = text.charAt(i);
				--steps;
				if (i != 0 && c == '§' && text.charAt(i + 1) == 'x' && steps - 13 < 0) {
					steps = 0;
					splitted.add(text.substring(start, i - 1));
					start = i - 1;
					continue;
				}
				if (steps == 0)
					if (c == '§' || text.charAt(i - 1) == '§') {
						splitted.add(text.substring(start, i - 1));
						start = i - 1;
					} else {
						splitted.add(text.substring(start, i));
						start = i;
					}
			}
			splitted.add(text.substring(start));
		}
		return splitted;
	}

	/**
	 * @apiNote Copy matches of String from Iterable<String>
	 * @return List<String>
	 */
	public static List<String> copyPartialMatches(String prefix, Iterable<String> originals) {
		List<String> matches = new ArrayList<>();
		for (String completion : originals)
			if (completion != null && (completion.regionMatches(true, 0, prefix, 0, prefix.length()) || completion.toLowerCase().contains(prefix.toLowerCase())))
				matches.add(completion);
		return matches;
	}

	/**
	 * @apiNote Copy matches of String from Iterable<String>
	 * @return List<String>
	 */
	public static List<String> copySortedPartialMatches(String prefix, Iterable<String> originals) {
		List<String> collection = StringUtils.copyPartialMatches(prefix, originals);
		Collections.sort(collection);
		return collection;
	}

	/**
	 * @apiNote Join Iterable into one String with split {@value split} @see
	 *          {@link StringUtils#join(Iterable<?>, String, int, int)}
	 * @param split Split string (defaulty ' ')
	 * @param args  Arguments
	 * @return String
	 */
	public static String join(Iterable<?> args, String split) {
		return StringUtils.join(args, split, 0, -1);
	}

	/**
	 * @apiNote Join Iterable into one String with split {@value split} @see
	 *          {@link StringUtils#join(Iterable<?>, String, int, int)}
	 * @param split Split string (defaulty ' ')
	 * @param start Start argument (defaulty 0)
	 * @param args  Arguments
	 * @return String
	 */
	public static String join(Iterable<?> args, String split, int start) {
		return StringUtils.join(args, split, start, -1);
	}

	/**
	 * @apiNote Join Iterable into one String with split {@value split} @see
	 *          {@link StringUtils#join(Iterable<?>, String, int, int)}
	 * @param split Split string (defaulty ' ')
	 * @param start Start argument (defaulty 0)
	 * @param end   Last argument (defaultly -1)
	 * @param args  Arguments
	 * @return String
	 */
	public static String join(Iterable<?> args, String split, int start, int end) {
		if (args == null || split == null)
			return null;
		StringContainer msg = new StringContainer(split.length() + 32);
		Iterator<?> iterator = args.iterator();
		boolean first = true;
		for (int i = start; iterator.hasNext() && (end == -1 || i < end); ++i) {
			if (!first)
				msg.append(split);
			else
				first = false;
			msg.append(String.valueOf(iterator.next()));
		}
		return msg.toString();
	}

	/**
	 * @apiNote Join objects into one String with split {@value split} @see
	 *          {@link StringUtils#join(Object[], String, int, int)}
	 * @param split Split string (defaulty ' ')
	 * @param args  Arguments
	 * @return String
	 */
	public static String join(Object[] args, String split) {
		return StringUtils.join(args, split, 0, args.length);
	}

	/**
	 * @apiNote Join objects into one String with split {@value split} @see
	 *          {@link StringUtils#join(Object[], String, int, int)}
	 * @param split Split string (defaulty ' ')
	 * @param start Start argument (defaulty 0)
	 * @param args  Arguments
	 * @return String
	 */
	public static String join(Object[] args, String split, int start) {
		return StringUtils.join(args, split, start, args.length);
	}

	/**
	 * @apiNote Join objects into one String with split {@value split} @see
	 *          {@link StringUtils#join(Object[], String, int, int)}
	 * @param split Split string (defaulty ' ')
	 * @param start Start argument (defaulty 0)
	 * @param end   Last argument (defaultly args.length)
	 * @param args  Arguments
	 * @return String
	 */
	public static String join(Object[] args, String split, int start, int end) {
		if (args == null || split == null)
			return null;
		StringContainer msg = new StringContainer(split.length() * (args.length - 1) + args.length * 4);
		boolean first = true;
		for (int i = start; i < args.length && i < end; ++i) {
			if (!first)
				msg.append(split);
			else
				first = false;
			msg.append(String.valueOf(args[i]));
		}
		return msg.toString();
	}

	/**
	 * @apiNote Join strings to one String with split ' ' @see
	 *          {@link StringUtils#join(Object[], String, int, int)}
	 * @param args Arguments
	 * @return String
	 *
	 */
	public static String buildString(String[] args) {
		return StringUtils.join(args, " ", 0, args.length);
	}

	/**
	 * @apiNote Join strings to one String with split ' ' @see
	 *          {@link StringUtils#join(Object[], String, int, int)}
	 * @param start Start argument (defaulty 0)
	 * @param args  Arguments
	 * @return String
	 *
	 */
	public static String buildString(int start, String[] args) {
		return StringUtils.join(args, " ", start, args.length);
	}

	/**
	 * @apiNote Join strings to one String with split ' ' @see
	 *          {@link StringUtils#join(Object[], String, int, int)}
	 * @param start Start argument (defaulty 0)
	 * @param end   Last argument (defaultly args.length)
	 * @param args  Arguments
	 * @return String
	 *
	 */
	public static String buildString(int start, int end, String[] args) {
		return StringUtils.join(args, " ", start, end);
	}

	/**
	 * @apiNote Return random object from list
	 */
	public static <T> T randomFromList(List<T> list) {
		if (list == null || list.isEmpty())
			return null;
		return list.get(StringUtils.random.nextInt(list.size()));
	}

	/**
	 * @apiNote Return random object from collection
	 */
	@SuppressWarnings("unchecked")
	public static <T> T randomFromCollection(Collection<T> list) {
		if (list == null || list.isEmpty())
			return null;
		if (list instanceof List)
			return StringUtils.randomFromList((List<T>) list);
		return (T) list.toArray()[StringUtils.random.nextInt(list.size())];
	}

	/**
	 * @apiNote Use {@link ParseUtils#getBoolean(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean getBoolean(String text) {
		return ParseUtils.getBoolean(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#getDouble(String, int, int)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static double getDouble(String text, int start, int end) {
		return ParseUtils.getDouble(text, start, end);
	}

	/**
	 * @apiNote Use {@link ParseUtils#getDouble(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static double getDouble(String text) {
		return ParseUtils.getDouble(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#isDouble(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean isDouble(String text) {
		return ParseUtils.isDouble(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#getLong(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static long getLong(String text) {
		return ParseUtils.getLong(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#isLong(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean isLong(String text) {
		return ParseUtils.isInt(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#getInt(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static int getInt(String text) {
		return ParseUtils.getInt(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#isInt(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean isInt(String text) {
		return ParseUtils.isInt(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#isFloat(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean isFloat(String text) {
		return ParseUtils.isFloat(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#getFloat(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static float getFloat(String text) {
		return ParseUtils.getFloat(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#isByte(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean isByte(String text) {
		return ParseUtils.isByte(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#getByte(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static byte getByte(String text) {
		return ParseUtils.getByte(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#isShort(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean isShort(String text) {
		return ParseUtils.isShort(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#getShort(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static short getShort(String text) {
		return ParseUtils.getShort(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#isNumber(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean isNumber(String text) {
		return ParseUtils.isNumber(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#isBoolean(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean isBoolean(String text) {
		return ParseUtils.isBoolean(text);
	}

	/**
	 * @apiNote Use {@link ParseUtils#getNumber(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static Number getNumber(String text) {
		return ParseUtils.getNumber(text);
	}

	/**
	 * @apiNote Use {@link ColorUtils#getLastColors(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static String getLastColors(String text) {
		return ColorUtils.getLastColors(text);
	}

	/**
	 * @apiNote Use {@link ColorUtils#getLastColorsSplitFormats(String)} method
	 *          instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static String[] getLastColorsSplitFormats(String text) {
		return ColorUtils.getLastColorsSplitFormats(text);
	}

	/**
	 * @apiNote Use {@link ColorUtils#gradient(List<String>)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static List<String> gradient(List<String> list) {
		return ColorUtils.gradient(list);
	}

	/**
	 * @apiNote Use {@link ColorUtils#gradient(String)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static String gradient(String originalMsg) {
		return ColorUtils.gradient(originalMsg);
	}

	/**
	 * @apiNote Use {@link ColorUtils#gradient(String, List<String>)} method
	 *          instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static String gradient(String originalMsg, List<String> protectedStrings) {
		return ColorUtils.gradient(originalMsg, protectedStrings);
	}

	/**
	 * @apiNote Use {@link ColorUtils#colorize(List<String>)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static List<String> colorize(List<String> list) {
		return ColorUtils.colorize(list);
	}

	/**
	 * @apiNote Use {@link ColorUtils#colorize(List<String>, List<String>)} method
	 *          instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static List<String> colorize(List<String> list, List<String> protectedStrings) {
		return ColorUtils.colorize(list, protectedStrings);
	}

	/**
	 * @apiNote Use {@link ColorUtils#colorize(String, List<String>)} method
	 *          instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static String colorize(String original) {
		return ColorUtils.colorize(original);
	}

	/**
	 * @apiNote Use {@link ColorUtils#colorize(String, List<String>)} method
	 *          instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static String colorize(String original, List<String> protectedStrings) {
		return ColorUtils.colorize(original, protectedStrings);
	}

	/**
	 * @apiNote Use {@link TimeUtils#timeToString(long)} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static String timeToString(long period) {
		return TimeUtils.timeToString(period);
	}

	/**
	 * @apiNote Use {@link TimeUtils#timeToString(long, TimeFormat...))} method
	 *          instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static String timeToString(long period, TimeFormat... disabled) {
		return TimeUtils.timeToString(period, convertToNewClass(disabled));
	}

	/**
	 * @apiNote Use {@link TimeUtils#timeToString(long, String, TimeFormat...))}
	 *          method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static String timeToString(long period, String split, TimeFormat... disabled) {
		return TimeUtils.timeToString(period, split, convertToNewClass(disabled));
	}

	private static TimeUtils.TimeFormat[] convertToNewClass(TimeFormat[] disabled) {
		if (disabled == null || disabled.length == 0)
			return new TimeUtils.TimeFormat[0];
		TimeUtils.TimeFormat[] converted = new TimeUtils.TimeFormat[disabled.length];
		for (int i = 0; i < disabled.length; ++i)
			converted[i] = TimeUtils.TimeFormat.valueOf(disabled[i].name());
		return converted;
	}

	/**
	 * @apiNote Use {@link TimeUtils#timeFromString(String))} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static long timeFromString(String text) {
		return TimeUtils.timeFromString(text);
	}

	/**
	 * @apiNote Use {@link MathUtils#checkProbability(double))} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean checkProbability(double percent) {
		return MathUtils.checkProbability(percent, 100);
	}

	/**
	 * @apiNote Use {@link MathUtils#checkProbability(double, double))} method
	 *          instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static boolean checkProbability(double percent, double basedChance) {
		return MathUtils.checkProbability(percent, basedChance);
	}

	/**
	 * @apiNote Use {@link MathUtils#randomInt(int))} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static int randomInt(int max) {
		return MathUtils.randomInt(max);
	}

	/**
	 * @apiNote Use {@link MathUtils#randomInt(int, int))} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static int randomInt(int min, int max) {
		return MathUtils.randomInt(min, max);
	}

	/**
	 * @apiNote Use {@link MathUtils#randomDouble(double))} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static double randomDouble(double max) {
		return MathUtils.randomDouble(max);
	}

	/**
	 * @apiNote Use {@link MathUtils#randomDouble(double, double))} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static double randomDouble(double min, double max) {
		return MathUtils.randomDouble(min, max);
	}

	/**
	 * @apiNote Use {@link MathUtils#calculate(String))} method instead.
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static double calculate(String expression) {
		return MathUtils.calculate(expression);
	}

	/**
	 * @apiNote Use {@link MathUtils#calculate(String, int, int))} method instead.
	 * 
	 */
	@Deprecated
	@ScheduledForRemoval(inVersion = "11.2")
	public static double calculate(String expression, int startPos, int endPos) {
		return MathUtils.calculate(expression, startPos, endPos);
	}
}
