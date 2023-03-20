package me.devtec.shared.utility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devtec.shared.API;
import me.devtec.shared.Ref;
import me.devtec.shared.dataholder.StringContainer;

public class StringUtils {

	// INIT THIS

	public static ColormaticFactory color;
	public static Pattern rainbowSplit;
	// TIME UTILS
	// string -> time
	// time -> string
	public static final Map<TimeFormat, TimeFormatter> timeConvertor = new HashMap<>();
	// COLOR UTILS
	public static Pattern gradientFinder;

	// VARRIABLE INIT
	public static Map<String, String> colorMap = new HashMap<>();
	public static String tagPrefix = "!";
	public static String timeSplit = " ";

	// DO NOT TOUCH
	public static final Random random = new Random();

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

	public interface TimeFormatter {
		/**
		 * @apiNote Nullable if settings isn't supported
		 */
		public String toString(long value);

		public Matcher matcher(String text);
	}

	public interface ColormaticFactory {
		char[] characters = "abcdef0123456789".toCharArray();

		/**
		 * @apiNote Generates random color depends on software & version
		 */
		public default String generateColor() {
			StringContainer b = new StringContainer(7).append('#');
			for (int i = 0; i < 6; ++i)
				b.append(characters[random.nextInt(16)]);
			return b.toString();
		}

		/**
		 * @apiNote @see {@link API#basics()}
		 */
		public default String[] getLastColors(String text) {
			return API.basics().getLastColors(text);
		}

		/**
		 * @apiNote Replace #RRGGBB hex color depends on software
		 */
		public default String replaceHex(String text) {
			StringContainer container = new StringContainer(text.length() + 14 * 2);
			for (int i = 0; i < text.length(); ++i) {
				char c = text.charAt(i);
				if (c == '#' && i + 6 < text.length()) {
					boolean isHex = true;
					for (int ic = 1; ic < 7; ++ic) {
						char cn = text.charAt(i + ic);
						if (cn >= 64 && cn <= 70 || cn >= 97 && cn <= 102 || cn >= 48 && cn <= 57)
							continue;
						isHex = false;
						break;
					}
					if (isHex) {
						container.append('§').append('x');
						for (int ic = 1; ic < 7; ++ic) {
							char cn = text.charAt(i + ic);
							container.append('§').append(cn);
						}
						i += 6;
						continue;
					}
				}
				container.append(c);
			}
			return container.toString();
		}

		/**
		 * @param protectedStrings List of strings which not be colored via gradient
		 * @apiNote @see {@link API#basics()}
		 */
		public default String gradient(String msg, String fromHex, String toHex, List<String> protectedStrings) {
			return API.basics().gradient(msg, fromHex, toHex, protectedStrings);
		}

		/**
		 * @param protectedStrings List of strings which not be colored via gradient
		 * @apiNote @see {@link API#basics()}
		 */
		public default String rainbow(String msg, String fromHex, String toHex, List<String> protectedStrings) {
			return API.basics().rainbow(msg, fromHex, toHex, protectedStrings);
		}

	}

	public enum FormatType {
		BASIC, // Basic format - xxx.xx
		NORMAL, // Improved BASIS format - xxx,xxx.xx
		COMPLEX // NORMAL format + balance type
	}

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
	 * @apiNote Generates a random chance and compares it to the specified chance
	 * @param percent Inserted chance
	 */
	public static boolean checkProbability(double percent) {
		return checkProbability(percent, 100);
	}

	/**
	 * @apiNote Generates a random chance and compares it to the specified chance
	 * @param percent       Inserted chance
	 * @param maximumChance Based chance
	 */
	public static boolean checkProbability(double percent, double basedChance) {
		return StringUtils.randomDouble(basedChance) <= percent;
	}

	/**
	 * @apiNote Generate random int within limits
	 * @param max Maximum int
	 */
	public static int randomInt(int max) {
		return StringUtils.randomInt(0, max);
	}

	/**
	 * @apiNote Generate random double within limits
	 * @param max Maximum double
	 */
	public static double randomDouble(double max) {
		return StringUtils.randomDouble(0, max);
	}

	/**
	 * @apiNote Generate random double within limits
	 * @param min Minimum double
	 * @param max Maximum double
	 * @return double
	 */
	public static double randomDouble(double min, double max) {
		double range = max - min;
		if (range <= 0)
			return min;
		double randomValue = Math.random() * range + min;
		if (randomValue >= max)
			return Math.nextDown(max);
		return randomValue;
	}

	/**
	 * @apiNote Generate random int within limits
	 * @param min Minimum int
	 * @param max Maximum int
	 * @return int
	 */
	public static int randomInt(int min, int max) {
		if (min == max)
			return min;

		boolean isNegative = max < 0;
		if (isNegative) {
			max *= -1;
			min *= -1;
		}

		int range = max - min;
		if (range <= 0)
			throw new IllegalArgumentException("Invalid range: min > max");
		int randomValue = (int) (Math.random() * range) + min;
		return isNegative ? randomValue * -1 : randomValue;
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
	 * @apiNote Return joined strings ([0] + [1]) from
	 *          {@link StringUtils#getLastColorsSplitFormats(String)}
	 * @param text Input string
	 * @return String
	 */
	public static String getLastColors(String text) {
		String[] split = StringUtils.color.getLastColors(text);
		return (split[0] == null ? "" : split[0]) + (split[1] == null ? "" : split[1]);
	}

	/**
	 * @apiNote Get last colors from String (HEX SUPPORT!)
	 * @param text Input string
	 * @return String[]
	 */
	public static String[] getLastColorsSplitFormats(String text) {
		return StringUtils.color.getLastColors(text);
	}

	/**
	 * @apiNote Replace gradients in the List of strings
	 * @param list Input list of strings to colorize
	 * @return List<String>
	 */
	public static List<String> gradient(List<String> list) {
		list.replaceAll(StringUtils::gradient);
		return list;
	}

	/**
	 * @apiNote Replace gradients in the String
	 * @param originalMsg Input string to colorize
	 * @return String
	 */
	public static String gradient(String originalMsg) {
		return gradient(originalMsg, null);
	}

	/**
	 * @apiNote Replace gradients in the String
	 * @param originalMsg      Input string to colorize
	 * @param protectedStrings List of strings which not be colored via gradient
	 * @return String
	 */
	public static String gradient(String originalMsg, List<String> protectedStrings) {
		if (originalMsg == null || StringUtils.gradientFinder == null)
			return originalMsg;

		String legacyMsg = originalMsg;

		String low = legacyMsg.toLowerCase();
		for (Entry<String, String> code : StringUtils.colorMap.entrySet()) {
			String rawCode = (StringUtils.tagPrefix + code.getKey()).toLowerCase();
			if (!low.contains(rawCode))
				continue;
			legacyMsg = legacyMsg.replace(rawCode, code.getValue());
		}
		Matcher matcher = StringUtils.gradientFinder.matcher(legacyMsg);
		while (matcher.find()) {
			if (matcher.groupCount() == 0 || matcher.group().isEmpty())
				continue;
			String replace = StringUtils.color.gradient(matcher.group(2), matcher.group(1), matcher.group(3), protectedStrings);
			if (replace == null)
				continue;
			legacyMsg = legacyMsg.replace(matcher.group(), replace);
		}
		return legacyMsg;
	}

	/**
	 * @apiNote Colorize List of strings with colors
	 * @param list Texts to colorize
	 * @return List<String>
	 */
	public static List<String> colorize(List<String> list) {
		list.replaceAll(StringUtils::colorize);
		return list;
	}

	/**
	 * @apiNote Colorize List of strings with colors
	 * @param list             Texts to colorize
	 * @param protectedStrings List of strings which not be colored via gradient
	 * @return List<String>
	 */
	public static List<String> colorize(List<String> list, List<String> protectedStrings) {
		list.replaceAll(string -> colorize(string, protectedStrings));
		return list;
	}

	/**
	 * @apiNote Colorize string with colors
	 * @param original Text to colorize
	 * @return String
	 */
	public static String colorize(String original) {
		return colorize(original, null);
	}

	/**
	 * @apiNote Colorize string with colors
	 * @param original         Text to colorize
	 * @param protectedStrings List of strings which not be colored via gradient
	 * @return String
	 */
	public static String colorize(String original, List<String> protectedStrings) {
		if (original == null || original.trim().isEmpty())
			return original;

		StringBuilder builder = new StringBuilder(original.length());
		for (int i = 0; i < original.length(); ++i) {
			char c = original.charAt(i);
			if (c == '&' && original.length() > i + 1) {
				char next = original.charAt(++i);
				if (isColorChar(next))
					builder.append('§').append(toLowerCase(next));
				else
					builder.append(c).append(next);
				continue;
			}
			builder.append(c);
		}
		String msg = builder.toString();
		if (StringUtils.color != null) {
			if (!Ref.serverType().isBukkit() || Ref.isNewerThan(15)) { // Non bukkit software or 1.16+
				msg = StringUtils.gradient(msg, protectedStrings);
				if (msg.indexOf('#') != -1)
					msg = StringUtils.color.replaceHex(msg);
			}
			if (msg.contains("&u"))
				msg = StringUtils.color.rainbow(msg, StringUtils.color.generateColor(), StringUtils.color.generateColor(), protectedStrings);
		}
		return msg;
	}

	private static boolean isColorChar(int c) {
		return c <= 102 && c >= 97 || c <= 57 && c >= 48 || c <= 70 && c >= 65 || c <= 79 && c >= 75 || c <= 111 && c >= 107 || c == 114 || c == 82 || c == 88 || c == 120;
	}

	private static char toLowerCase(int c) {
		switch (c) {
		case 65:
		case 66:
		case 67:
		case 68:
		case 69:
		case 70:
		case 75:
		case 76:
		case 77:
		case 78:
		case 79:
		case 82:
			return (char) (c + 32);
		case 120:
			return (char) 88;
		default:
			return (char) c;
		}
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
	 * @apiNote Convert long time to String
	 * @param period long Time to convert
	 * @return String
	 */
	public static String timeToString(long period) {
		return StringUtils.timeToString(period, StringUtils.timeSplit);
	}

	/**
	 * @apiNote Convert long time to String
	 * @param period long Time to convert
	 * @return String
	 */
	public static String timeToString(long period, TimeFormat... disabled) {
		return StringUtils.timeToString(period, StringUtils.timeSplit, disabled);
	}

	/**
	 * @apiNote Convert long time to String
	 * @param period   long Time to convert
	 * @param split    String Split between time
	 * @param disabled TimeFormat... disabled time formats
	 * @return String
	 */
	public static String timeToString(long period, String split, TimeFormat... disabled) {
		boolean digit = split.length() == 1 ? split.charAt(0) == ':' : false;

		if (period == 0L)
			return digit ? "0" : StringUtils.timeConvertor.get(TimeFormat.SECONDS).toString(0);

		boolean skipYear = false;
		boolean skipMonth = false;
		boolean skipDay = false;
		boolean skipHour = false;
		boolean skipMinute = false;
		boolean skipSecond = false;

		if (disabled != null)
			for (TimeFormat format : disabled)
				switch (format) {
				case DAYS:
					skipDay = true;
					break;
				case HOURS:
					skipHour = true;
					break;
				case MINUTES:
					skipMinute = true;
					break;
				case MONTHS:
					skipMonth = true;
					break;
				case SECONDS:
					skipSecond = true;
					break;
				case YEARS:
					skipYear = true;
					break;
				}

		if (skipYear && skipMonth && skipDay && skipHour && skipMinute && skipSecond)
			return digit ? String.valueOf(period) : StringUtils.timeConvertor.get(TimeFormat.SECONDS).toString(period);

		long years = 0;
		if (!skipYear) {
			years = period / TimeFormat.YEARS.seconds();
			period = period % TimeFormat.YEARS.seconds();
		}

		long months = 0;
		if (!skipMonth) {
			months = period / TimeFormat.MONTHS.seconds();
			period = period % TimeFormat.MONTHS.seconds();
		}

		long days = 0;
		if (!skipDay) {
			days = period / TimeFormat.DAYS.seconds();
			period = period % TimeFormat.DAYS.seconds();
		}
		long hours = 0;
		if (!skipHour) {
			hours = period / TimeFormat.HOURS.seconds();
			period = period % TimeFormat.HOURS.seconds();
		}

		long minutes = 0;
		if (!skipMinute) {
			minutes = period / TimeFormat.MINUTES.seconds();
			period = period % TimeFormat.MINUTES.seconds();
		}

		long seconds = skipSecond ? 0 : period;
		StringContainer builder = new StringContainer((int) (years + months + days + hours + minutes + seconds + 8));
		StringUtils.addFormat(builder, split, TimeFormat.YEARS, digit, years);
		StringUtils.addFormat(builder, split, TimeFormat.MONTHS, digit, months);
		StringUtils.addFormat(builder, split, TimeFormat.DAYS, digit, days);
		StringUtils.addFormat(builder, split, TimeFormat.HOURS, digit, hours);
		StringUtils.addFormat(builder, split, TimeFormat.MINUTES, digit, minutes);
		StringUtils.addFormat(builder, split, TimeFormat.SECONDS, digit, seconds);
		return builder.toString();
	}

	private static void addFormat(StringContainer builder, String split, TimeFormat format, boolean digit, long time) {
		if (time > 0) {
			boolean notFirst = builder.length() != 0;
			if (notFirst)
				builder.append(split);
			if (digit) {
				if (time < 10 && notFirst)
					builder.append('0');
				builder.append(time);
			} else
				builder.append(StringUtils.timeConvertor.get(format).toString(time));
		} else if (digit) {
			boolean notFirst = builder.length() != 0;
			if (notFirst)
				builder.append(split).append("00");
		}
	}

	/**
	 * @apiNote Get long from string
	 * @param period String
	 * @return long
	 */
	public static long timeFromString(String original) {
		if (original == null || original.isEmpty())
			return 0;

		String period = original;

		if (StringUtils.isLong(period))
			return StringUtils.getLong(period);

		long time = 0;

		if (period.contains(":")) {
			String[] split = period.split(":");
			switch (split.length) {
			case 2: // mm:ss
				time += StringUtils.getLong(split[0]) * TimeFormat.MINUTES.seconds();
				time += StringUtils.getLong(split[1]);
				break;
			case 3: // hh:mm:ss
				time += StringUtils.getLong(split[0]) * TimeFormat.HOURS.seconds();
				time += StringUtils.getLong(split[1]) * TimeFormat.MINUTES.seconds();
				time += StringUtils.getLong(split[2]);
				break;
			case 4: // dd:hh:mm:ss
				time += StringUtils.getLong(split[0]) * TimeFormat.DAYS.seconds();
				time += StringUtils.getLong(split[1]) * TimeFormat.HOURS.seconds();
				time += StringUtils.getLong(split[2]) * TimeFormat.MINUTES.seconds();
				time += StringUtils.getLong(split[3]);
				break;
			case 5: // mm:dd:hh:mm:ss
				time += StringUtils.getLong(split[0]) * TimeFormat.MONTHS.seconds();
				time += StringUtils.getLong(split[1]) * TimeFormat.DAYS.seconds();
				time += StringUtils.getLong(split[2]) * TimeFormat.HOURS.seconds();
				time += StringUtils.getLong(split[3]) * TimeFormat.MINUTES.seconds();
				time += StringUtils.getLong(split[4]);
				break;
			default: // yy:mm:dd:hh:mm:ss
				time += StringUtils.getLong(split[0]) * TimeFormat.YEARS.seconds();
				time += StringUtils.getLong(split[1]) * TimeFormat.MONTHS.seconds();
				time += StringUtils.getLong(split[2]) * TimeFormat.DAYS.seconds();
				time += StringUtils.getLong(split[3]) * TimeFormat.HOURS.seconds();
				time += StringUtils.getLong(split[4]) * TimeFormat.MINUTES.seconds();
				time += StringUtils.getLong(split[5]);
				break;
			}
			return time;
		}

		for (TimeFormat format : TimeFormat.values()) {
			Matcher matcher = StringUtils.timeConvertor.get(format).matcher(period);
			while (matcher.find())
				time += StringUtils.getLong(matcher.group()) * format.seconds();
			period = matcher.replaceAll("");
		}
		return time;
	}

	/**
	 * @apiNote Get boolean from string
	 * @return boolean
	 */
	public static boolean getBoolean(String fromString) {
		try {
			return fromString.equalsIgnoreCase("true");
		} catch (Exception er) {
			return false;
		}
	}

	/**
	 * @apiNote his method takes a String argument that represents a mathematical
	 *          expression and returns a double value that represents the result of
	 *          evaluating the expression. The expression can contain the usual
	 *          arithmetic operators (+, -, *, /) as well as parentheses to control
	 *          the order of operations. The method uses a stack to keep track of
	 *          intermediate results and operators, and follows the standard rules
	 *          of operator precedence and associativity.
	 * @return double
	 */

	public static double calculate(String expression) {
		Deque<Double> operands = new ArrayDeque<>();
		Deque<Character> operators = new ArrayDeque<>();
		int i = 0;
		while (i < expression.length()) {
			char c = expression.charAt(i);
			if (isReadable(c)) {
				int j = i + 1;
				while (j < expression.length() && isReadable(expression.charAt(j)))
					j++;
				double value = StringUtils.getDouble(expression, i, j);
				operands.push(value);
				i = j;
			} else
				switch (c) {
				case '+':
				case '-':
				case '*':
				case '/':
					while (!operators.isEmpty() && hasPrecedence(c, operators.peek()))
						operands.push(applyOperator(operators.pop(), operands.pop(), operands.pop()));
					operators.push(c);
					i++;
					break;
				case '(':
					operators.push(c);
					i++;
					break;
				case ')':
					while (!operators.isEmpty() && operators.peek() != '(')
						operands.push(applyOperator(operators.pop(), operands.pop(), operands.pop()));
					if (operators.isEmpty())
						return 0;
					operators.pop(); // pop the '('
					i++;
					break;
				default:
					i++;
					break;
				}
		}
		while (!operators.isEmpty()) {
			if (operators.peek() == '(')
				return 0;
			operands.push(applyOperator(operators.pop(), operands.pop(), operands.pop()));
		}
		if (operands.size() != 1)
			return 0;
		return operands.pop();
	}

	private static boolean hasPrecedence(char op1, char op2) {
		if (op2 == '(' || op2 == ')')
			return false;
		if ((op1 == '*' || op1 == '/') && (op2 == '+' || op2 == '-'))
			return false;
		return true;
	}

	private static double applyOperator(char operator, double operand2, double operand1) {
		switch (operator) {
		case '+':
			return operand1 + operand2;
		case '-':
			return operand1 - operand2;
		case '*':
			return operand1 * operand2;
		case '/':
			if (operand2 == 0)
				return 0;
			return operand1 / operand2;
		default:
			return 0;
		}
	}

	private static boolean isReadable(char c) {
		return c >= '0' && c <= '9' || c == '.' || c == 'e' || c == 'E' || c == ',';
	}

	/**
	 * @apiNote Get double from string
	 * @return double
	 */
	public static double getDouble(String fromString, int start, int end) {
		if (fromString == null)
			return 0;

		double result = 0.0;
		int decimal = 0;
		int exponent = 0;
		boolean minusExponent = false;

		boolean minus = false;
		boolean hasDecimal = false;
		boolean hasExponent = false;
		byte exponentSymbol = 0;

		short totalWidth = 0;

		int size = end;
		charsLoop: for (int i = start; i < size; ++i) {
			char c = fromString.charAt(i);
			switch (c) {
			case ' ':
				continue charsLoop;
			case '-':
				if (minus) {
					if (hasExponent && exponent == 0) {
						minusExponent = true;
						continue charsLoop;
					}
					break charsLoop;
				}
				if (hasExponent && exponent == 0) {
					minusExponent = true;
					continue charsLoop;
				}
				minus = true;
				continue charsLoop;
			case 'e':
			case 'E':
				if (hasExponent)
					break charsLoop;
				hasExponent = true;
				exponentSymbol = 1;
				continue charsLoop;
			case '.':
			case ',':
				if (hasDecimal || hasExponent)
					break charsLoop;
				hasDecimal = true;
				continue charsLoop;
			}
			if (c < 48 || c > 57) {
				if (totalWidth == 0) {
					if (c == 'N' && i + 3 <= size)
						if (fromString.charAt(i + 1) == 'a' && fromString.charAt(i + 2) == 'N')
							return Double.NaN;
					if (c == 'I' && i + 8 <= size)
						if (fromString.charAt(i + 1) == 'n' && fromString.charAt(i + 2) == 'f' && fromString.charAt(i + 3) == 'i' && fromString.charAt(i + 4) == 'n' && fromString.charAt(i + 5) == 'i'
								&& fromString.charAt(i + 6) == 't' && fromString.charAt(i + 7) == 'y')
							return minus ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
				}
				continue;
			}
			if (totalWidth == 0 && c == 48)
				continue;
			int digit = c - 48;
			if (++totalWidth > 308)
				return minus ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

			if (hasExponent) {
				exponent = exponent * 10 + digit;
				exponentSymbol = 0;
			} else {
				result = result * 10 + digit;
				if (hasDecimal)
					++decimal;
			}
		}
		int range = (minusExponent ? -exponent : exponent) - decimal;
		if (range != 0)
			if (range > 0)
				result *= Math.pow(10, range);
			else
				result /= Math.pow(10, range * -1);
		return exponentSymbol == 0 ? minus ? -result : result : 0;
	}

	/**
	 * @apiNote Get double from string
	 * @return double
	 */
	public static double getDouble(String fromString) {
		if (fromString == null)
			return 0;

		return getDouble(fromString, 0, fromString.length());
	}

	/**
	 * @apiNote Is string, double ?
	 * @return boolean
	 */
	public static boolean isDouble(String stringToTest) {
		boolean foundZero = false;
		boolean minus = false;
		short totalWidth = 0;

		boolean dot = false;
		boolean esymbol = false;
		byte unfinishedEsymbol = 0;

		int size = stringToTest.length();
		for (int i = 0; i < size; ++i) {
			char c = stringToTest.charAt(i);
			switch (c) {
			case ' ':
				continue;
			case '-':
				if (minus)
					break;
				minus = true;
				continue;
			case 'e':
			case 'E':
				if (esymbol || totalWidth == 0)
					return false;
				dot = true;
				esymbol = true;
				unfinishedEsymbol = 1;
				continue;
			case '.':
			case ',':
				if (dot || esymbol || totalWidth == 0)
					return false;
				dot = true;
				continue;
			}
			if (c < 48 || c > 57) {
				if (size - 1 == i && (c == 'd' || c == 'f' || c == 'D' || c == 'F'))
					continue;
				if (!foundZero && totalWidth == 0 && c == 'N' && i + 3 <= size)
					return stringToTest.charAt(++i) == 'a' && stringToTest.charAt(++i) == 'N';
				if (!foundZero && totalWidth == 0 && c == 'I' && i + 8 <= size)
					return stringToTest.charAt(++i) == 'n' && stringToTest.charAt(++i) == 'f' && stringToTest.charAt(++i) == 'i' && stringToTest.charAt(++i) == 'n' && stringToTest.charAt(++i) == 'i'
							&& stringToTest.charAt(++i) == 't' && stringToTest.charAt(++i) == 'y';
				return false;
			}
			if (totalWidth == 0 && c == 48) {
				foundZero = true;
				continue;
			}
			++totalWidth;
			unfinishedEsymbol = 0;
		}
		return (totalWidth > 0 || foundZero) && unfinishedEsymbol == 0;
	}

	/**
	 * @apiNote Get long from string
	 * @return long
	 */
	public static long getLong(String fromString) {
		if (fromString == null)
			return 0;
		long result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = 0; i < fromString.length(); ++i) {
			char c = fromString.charAt(i);
			switch (c) {
			case ' ':
				continue;
			case '-':
				if (minus)
					break;
				minus = true;
				result = -result;
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = c == 57;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overLongLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 19 || totalWidth == 19 && overLimit == 1)
				return 0;

			result = result * 10 + (minus ? -digit : digit);
		}
		return result;
	}

	/**
	 * @apiNote Is string, long ?
	 * @return boolean
	 */
	public static boolean isLong(String stringToTest) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = 0; i < stringToTest.length(); ++i) {
			char c = stringToTest.charAt(i);
			switch (c) {
			case ' ':
				continue;
			case '-':
				if (minus)
					break;
				minus = true;
				continue;
			}
			if (c < 48 || c > 57)
				return false;
			if (totalWidth == 0) {
				if (c == 48) {
					foundZero = true;
					continue;
				}
				onLimit = c == 57;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overLongLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 19 || totalWidth == 19 && overLimit == 1)
				return false;
		}
		return totalWidth > 0 || foundZero;
	}

	// 9,223,372,036,854,775,807-8
	private static int overLongLimit(boolean minus, int pos) {
		switch (pos) {
		case 1:
		case 2:
		case 6:
			return 2;
		case 3:
		case 4:
		case 8:
			return 3;
		case 5:
		case 13:
		case 14:
			return 7;
		case 7:
		case 17:
			return 0;
		case 9:
			return 6;
		case 10:
		case 16:
			return 8;
		case 11:
		case 15:
			return 5;
		case 12:
			return 4;
		case 18:
			return minus ? 8 : 7;
		default:
			break;
		}
		return 9;
	}

	/**
	 * @apiNote Get int from string
	 * @return int
	 */
	public static int getInt(String fromString) {
		if (fromString == null)
			return 0;
		int result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = 0; i < fromString.length(); ++i) {
			char c = fromString.charAt(i);
			switch (c) {
			case ' ':
				continue;
			case '-':
				if (minus)
					break;
				minus = true;
				result = -result;
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = c == 50;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overIntLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 10 || totalWidth == 10 && overLimit == 1)
				return 0;

			result = result * 10 + (minus ? -digit : digit);
		}
		return result;
	}

	/**
	 * @apiNote Is string, int ?
	 * @return boolean
	 */
	public static boolean isInt(String stringToTest) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = 0; i < stringToTest.length(); ++i) {
			char c = stringToTest.charAt(i);
			switch (c) {
			case ' ':
				continue;
			case '-':
				if (minus)
					break;
				minus = true;
				continue;
			}
			if (c < 48 || c > 57)
				return false;
			if (totalWidth == 0) {
				if (c == 48) {
					foundZero = true;
					continue;
				}
				onLimit = c == 50;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overIntLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 10 || totalWidth == 10 && overLimit == 1)
				return false;
		}
		return totalWidth > 0 || foundZero;
	}

	// -2,147,483,647-8
	private static int overIntLimit(boolean minus, int pos) {
		switch (pos) {
		case 1:
			return 1;
		case 2:
		case 4:
		case 8:
			return 4;
		case 3:
			return 7;
		case 5:
			return 8;
		case 6:
			return 3;
		case 7:
			return 6;
		case 9:
			return minus ? 8 : 7;
		default:
			break;
		}
		return 2;
	}

	/**
	 * @apiNote Is string, float (double) ?
	 * @return boolean
	 */
	public static boolean isFloat(String stringToTest) {
		return isDouble(stringToTest);
	}

	/**
	 * @apiNote Get float from string
	 * @return float
	 */
	public static float getFloat(String fromString) {
		if (fromString == null)
			return 0;

		float result = 0;
		int decimal = 0;
		int exponent = 0;
		boolean minusExponent = false;

		boolean minus = false;
		boolean hasDecimal = false;
		boolean hasExponent = false;
		byte exponentSymbol = 0;

		short totalWidth = 0;

		int size = fromString.length();
		charsLoop: for (int i = 0; i < size; ++i) {
			char c = fromString.charAt(i);
			switch (c) {
			case ' ':
				continue charsLoop;
			case '-':
				if (minus) {
					if (hasExponent && exponent == 0) {
						minusExponent = true;
						continue charsLoop;
					}
					break charsLoop;
				}
				if (hasExponent && exponent == 0) {
					minusExponent = true;
					continue charsLoop;
				}
				minus = true;
				continue charsLoop;
			case 'e':
			case 'E':
				if (hasExponent)
					break charsLoop;
				hasExponent = true;
				exponentSymbol = 1;
				continue charsLoop;
			case '.':
			case ',':
				if (hasDecimal || hasExponent)
					break charsLoop;
				hasDecimal = true;
				continue charsLoop;
			}
			if (c < 48 || c > 57) {
				if (totalWidth == 0) {
					if (c == 'N' && i + 3 <= size)
						if (fromString.charAt(i + 1) == 'a' && fromString.charAt(i + 2) == 'N')
							return Float.NaN;
					if (c == 'I' && i + 8 <= size)
						if (fromString.charAt(i + 1) == 'n' && fromString.charAt(i + 2) == 'f' && fromString.charAt(i + 3) == 'i' && fromString.charAt(i + 4) == 'n' && fromString.charAt(i + 5) == 'i'
								&& fromString.charAt(i + 6) == 't' && fromString.charAt(i + 7) == 'y')
							return minus ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
				}
				continue;
			}
			if (totalWidth == 0 && c == 48)
				continue;
			int digit = c - 48;
			if (++totalWidth > 39)
				return minus ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;

			if (hasExponent) {
				exponent = exponent * 10 + digit;
				exponentSymbol = 0;
			} else {
				result = result * 10 + digit;
				if (hasDecimal)
					++decimal;
			}
		}
		int range = (minusExponent ? -exponent : exponent) - decimal;
		if (range != 0)
			if (range > 0)
				result *= Math.pow(10, range);
			else
				result /= Math.pow(10, range * -1);
		return exponentSymbol == 0 ? minus ? -result : result : 0;
	}

	/**
	 * @apiNote Is string, byte ?
	 * @return boolean
	 */
	public static boolean isByte(String stringToTest) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = 0; i < stringToTest.length(); ++i) {
			char c = stringToTest.charAt(i);
			switch (c) {
			case ' ':
				continue;
			case '-':
				if (minus)
					break;
				minus = true;
				continue;
			}
			if (c < 48 || c > 57)
				return false;
			if (totalWidth == 0) {
				if (c == 48) {
					foundZero = true;
					continue;
				}
				onLimit = c == 49;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overByteLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 3 || totalWidth == 3 && overLimit == 1)
				return false;
		}
		return totalWidth > 0 || foundZero;
	}

	// 127-8
	private static int overByteLimit(boolean minus, int pos) {
		switch (pos) {
		case 1:
			return 2;
		case 2:
			return minus ? 8 : 7;
		}
		return 1;
	}

	/**
	 * @apiNote Get float from string
	 * @return byte
	 */
	public static byte getByte(String fromString) {
		if (fromString == null)
			return 0;
		byte result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = 0; i < fromString.length(); ++i) {
			char c = fromString.charAt(i);
			switch (c) {
			case ' ':
				continue;
			case '-':
				if (minus)
					break;
				minus = true;
				result = (byte) -result;
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = c == 51;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overByteLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 5 || totalWidth == 5 && overLimit == 1)
				return 0;

			result = (byte) (result * 10 + (minus ? -digit : digit));
		}
		return result;
	}

	/**
	 * @apiNote Is string, short ?
	 * @return boolean
	 */
	public static boolean isShort(String stringToTest) {
		boolean foundZero = false;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = 0; i < stringToTest.length(); ++i) {
			char c = stringToTest.charAt(i);
			switch (c) {
			case ' ':
				continue;
			case '-':
				if (minus)
					break;
				minus = true;
				continue;
			}
			if (c < 48 || c > 57)
				return false;
			if (totalWidth == 0) {
				if (c == 48) {
					foundZero = true;
					continue;
				}
				onLimit = c == 51;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overShortLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 5 || totalWidth == 5 && overLimit == 1)
				return false;
		}
		return totalWidth > 0 || foundZero;
	}

	// 32,767-8
	private static int overShortLimit(boolean minus, int pos) {
		switch (pos) {
		case 1:
			return 2;
		case 2:
			return 7;
		case 3:
			return 6;
		case 4:
			return minus ? 8 : 7;
		}
		return 3;
	}

	/**
	 * @apiNote Get float from string
	 * @return short
	 */
	public static short getShort(String fromString) {
		if (fromString == null)
			return 0;
		short result = 0;
		boolean minus = false;
		byte totalWidth = 0;
		byte overLimit = 0;
		boolean onLimit = false;
		int limit = 0;

		for (int i = 0; i < fromString.length(); ++i) {
			char c = fromString.charAt(i);
			switch (c) {
			case ' ':
				continue;
			case '-':
				if (minus)
					break;
				minus = true;
				result = (short) -result;
				continue;
			}
			if (c < 48 || c > 57)
				continue;
			if (totalWidth == 0) {
				if (c == 48)
					continue;
				onLimit = c == 51;
			}
			int digit = c - 48;

			if (onLimit) {
				limit = overShortLimit(minus, totalWidth);
				if (digit != limit)
					if (digit > limit)
						overLimit = 1;
					else if (digit < limit)
						onLimit = false;
			}
			if (++totalWidth > 5 || totalWidth == 5 && overLimit == 1)
				return 0;

			result = (short) (result * 10 + (minus ? -digit : digit));
		}
		return result;
	}

	/**
	 * @apiNote Is string, number ?
	 * @return boolean
	 */
	public static boolean isNumber(String fromString) {
		return StringUtils.isInt(fromString) || StringUtils.isLong(fromString) || StringUtils.isDouble(fromString);
	}

	/**
	 * @apiNote Is string, boolean ?
	 * @return boolean
	 */
	public static boolean isBoolean(String fromString) {
		if (fromString == null)
			return false;
		return fromString.equalsIgnoreCase("true") || fromString.equalsIgnoreCase("false");
	}

	public static Number getNumber(String o) {
		if (o == null || o.isEmpty())
			return null;
		if (o.indexOf('.') == -1) {
			if (StringUtils.isInt(o))
				return StringUtils.getInt(o);
			if (StringUtils.isLong(o))
				return StringUtils.getLong(o);
		}
		if (StringUtils.isDouble(o))
			return StringUtils.getDouble(o);
		return null;
	}
}
