package me.devtec.shared.utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
	// SPECIAL CHARS
	private static final Pattern special = Pattern.compile("[^A-Z-a-z0-9_]+");
	// CALCULATOR
	private static final Pattern extra = Pattern.compile("((^[-])?[ ]*[0-9.]+)[ ]*([*/])[ ]*(-?[ ]*[0-9.]+)");
	private static final Pattern normal = Pattern.compile("((^[-])?[ ]*[0-9.]+)[ ]*([+-])[ ]*(-?[ ]*[0-9.]+)");

	public enum TimeFormat {
		YEARS(31536000, 365.2420833333334, "y"), MONTHS(2628000, 12, "mon"), DAYS(86400, 30.43684027777778, "d"), HOURS(3600, 24, "h"), MINUTES(60, 60, "m"), SECONDS(1, 60, "s");

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
		Pattern getLast = Pattern.compile("(&?#[A-Fa-f0-9k-oK-ORrXxUu]{6}|§[Xx](§[A-Fa-f0-9k-oK-ORrXxUu]){6}|§[A-Fa-f0-9k-oK-ORrXxUu]|&[Uu])");

		/**
		 * @apiNote Generates random color depends on software & version
		 */
		public default String generateColor() {
			StringContainer b = new StringContainer(7).append("#");
			for (int i = 0; i < 6; ++i)
				b.append(characters[random.nextInt(16)]);
			return b.toString();
		}

		/**
		 * @apiNote @see {@link API#basics()}
		 */
		public default String[] getLastColors(String text) {
			return API.basics().getLastColors(getLast, text);
		}

		/**
		 * @apiNote Replace #RRGGBB hex color depends on software
		 */
		public default String replaceHex(String text) {
			StringContainer container = new StringContainer(text.length() + 14 * 6);

			boolean HEX_CHAR = false;
			StringContainer hex = new StringContainer(6);
			for (int i = 0; i < text.length(); ++i) {
				char c = text.charAt(i);
				if (c == '#') {
					if (HEX_CHAR) {
						container.append('#').append(hex);
						hex.clear();
						continue;
					}
					HEX_CHAR = true;
					continue;
				}
				if (HEX_CHAR) {
					if (c >= 64 && c <= 70 || c >= 97 && c <= 102 || c >= 48 && c <= 57) { // color
						hex.append(c);
						if (hex.length() == 6) {
							HEX_CHAR = false;
							container.append('§').append('x');
							for (int hexPos = 0; hexPos < 6; ++hexPos)
								container.append('§').append(hex.charAt(hexPos));
							hex.clear();
						}
						continue;
					}
					HEX_CHAR = false;
					container.append('#').append(hex);
					hex.clear();
				}
				container.append(c);
			}
			if (HEX_CHAR)
				container.append('#').append(hex);
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
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1000000) + "m";
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
		double r = (StringUtils.random.nextLong() >>> 11) * 0x1.0p-53;
		boolean isNegative = max < 0;
		if (isNegative) {
			max *= -1;
			min *= -1;
		}
		r = r * max;
		if (r >= max) // may need to correct a rounding problem
			r = Double.longBitsToDouble(Double.doubleToLongBits(max) - 1);
		else if (r < min)
			r = min;
		return isNegative ? r * -1 : r;
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
		int r = random.nextInt();
		if (min < max) {
			// It's not case (1).
			final int n = max - min;
			final int m = n - 1;
			if ((n & m) == 0)
				// It is case (2): length of range is a power of 2.
				r = (r & m) + min;
			else if (n > 0) {
				// It is case (3): need to reject over-represented candidates.
				for (int u = r >>> 1; u + m - (r = u % n) < 0; u = random.nextInt() >>> 1)
					;
				r += min;
			} else
				// It is case (4): length of range not representable as long.
				while (r < min || r >= max)
					r = random.nextInt();
		}
		return isNegative ? r * -1 : r;
	}

	/**
	 * @apiNote Split text correctly with colors
	 */
	public static List<String> fixedSplit(String text, int lengthOfSplit) {
		if (text == null)
			return null;
		List<String> splitted = new ArrayList<>();
		String split = text;
		String prefix = "";
		while (split.length() > lengthOfSplit) {
			int length = lengthOfSplit - 1 - prefix.length();
			String a = prefix + split.substring(0, length);
			if (a.endsWith("§")) {
				--length;
				a = prefix + split.substring(0, length);
			}
			String[] last = StringUtils.getLastColorsSplitFormats(a);
			prefix = (!last[0].isEmpty() ? "§" + last[0] : "") + (!last[1].isEmpty() ? "§" + last[1] : "");
			splitted.add(a);
			split = split.substring(length);
		}
		if (!(prefix + split).isEmpty())
			splitted.add(prefix + split);
		return splitted;
	}

	/**
	 * @apiNote Copy matches of String from Iterable<String>
	 * @return List<String>
	 */
	public static List<String> copyPartialMatches(String prefix, Iterable<String> originals) {
		List<String> collection = new ArrayList<>();
		for (String string : originals)
			if (string == null || string.length() < prefix.length() ? false : string.regionMatches(true, 0, prefix, 0, prefix.length()))
				collection.add(string);
		return collection;
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
		boolean charColor = false;
		for (int i = 0; i < original.length(); ++i) {
			char c = original.charAt(i);
			if (charColor) {
				if (StringUtils.has(c))
					builder.append('§').append(StringUtils.lower(c));
				else
					builder.append('&').append(c);
				charColor = false;
				continue;
			}
			if (c == '&') {
				charColor = true;
				continue;
			}
			charColor = false;
			builder.append(c);
		}
		String msg = builder.toString();
		if (StringUtils.color != null && /** Fast check for working #RRGGBB symbol **/
				(!Ref.serverType().isBukkit() || Ref.isNewerThan(15))) {
			msg = StringUtils.gradient(msg, protectedStrings);
			if (msg.contains("#"))
				msg = StringUtils.color.replaceHex(msg);
		}
		if (msg.contains("&u") && StringUtils.color != null)
			msg = StringUtils.color.rainbow(msg, StringUtils.color.generateColor(), StringUtils.color.generateColor(), protectedStrings);
		return msg;
	}

	private static boolean has(int c) {
		return c <= 102 && c >= 97 || c <= 57 && c >= 48 || c <= 70 && c >= 65 || c <= 79 && c >= 75 || c <= 111 && c >= 107 || c == 114 || c == 82 || c == 88 || c == 120;
	}

	private static char lower(int c) {
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
		boolean digit = split.equals(":");

		if (period == 0L)
			return digit ? "0" : StringUtils.timeConvertor.get(TimeFormat.SECONDS).toString(0);

		boolean skipYear = false;
		boolean skipMonth = false;
		boolean skipDay = false;
		boolean skipHour = false;
		boolean skipMinute = false;
		boolean skipSecond = false;
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
			return digit ? period + "" : StringUtils.timeConvertor.get(TimeFormat.SECONDS).toString(period);

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
		StringContainer builder = new StringContainer(split.length() + 32);
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

		if (StringUtils.isFloat(period) && !period.endsWith("d") && !period.endsWith("e"))
			return (long) StringUtils.getFloat(period);

		long time = 0;

		if (period.contains(":")) {
			String[] split = period.split(":");
			switch (split.length) {
			case 2: // mm:ss
				time += StringUtils.getFloat(split[0]) * TimeFormat.MINUTES.seconds();
				time += StringUtils.getFloat(split[1]);
				break;
			case 3: // hh:mm:ss
				time += StringUtils.getFloat(split[0]) * TimeFormat.HOURS.seconds();
				time += StringUtils.getFloat(split[1]) * TimeFormat.MINUTES.seconds();
				time += StringUtils.getFloat(split[2]);
				break;
			case 4: // dd:hh:mm:ss
				time += StringUtils.getFloat(split[0]) * TimeFormat.DAYS.seconds();
				time += StringUtils.getFloat(split[1]) * TimeFormat.HOURS.seconds();
				time += StringUtils.getFloat(split[2]) * TimeFormat.MINUTES.seconds();
				time += StringUtils.getFloat(split[3]);
				break;
			case 5: // mm:dd:hh:mm:ss
				time += StringUtils.getFloat(split[0]) * TimeFormat.MONTHS.seconds();
				time += StringUtils.getFloat(split[1]) * TimeFormat.DAYS.seconds();
				time += StringUtils.getFloat(split[2]) * TimeFormat.HOURS.seconds();
				time += StringUtils.getFloat(split[3]) * TimeFormat.MINUTES.seconds();
				time += StringUtils.getFloat(split[4]);
				break;
			default: // yy:mm:dd:hh:mm:ss
				time += StringUtils.getFloat(split[0]) * TimeFormat.YEARS.seconds();
				time += StringUtils.getFloat(split[1]) * TimeFormat.MONTHS.seconds();
				time += StringUtils.getFloat(split[2]) * TimeFormat.DAYS.seconds();
				time += StringUtils.getFloat(split[3]) * TimeFormat.HOURS.seconds();
				time += StringUtils.getFloat(split[4]) * TimeFormat.MINUTES.seconds();
				time += StringUtils.getFloat(split[5]);
				break;
			}
			return time;
		}

		for (TimeFormat format : TimeFormat.values()) {
			Matcher matcher = StringUtils.timeConvertor.get(format).matcher(period);
			while (matcher.find()) {
				time += StringUtils.getLong(matcher.group()) * format.seconds();
				period = matcher.replaceFirst("");
			}
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
	 * @apiNote Convert String to Math and Calculate exempt
	 * @return double
	 */
	public static double calculate(String original) {
		String val = original;

		if (val.indexOf('(') != -1 && val.indexOf(')') != -1)
			val = splitter(val);

		if (val.indexOf('*') != -1 || val.indexOf('/') != -1) {
			Matcher s = extra.matcher(val);
			while (s.find()) {
				double a = StringUtils.getDouble(s.group(1));
				String b = s.group(3);
				double d = StringUtils.getDouble(s.group(4));
				val = val.replace(s.group(), (a == 0 || d == 0 ? 0 : b.charAt(0) == '*' ? a * d : a / d) + "");
				s.reset(val);
			}
		}
		if (val.indexOf('+') != -1 || val.indexOf('-') != -1) {
			Matcher s = normal.matcher(val);
			while (s.find()) {
				double a = StringUtils.getDouble(s.group(1));
				String b = s.group(3);
				double d = StringUtils.getDouble(s.group(4));
				val = val.replace(s.group(), (b.charAt(0) == '+' ? a + d : a - d) + "");
				s.reset(val);
			}
		}
		return StringUtils.getDouble(val);
	}

	private static String splitter(String s) {
		StringContainer result = new StringContainer(s.length());
		StringContainer subResult = null;

		int inside = 0;

		for (int pos = 0; pos < s.length(); ++pos) {
			char c = s.charAt(pos);
			if (c == '(') {
				if (inside == 0)
					subResult = new StringContainer(s.length());
				else
					subResult.append(c);

				++inside;
			} else if (c == ')') {
				if (inside > 1)
					subResult.append(c);

				if (--inside == 0) {
					result.append("" + calculate(subResult.toString()));
					subResult.clear();
				}
			} else if (inside == 0)
				result.append(c);
			else
				subResult.append(c);
		}
		return result.toString();
	}

	/**
	 * @apiNote Get double from string
	 * @return double
	 */
	public static double getDouble(String fromString) {
		if (fromString == null)
			return 0.0D;
		String a = fromString.replaceAll("[^+0-9E.,-]+", "").replace(",", ".");
		try {
			return Double.parseDouble(a);
		} catch (NumberFormatException e) {
		}
		return 0.0D;
	}

	/**
	 * @apiNote Is string, double ?
	 * @return boolean
	 */
	public static boolean isDouble(String fromString) {
		try {
			Double.parseDouble(fromString);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * @apiNote Get long from string
	 * @return long
	 */
	public static long getLong(String fromString) {
		if (fromString == null)
			return 0L;
		String a = fromString.replaceAll("[^+0-9E.,-]+", "").replace(",", ".");
		try {
			return Long.parseLong(a);
		} catch (NumberFormatException e) {
		}
		return 0L;
	}

	/**
	 * @apiNote Is string, long ?
	 * @return boolean
	 */
	public static boolean isLong(String fromString) {
		try {
			Long.parseLong(fromString);
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}

	/**
	 * @apiNote Get int from string
	 * @return int
	 */
	public static int getInt(String fromString) {
		if (fromString == null)
			return 0;
		String a = fromString.replaceAll("[^+0-9E.,-]+", "").replace(",", ".");
		if (!a.contains(".")) {
			try {
				return Integer.parseInt(a);
			} catch (NumberFormatException e) {
			}
			try {
				return (int) Long.parseLong(a);
			} catch (NumberFormatException e) {
			}
		}
		try {
			return (int) Double.parseDouble(a);
		} catch (NumberFormatException e) {
		}
		return 0;
	}

	/**
	 * @apiNote Is string, integer ?
	 * @return boolean
	 */
	public static boolean isInt(String fromString) {
		try {
			Integer.parseInt(fromString);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * @apiNote Is string, float ?
	 * @return boolean
	 */
	public static boolean isFloat(String fromString) {
		try {
			Float.parseFloat(fromString);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * @apiNote Get float from string
	 * @return float
	 */
	public static float getFloat(String fromString) {
		if (fromString == null)
			return 0F;
		String a = fromString.replaceAll("[^+0-9E.,-]+", "").replace(",", ".");
		try {
			return Float.parseFloat(a);
		} catch (NumberFormatException e) {
		}
		return 0;
	}

	/**
	 * @apiNote Is string, float ?
	 * @return boolean
	 */
	public static boolean isByte(String fromString) {
		try {
			Byte.parseByte(fromString);
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}

	/**
	 * @apiNote Get float from string
	 * @return float
	 */
	public static byte getByte(String fromString) {
		if (fromString == null)
			return (byte) 0;
		String a = fromString.replaceAll("[^+0-9E-]+", "");
		try {
			return Byte.parseByte(a);
		} catch (NumberFormatException e) {
		}
		return 0;
	}

	/**
	 * @apiNote Is string, float ?
	 * @return boolean
	 */
	public static boolean isShort(String fromString) {
		try {
			Short.parseShort(fromString);
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}

	/**
	 * @apiNote Get float from string
	 * @return float
	 */
	public static short getShort(String fromString) {
		if (fromString == null)
			return (short) 0;
		String a = fromString.replaceAll("[^+0-9E-]+", "");
		try {
			return Short.parseShort(a);
		} catch (NumberFormatException e) {
		}
		return 0;
	}

	/**
	 * @apiNote Is string, number ?
	 * @return boolean
	 */
	public static boolean isNumber(String fromString) {
		return StringUtils.isInt(fromString) || StringUtils.isDouble(fromString) || StringUtils.isLong(fromString) || StringUtils.isByte(fromString) || StringUtils.isShort(fromString)
				|| StringUtils.isFloat(fromString);
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

	public static boolean containsSpecial(String value) {
		return StringUtils.special.matcher(value).find();
	}

	public static Number getNumber(String o) {
		if (o == null)
			return null;
		if (!o.contains(".")) {
			if (StringUtils.isInt(o))
				return StringUtils.getInt(o);
			if (StringUtils.isLong(o))
				return StringUtils.getLong(o);
			if (StringUtils.isByte(o))
				return StringUtils.getByte(o);
			if (StringUtils.isShort(o))
				return StringUtils.getShort(o);
		}
		if (StringUtils.isDouble(o))
			return StringUtils.getDouble(o);
		if (StringUtils.isFloat(o))
			return StringUtils.getFloat(o);
		return null;
	}
}
