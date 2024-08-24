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

	public enum FormatType {
		BASIC, NORMAL, COMPLEX
	}

	/**
	 * @apiNote Format double and remove unused zeros on the end
	 * <p>
	 *          Format types: {@link FormatType#BASIC} 1000.01
	 *          {@link FormatType#NORMAL} 1,000.01 {@link FormatType#COMPLEX}
	 *          {Normal type} + BalanceType (k, m, b, t...)
	 */
	public static String formatDouble(FormatType type, double value) {
		switch (type) {
		case BASIC: {
			boolean minus = false;
			if (value < 0) {
				minus = true;
				value *= -1;
			}

			long integerPart = (long) value;
			long decimalPart = Math.round(Math.abs(value - integerPart) * 100);
			StringContainer sb = new StringContainer(MathUtils.getLongLength(integerPart) + 3);
			if (decimalPart == 100) {
				++integerPart;
				decimalPart = 0;
			}
			sb.append(integerPart);
			if (decimalPart != 0) {
				sb.append('.');
				if (decimalPart < 10) {
					sb.append('0');
					sb.append(decimalPart);
				} else {
					double smaller = (double) decimalPart / 10;
					if (MathUtils.hasDecimal(smaller))
						sb.append(decimalPart);
					else
						sb.append((long) smaller);
				}
			}
			if (minus)
				sb.insert(0, '-');
			return sb.toString();
		}
		case NORMAL: {
			boolean minus = false;
			if (value < 0) {
				minus = true;
				value *= -1;
			}

			long integerPart = (long) value;
			long decimalPart = Math.round(Math.abs(value - integerPart) * 100);

			int width = MathUtils.getLongLength(integerPart);
			StringContainer sb = new StringContainer(width + width / 3 + 3);
			if (decimalPart == 100) {
				++integerPart;
				decimalPart = 0;
			}

			int count = 0;
			do {
				if (count == 3) {
					sb.insert(0, ',');
					count = 0;
				}
				sb.insert(0, integerPart % 10);
				integerPart /= 10;
				count++;
			} while (integerPart > 0);
			if (decimalPart != 0) {
				sb.append('.');
				if (decimalPart < 10) {
					sb.append('0');
					sb.append(decimalPart);
				} else {
					double smaller = (double) decimalPart / 10;
					if (MathUtils.hasDecimal(smaller))
						sb.append(decimalPart);
					else
						sb.append((long) smaller);
				}
			}
			if (minus)
				sb.insert(0, '-');
			return sb.toString();
		}
		case COMPLEX: {
			double testValue = value < 0 ? value * -1 : value;
			if (testValue >= 1.0E63) {
				if (value < 0)
					return "-∞";
				return "∞";
			}
			if (testValue >= 1.0E60)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E60) + "NOV";
			if (testValue >= 1.0E57)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E57) + "OCT";
			if (testValue >= 1.0E54)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E54) + "SEP";
			if (testValue >= 1.0E51)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E51) + "SED";
			if (testValue >= 1.0E48)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E48) + "QUI";
			if (testValue >= 1.0E45)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E45) + "QUA";
			if (testValue >= 1.0E42)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E42) + "tre";
			if (testValue >= 1.0E39)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E39) + "duo";
			if (testValue >= 1.0E36)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E36) + "und";
			if (testValue >= 1.0E33)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E33) + "dec";
			if (testValue >= 1.0E30)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E30) + "non";
			if (testValue >= 1.0E27)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E27) + "oct";
			if (testValue >= 1.0E24)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E24) + "sep";
			if (testValue >= 1.0E21) // No, it's not "sex"...
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E21) + "sex";
			if (testValue >= 1.0E18)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E18) + "qui";
			if (testValue >= 1.0E15)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E15) + "qua";
			if (testValue >= 1.0E12)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E12) + "t";
			if (testValue >= 1.0E9)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E9) + "b";
			if (testValue >= 1.0E6)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1.0E6) + "m";
			if (testValue >= 1000)
				return StringUtils.formatDouble(FormatType.NORMAL, value / 1000) + "k";
			return StringUtils.formatDouble(FormatType.NORMAL, value);
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
	 * @apiNote Join Iterable into one String with split String {@link me.devtec.shared.utility.StringUtils#join(Iterable, String, int, int)}
	 * @param split Split string (defaulty ' ')
	 * @param args  Arguments
	 * @return String
	 */
	public static String join(Iterable<?> args, String split) {
		return StringUtils.join(args, split, 0, -1);
	}

	/**
	 * @apiNote Join Iterable into one String with split String {@link me.devtec.shared.utility.StringUtils#join(Iterable, String, int, int)}
	 * @param split Split string (defaulty ' ')
	 * @param start Start argument (defaulty 0)
	 * @param args  Arguments
	 * @return String
	 */
	public static String join(Iterable<?> args, String split, int start) {
		return StringUtils.join(args, split, start, -1);
	}

	/**
	 * @apiNote Join Iterable into one String with split String
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
	 * @apiNote Join objects into one String with split String {@link me.devtec.shared.utility.StringUtils#join(Object[], String, int, int)}
	 * @param split Split string (defaulty ' ')
	 * @param args  Arguments
	 * @return String
	 */
	public static String join(Object[] args, String split) {
		return StringUtils.join(args, split, 0, args.length);
	}

	/**
	 * @apiNote Join objects into one String with split String {@link me.devtec.shared.utility.StringUtils#join(Object[], String, int, int)
	 * @param split Split string (defaulty ' ')
	 * @param start Start argument (defaulty 0)
	 * @param args  Arguments
	 * @return String
	 */
	public static String join(Object[] args, String split, int start) {
		return StringUtils.join(args, split, start, args.length);
	}

	/**
	 * @apiNote Join objects into one String with split String
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
}
