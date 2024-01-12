package me.devtec.shared.utility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.devtec.shared.API;
import me.devtec.shared.Ref;
import me.devtec.shared.annotations.Nullable;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.utility.colors.Branch;
import me.devtec.shared.utility.colors.ClassConstructor;
import me.devtec.shared.utility.colors.GradientFinder;
import me.devtec.shared.utility.colors.HexReplacer;

public class ColorUtils {

	public static ColormaticFactory color = new ColormaticFactory() {
	};

	public static String tagPrefix = "!";

	private static long seed = MathUtils.random.nextLong();

	// Finder of gradients (like <#hex>text</hex>)
	public static ClassConstructor gradientFinderConstructor;

	// Replace of hex colors (like &#rrggbb or <#rrggbb>)
	public static HexReplacer hexReplacer;

	// Chat Tags (like !fire)
	public static Map<Character, Branch[]> colorMapTree = new HashMap<>();

	public static void registerColorTag(String tag, String hex) {
		Branch[] branch = ColorUtils.colorMapTree.get(tag.charAt(0));
		if (branch == null)
			ColorUtils.colorMapTree.put(tag.charAt(0), branch = new Branch[] {});
		Branch parent = null;
		int pos = 1;
		charLoop: for (char c : tag.substring(1).toLowerCase().toCharArray()) {
			++pos;
			for (Branch br : branch)
				if (br.getKey() == c) {
					parent = br;
					branch = br.getBranches();
					if (branch == null) {
						br.setBranches(branch = new Branch[] {});
						continue charLoop;
					}
					continue charLoop;
				}
			Branch[] copy = new Branch[branch.length + 1];
			System.arraycopy(branch, 0, copy, 0, branch.length);
			Branch br;
			copy[branch.length] = br = new Branch(c);
			if (pos == tag.length())
				br.setValue(hex);
			if (parent != null)
				parent.setBranches(copy);
			else
				ColorUtils.colorMapTree.put(tag.charAt(0), copy);
			if (pos != tag.length()) {
				parent = br;
				br.setBranches(branch = new Branch[] {});
			}
		}
	}

	public interface ColormaticFactory {
		char[] chars = { 'a', 'b', 'c', 'd', 'e', 'f', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };

		/**
		 * @apiNote Generates random color depends on software & version
		 * @author justADeni
		 */
		public default String generateColor() {
			seed ^= seed << 21;
			seed ^= seed >>> 35;
			seed ^= seed << 4;
			return new String(new char[] { '#', chars[(int) seed & 0xF], chars[(int) seed >> 4 & 0xF], chars[(int) seed >> 8 & 0xF], chars[(int) seed >> 12 & 0xF], chars[(int) seed >> 16 & 0xF],
					chars[(int) seed >> 20 & 0xF] });
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
			return replaceHex(new StringContainer(text, 0, 14 * 2)).toString();
		}

		/**
		 * @apiNote Replace #RRGGBB hex color depends on software
		 */
		public default StringContainer replaceHex(StringContainer text) {
			hexReplacer.apply(text, 0, text.length());
			return text;
		}

		/**
		 * @param protectedStrings List of strings which not be colored via gradient
		 * @apiNote @see {@link API#basics()}
		 */
		public default String gradient(String msg, @Nullable String firstHex, @Nullable String secondHex, @Nullable List<String> protectedStrings) {
			return gradient(new StringContainer(msg), 0, msg.length(), firstHex, secondHex, protectedStrings).toString();
		}

		/**
		 * @param protectedStrings List of strings which not be colored via gradient
		 * @apiNote @see {@link API#basics()}
		 */
		public default String rainbow(String msg, @Nullable String firstHex, @Nullable String secondHex, @Nullable List<String> protectedStrings) {
			return rainbow(new StringContainer(msg), 0, msg.length(), firstHex, secondHex, protectedStrings).toString();
		}

		/**
		 * @param protectedStrings List of strings which not be colored via gradient
		 * @apiNote @see {@link API#basics()}
		 */
		public default StringContainer gradient(StringContainer container, int start, int end, @Nullable String firstHex, @Nullable String secondHex, @Nullable List<String> protectedStrings) {
			API.basics().gradient(container, start, end, firstHex, secondHex, protectedStrings);
			return container;
		}

		/**
		 * @param protectedStrings List of strings which not be colored via gradient
		 * @apiNote @see {@link API#basics()}
		 */
		public default StringContainer rainbow(StringContainer container, int start, int end, @Nullable String firstHex, @Nullable String secondHex, @Nullable List<String> protectedStrings) {
			API.basics().rainbow(container, start, end, firstHex, secondHex, protectedStrings);
			return container;
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
	public static String gradient(String text, List<String> protectedStrings) {
		if (text == null || ColorUtils.gradientFinderConstructor == null)
			return text;
		return gradient(new StringContainer(text), protectedStrings).toString();
	}

	/**
	 * @apiNote Replace gradients in the StringContainer
	 * @param container        Input StringContainer to colorize
	 * @param protectedStrings List of strings which not be colored via gradient
	 */
	public static StringContainer gradient(StringContainer container, List<String> protectedStrings) {
		return internalGradient(container, container.indexOf(tagPrefix), protectedStrings);
	}

	private static StringContainer internalGradient(StringContainer container, int startAt, List<String> protectedStrings) {
		if (ColorUtils.gradientFinderConstructor == null)
			return container;

		if (startAt > -1)
			for (int i = startAt; i < container.length(); ++i) {
				char c = Character.toLowerCase(container.charAt(i));
				Branch[] branch = colorMapTree.get(c);
				int deep = 0;
				while (branch != null && i + deep - 1 < container.length()) {
					c = Character.toLowerCase(container.charAt(i + ++deep));
					for (Branch current : branch) {
						if (current.getKey() == c) {
							branch = current.getBranches();
							if (current.hasValue()) {
								String hex = current.getValue();
								container.replace(i, i + deep + 1, hex);
								i += hex.length();
							}
							break;
						}
						branch = null;
					}
				}
			}

		GradientFinder finder = ColorUtils.gradientFinderConstructor.matcher(container);
		while (finder.find()) {
			int length = container.length();
			// Remove hexs
			container.delete(finder.getEnd(), finder.getEnd() + finder.getSecondHexLength()).delete(finder.getStart() - finder.getFirstHexLength(), finder.getStart());

			// Replace gradients
			ColorUtils.color.gradient(container, finder.getStart() - finder.getFirstHexLength(), finder.getEnd() - finder.getSecondHexLength(), finder.getFirstHex(), finder.getSecondHex(),
					protectedStrings);
			finder.skip(container.length() - length);
		}
		return container;
	}

	/**
	 * @apiNote Colorize List of strings with colors
	 * @param list Texts to colorize
	 * @return List<String>
	 */
	public static List<String> colorize(List<String> list) {
		return colorize(list, null);
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
	 * @apiNote Colorize List of strings with colors
	 * @param list Texts to colorize
	 * @return List<StringContainer>
	 */
	public static List<StringContainer> colorizeCont(List<StringContainer> list) {
		return colorizeCont(list, null);
	}

	/**
	 * @apiNote Colorize List of strings with colors
	 * @param list             Texts to colorize
	 * @param protectedStrings List of strings which not be colored via gradient
	 * @return List<StringContainer>
	 */
	public static List<StringContainer> colorizeCont(List<StringContainer> list, List<String> protectedStrings) {
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
	 * @apiNote Strip colors from the String
	 * @param text Text with colorize
	 * @return String
	 */
	public static String strip(String text) {
		if (text == null || text.isEmpty())
			return text;
		return strip(new StringContainer(text)).toString();
	}

	/**
	 * @apiNote Strip colors from the String
	 * @param container Text with colorize
	 * @return String
	 */
	public static StringContainer strip(StringContainer container) {
		if (container == null || container.isEmpty())
			return container;

		for (int i = 0; i < container.length(); ++i) {
			char c = container.charAt(i);
			if (c == '§' && container.length() > i + 1) {
				char next = container.charAt(i + 1);
				if (isColorChar(next)) {
					container.delete(i, i + 2);
					--i;
				}
				continue;
			}
		}
		return container;
	}

	/**
	 * @apiNote Colorize string with colors
	 * @param text             Text to colorize
	 * @param protectedStrings List of strings which not be colored via gradient
	 * @return String
	 */
	public static String colorize(String text, List<String> protectedStrings) {
		if (text == null || text.isEmpty())
			return text;
		return colorize(new StringContainer(text, 0, 24), protectedStrings).toString();
	}

	/**
	 * @apiNote Colorize string with colors
	 * @param container        Text to colorize
	 * @param protectedStrings List of strings which not be colored via gradient
	 * @return String
	 */
	public static StringContainer colorize(StringContainer container, List<String> protectedStrings) {
		if (container.isEmpty())
			return container;

		boolean foundRainbowChar = false;
		boolean foundHash = false;
		int foundTagPrefix = -2;
		char tagPrefixChar = tagPrefix.charAt(0);
		for (int i = 0; i < container.length(); ++i) {
			char c = container.charAt(i);
			if (c == tagPrefixChar && foundTagPrefix == -2)
				foundTagPrefix = tagPrefix.length() == 1 ? i : container.indexOf(tagPrefix, i);
			switch (c) {
			case '#':
				foundHash = true;
				continue;
			case '&':
				if (container.length() > i + 1) {
					char next = container.charAt(++i);
					if (isColorChar(next)) {
						container.setCharAt(i - 1, '§');
						container.setCharAt(i, Character.toLowerCase(next));
					} else if (next == 'u')
						foundRainbowChar = true;
					else if (next == '#')
						foundHash = true;
				}
			}
		}
		if (ColorUtils.color != null) {
			if (!Ref.serverType().isBukkit() || Ref.isNewerThan(15)) {
				if (foundHash || foundTagPrefix > -1)
					ColorUtils.internalGradient(container, foundTagPrefix, protectedStrings);
				if (foundHash)
					ColorUtils.color.replaceHex(container);
			}
			if (foundRainbowChar)
				ColorUtils.color.rainbow(container, 0, container.length(), null, null, protectedStrings);
		}
		return container;
	}

	private static boolean isColorChar(int c) {
		return c <= 102 && c >= 97 || c <= 57 && c >= 48 || c <= 70 && c >= 65 || c <= 79 && c >= 75 || c <= 111 && c >= 107 || c == 114 || c == 82 || c == 120;
	}
}
