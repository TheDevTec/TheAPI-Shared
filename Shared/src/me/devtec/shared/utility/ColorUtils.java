package me.devtec.shared.utility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devtec.shared.API;
import me.devtec.shared.Ref;
import me.devtec.shared.dataholder.StringContainer;

public class ColorUtils {

	// INIT THIS
	public static ColormaticFactory color;
	public static Pattern rainbowSplit;
	// COLOR UTILS
	public static Pattern gradientFinder;

	// VARRIABLE INIT
	public static Map<String, String> colorMap = new HashMap<>();
	public static String tagPrefix = "!";

	public interface ColormaticFactory {
		char[] characters = { 'a', 'b', 'c', 'd', 'e', 'f', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };

		/**
		 * @apiNote Generates random color depends on software & version
		 */
		public default String generateColor() {
			StringContainer b = new StringContainer(7).append('#');
			for (int i = 0; i < 6; ++i)
				b.append(characters[StringUtils.random.nextInt(16)]);
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
				if (c == '&' && i + 7 < text.length() && text.charAt(i + 1) == '#') {
					boolean isHex = true;
					for (int ic = 2; ic < 8; ++ic) {
						char cn = text.charAt(i + ic);
						if (!(cn >= 64 && cn <= 70 || cn >= 97 && cn <= 102 || cn >= 48 && cn <= 57)) {
							isHex = false;
							break;
						}
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
				} else if (c == '#' && i + 6 < text.length()) {
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
							container.append('§').append(toLowerCase(cn));
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

	/**
	 * @apiNote Return joined strings ([0] + [1]) from
	 *          {@link ColorUtils#getLastColorsSplitFormats(String)}
	 * @param text Input string
	 * @return String
	 */
	public static String getLastColors(String text) {
		String[] split = color.getLastColors(text);
		return (split[0] == null ? "" : split[0]) + (split[1] == null ? "" : split[1]);
	}

	/**
	 * @apiNote Get last colors from String (HEX SUPPORT!)
	 * @param text Input string
	 * @return String[]
	 */
	public static String[] getLastColorsSplitFormats(String text) {
		return color.getLastColors(text);
	}

	/**
	 * @apiNote Replace gradients in the List of strings
	 * @param list Input list of strings to colorize
	 * @return List<String>
	 */
	public static List<String> gradient(List<String> list) {
		list.replaceAll(ColorUtils::gradient);
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
		if (originalMsg == null || ColorUtils.gradientFinder == null)
			return originalMsg;

		String legacyMsg = originalMsg;

		String low = legacyMsg.toLowerCase();
		for (Entry<String, String> code : ColorUtils.colorMap.entrySet()) {
			String rawCode = (ColorUtils.tagPrefix + code.getKey()).toLowerCase();
			if (!low.contains(rawCode))
				continue;
			legacyMsg = legacyMsg.replace(rawCode, ColorUtils.color.replaceHex(code.getValue()));
		}
		Matcher matcher = ColorUtils.gradientFinder.matcher(legacyMsg);
		while (matcher.find()) {
			if (matcher.groupCount() == 0 || matcher.group().isEmpty())
				continue;
			String replace = ColorUtils.color.gradient(matcher.group(3), matcher.group(1), matcher.group(4), protectedStrings);
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
		list.replaceAll(ColorUtils::colorize);
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
		if (ColorUtils.color != null) {
			if (!Ref.serverType().isBukkit() || Ref.isNewerThan(15)) { // Non bukkit software or 1.16+
				msg = ColorUtils.gradient(msg, protectedStrings);
				if (msg.indexOf('#') != -1)
					msg = ColorUtils.color.replaceHex(msg);
			}
			if (msg.contains("&u"))
				msg = ColorUtils.color.rainbow(msg, null, null, protectedStrings);
		}
		return msg;
	}

	private static boolean isColorChar(int c) {
		return c <= 102 && c >= 97 || c <= 57 && c >= 48 || c <= 70 && c >= 65 || c <= 79 && c >= 75 || c <= 111 && c >= 107 || c == 114 || c == 82 || c == 120;
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
		case 85:
		case 88:
			return (char) (c + 32);
		default:
			return (char) c;
		}
	}
}
