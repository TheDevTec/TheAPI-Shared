package me.devtec.shared.utility;

import java.util.List;

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

	// Finder of gradients
	public static ClassConstructor gradientFinderConstructor;

	// Replace of hex colors
	public static HexReplacer hexReplacer;

	// Chat tags
	private static Branch[] base = {};
	private static int baseSize;

	/*
	 * Fast lookup for first ASCII character of registered tags.
	 *
	 * Registration is cold-path, lookup can be hot-path.
	 */
	private static final Branch[] ASCII_BASE = new Branch[128];

	public static void registerColorTag(String replace, String value) {
		if (replace == null || replace.isEmpty())
			throw new IllegalArgumentException("Color tag cannot be null or empty");

		char first = lower(replace.charAt(0));
		Branch current = findBranchFor(first);

		if (current == null) {
			current = new Branch(first, null);

			Branch[] old = base;
			Branch[] expanded = new Branch[old.length + 1];

			System.arraycopy(old, 0, expanded, 0, old.length);

			expanded[old.length] = current;

			base = expanded;
			baseSize = expanded.length;

			if (first < ASCII_BASE.length)
				ASCII_BASE[first] = current;
		}

		for (int i = 1, length = replace.length(); i < length; ++i) {
			char c = lower(replace.charAt(i));

			Branch next = findBranch(current.sub, c);

			if (next == null) {
				next = new Branch(c, null);

				if (current.sub == null)
					current.sub = new Branch[] { next };
				else {
					Branch[] old = current.sub;
					Branch[] expanded = new Branch[old.length + 1];

					System.arraycopy(old, 0, expanded, 0, old.length);

					expanded[old.length] = next;
					current.sub = expanded;
				}
			}

			current = next;
		}

		/*
		 * Preserve original behavior: first registered value wins.
		 */
		if (current.value == null) {
			current.value = value;
			current.length = replace.length();
		}
	}

	private static Branch findBranchFor(char c) {
		if (c < ASCII_BASE.length) {
			Branch result = ASCII_BASE[c];

			if (result != null)
				return result;
		}

		for (Branch branch : base)
			if (branch.c == c)
				return branch;

		return null;
	}

	private static Branch findBranch(Branch[] branches, char c) {
		if (branches == null)
			return null;

		for (Branch branch : branches)
			if (branch.c == c)
				return branch;

		return null;
	}

	/*
	 * Checks whether a real registered tag begins at start.
	 *
	 * Important: !#123456 must NOT be considered a tag just because tagPrefix is
	 * "!".
	 */
	private static Branch findTagEnd(char[] value, int length, int start) {
		if (start < 0 || start >= length)
			return null;

		Branch current = findBranchFor(lower(value[start]));

		if (current == null)
			return null;

		Branch best = current.value == null ? null : current;

		for (int i = start + 1; i < length && current.sub != null; ++i) {
			current = findBranch(current.sub, lower(value[i]));

			if (current == null)
				break;

			if (current.value != null)
				best = current;
		}

		return best;
	}

	private static Branch findTagEnd(StringContainer container, int start) {
		int length = container.length();

		if (start < 0 || start >= length)
			return null;

		Branch current = findBranchFor(lower(container.charAt(start)));

		if (current == null)
			return null;

		Branch best = current.value == null ? null : current;

		for (int i = start + 1; i < length && current.sub != null; ++i) {
			current = findBranch(current.sub, lower(container.charAt(i)));

			if (current == null)
				break;

			if (current.value != null)
				best = current;
		}

		return best;
	}

	public interface ColormaticFactory {

		char[] chars = { 'a', 'b', 'c', 'd', 'e', 'f', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };

		default String generateColor() {
			seed ^= seed << 21;
			seed ^= seed >>> 35;
			seed ^= seed << 4;

			return new String(new char[] { '#', chars[(int) seed & 0xF], chars[(int) seed >> 4 & 0xF],
					chars[(int) seed >> 8 & 0xF], chars[(int) seed >> 12 & 0xF], chars[(int) seed >> 16 & 0xF],
					chars[(int) seed >> 20 & 0xF] });
		}

		default String[] getLastColors(String text) {
			return API.basics().getLastColors(text);
		}

		default String replaceHex(String text) {
			if (text == null || text.isEmpty() || hexReplacer == null)
				return text;

			return replaceHex(new StringContainer(text, 0, 28)).toString();
		}

		default StringContainer replaceHex(StringContainer text) {
			if (text == null || text.isEmpty() || hexReplacer == null)
				return text;

			hexReplacer.apply(text, 0, text.length());

			return text;
		}

		default String gradient(String msg, @Nullable String firstHex, @Nullable String secondHex,
				@Nullable List<String> protectedStrings) {

			if (msg == null || msg.isEmpty())
				return msg;

			return gradient(new StringContainer(msg), 0, msg.length(), firstHex, secondHex, protectedStrings)
					.toString();
		}

		default String rainbow(String msg, @Nullable String firstHex, @Nullable String secondHex,
				@Nullable List<String> protectedStrings) {

			if (msg == null || msg.isEmpty())
				return msg;

			return rainbow(new StringContainer(msg), 0, msg.length(), firstHex, secondHex, protectedStrings).toString();
		}

		default StringContainer gradient(StringContainer container, int start, int end, @Nullable String firstHex,
				@Nullable String secondHex, @Nullable List<String> protectedStrings) {

			API.basics().gradient(container, start, end, firstHex, secondHex, protectedStrings);

			return container;
		}

		default StringContainer rainbow(StringContainer container, int start, int end, @Nullable String firstHex,
				@Nullable String secondHex, @Nullable List<String> protectedStrings) {

			API.basics().rainbow(container, start, end, firstHex, secondHex, protectedStrings);

			return container;
		}
	}

	public static String getLastColors(String text) {
		String[] split = color.getLastColors(text);

		if (split[0] == null)
			return split[1] == null ? "" : split[1];

		if (split[1] == null)
			return split[0];

		return split[0] + split[1];
	}

	public static String[] getLastColorsSplitFormats(String text) {
		return color.getLastColors(text);
	}

	public static List<String> gradient(List<String> list) {
		if (list != null)
			list.replaceAll(ColorUtils::gradient);

		return list;
	}

	public static String gradient(String originalMsg) {
		return gradient(originalMsg, null);
	}

	public static String gradient(String text, List<String> protectedStrings) {

		if (text == null || text.isEmpty() || gradientFinderConstructor == null)
			return text;

		return gradient(new StringContainer(text), protectedStrings).toString();
	}

	public static StringContainer gradient(StringContainer container, List<String> protectedStrings) {

		if (container == null || container.isEmpty() || gradientFinderConstructor == null)
			return container;

		internalGradient(container, findRegisteredTag(container, 0), protectedStrings);

		return container;
	}

	/*
	 * Returns how many raw #RRGGBB colors were consumed as gradient delimiters.
	 *
	 * colorize() uses this to avoid running hexReplacer over the already-expanded
	 * §x... output when no raw hex remains.
	 */
	private static int internalGradient(StringContainer container, int tagAt, List<String> protectedStrings) {

		if (gradientFinderConstructor == null)
			return 0;

		if (tagAt >= 0 && baseSize != 0)
			replaceTags(container, tagAt);

		GradientFinder finder = gradientFinderConstructor.matcher(container);

		int consumedRawHex = 0;

		while (finder.find()) {
			int oldLength = container.length();

			int firstLength = finder.getFirstHexLength();
			int secondLength = finder.getSecondHexLength();

			int firstRGB = finder.getFirstRGB();
			int secondRGB = finder.getSecondRGB();

			/*
			 * Direct RGB is currently supplied by fixed finders whose delimiters are raw
			 * #RRGGBB.
			 */
			if (firstRGB >= 0)
				++consumedRawHex;

			if (secondRGB >= 0)
				++consumedRawHex;

			/*
			 * Save positions before mutation.
			 */
			int finderStart = finder.getStart();
			int finderEnd = finder.getEnd();

			boolean direct = false;

			if (firstRGB >= 0 && secondRGB >= 0 && (protectedStrings == null || protectedStrings.isEmpty()))
				direct = API.basics().gradientFastDelimited(container,

						// beginning of first !#xxxxxx marker
						finderStart - firstLength,

						// first character of gradient text
						finderStart,

						// beginning of second !#xxxxxx marker
						finderEnd,

						// immediately after second marker
						finderEnd + secondLength,

						firstRGB, secondRGB);
			if (!direct) {
				container.delete(finderEnd, finderEnd + secondLength).delete(finderStart - firstLength, finderStart);

				int gradientStart = finderStart - firstLength;

				int gradientEnd = finderEnd - firstLength;

				if (firstRGB >= 0 && secondRGB >= 0)
					API.basics().gradient(container, gradientStart, gradientEnd, firstRGB, secondRGB, protectedStrings);
				else
					color.gradient(container, gradientStart, gradientEnd, finder.getFirstHex(), finder.getSecondHex(),
							protectedStrings);
			}

			finder.skip(container.length() - oldLength);
		}

		return consumedRawHex;
	}

	private static int findRegisteredTag(StringContainer container, int from) {

		if (baseSize == 0 || tagPrefix == null || tagPrefix.isEmpty())
			return -1;

		int length = container.length();
		int prefixLength = tagPrefix.length();

		int position = Math.max(0, from);

		while (position < length) {
			position = prefixLength == 1 ? container.indexOf(tagPrefix.charAt(0), position)
					: container.indexOf(tagPrefix, position);

			if (position == -1)
				return -1;

			if (findTagEnd(container, position) != null)
				return position;

			++position;
		}

		return -1;
	}

	private static void replaceTags(StringContainer container, int startAt) {

		String prefix = tagPrefix;

		if (prefix == null || prefix.isEmpty() || baseSize == 0)
			return;

		int prefixLength = prefix.length();
		int position = Math.max(0, startAt);

		while (position < container.length()) {
			position = prefixLength == 1 ? container.indexOf(prefix.charAt(0), position)
					: container.indexOf(prefix, position);

			if (position == -1)
				return;

			Branch best = findTagEnd(container, position);

			if (best == null) {
				++position;
				continue;
			}

			String replacement = best.value;

			container.replace(position, position + best.length, replacement);

			position += replacement.length();
		}
	}

	public static List<String> colorize(List<String> list) {
		return colorize(list, null);
	}

	public static List<String> colorize(List<String> list, List<String> protectedStrings) {

		if (list != null)
			list.replaceAll(string -> colorize(string, protectedStrings));

		return list;
	}

	public static List<StringContainer> colorizeCont(List<StringContainer> list) {

		return colorizeCont(list, null);
	}

	public static List<StringContainer> colorizeCont(List<StringContainer> list, List<String> protectedStrings) {

		if (list != null)
			list.replaceAll(string -> colorize(string, protectedStrings));

		return list;
	}

	public static String colorize(String original) {
		return colorize(original, null);
	}

	public static String strip(String text) {
		if (text == null || text.isEmpty())
			return text;

		return strip(new StringContainer(text)).toString();
	}

	/*
	 * O(n) compaction.
	 *
	 * No repeated delete() / array shifting.
	 */
	public static StringContainer strip(StringContainer container) {

		if (container == null || container.isEmpty())
			return container;

		char[] value = container.getValueWithoutTrim();
		int length = container.length();

		int write = 0;
		int read = 0;

		while (read < length) {
			char c = value[read];

			if (c == '§' && read + 1 < length && isColorChar(value[read + 1])) {

				read += 2;
				continue;
			}

			value[write++] = c;
			++read;
		}

		if (write != length)
			container.delete(write, length);

		return container;
	}

	public static String colorize(String text, List<String> protectedStrings) {

		if (text == null || text.isEmpty())
			return text;

		return colorize(new StringContainer(text, 0, 24), protectedStrings).toString();
	}

	public static StringContainer colorize(StringContainer container, List<String> protectedStrings) {

		if (container == null || container.isEmpty())
			return container;

		char[] value = container.getValueWithoutTrim();
		int length = container.length();

		boolean rainbow = false;

		/*
		 * Number of actual raw #RRGGBB occurrences.
		 *
		 * This lets us skip hexReplacer after a gradient consumed all of them.
		 */
		int rawHexCount = 0;

		/*
		 * First REAL registered tag.
		 *
		 * !#123456 must not trigger replaceTags() just because tagPrefix is "!".
		 */
		int tagAt = -1;

		String prefix = tagPrefix;

		boolean searchTags = baseSize != 0 && prefix != null && !prefix.isEmpty();

		char prefixFirst = searchTags ? prefix.charAt(0) : 0;

		for (int i = 0; i < length; ++i) {
			char c = value[i];

			if (tagAt == -1 && searchTags && c == prefixFirst && findTagEnd(value, length, i) != null)
				tagAt = i;

			if (c == '#') {
				if (isHexColorAt(value, i, length))
					++rawHexCount;

				continue;
			}

			if (c != '&' || i + 1 >= length)
				continue;

			char next = value[i + 1];

			if (isColorChar(next)) {
				value[i] = '§';
				value[i + 1] = lower(next);

				++i;
				continue;
			}

			if (next == 'u') {
				rainbow = true;

				++i;
				continue;
			}

			if (next == '#') {
				if (isHexColorAt(value, i + 1, length))
					++rawHexCount;

				/*
				 * Skip '#'. Otherwise next iteration would count it again.
				 */
				++i;
			}
		}

		ColormaticFactory factory = color;

		if (factory == null)
			return container;

		/*
		 * Do not call Ref checks unless there is actually something requiring modern
		 * color support.
		 */
		if (rawHexCount != 0 || tagAt >= 0) {
			boolean modern = !Ref.type().isBukkit() || Ref.isAtLeast(16, 0);

			if (modern) {
				int consumedRawHex = internalGradient(container, tagAt, protectedStrings);

				/*
				 * Example:
				 *
				 * rawHexCount = 2 consumedRawHex = 2
				 *
				 * Both #RRGGBB values belonged to the gradient.
				 *
				 * The resulting container now contains hundreds of §x§R§R... chars. Scanning
				 * that again using hexReplacer would be pure overhead.
				 *
				 * A tag replacement may introduce a new #RRGGBB, therefore tagAt >= 0 keeps the
				 * replacer enabled.
				 */
				if (hexReplacer != null && (tagAt >= 0 || rawHexCount > consumedRawHex))
					factory.replaceHex(container);
			}
		}

		if (rainbow)
			factory.rainbow(container, 0, container.length(), null, null, protectedStrings);

		return container;
	}

	private static boolean isHexColorAt(char[] value, int index, int length) {

		return index >= 0 && index + 6 < length && value[index] == '#' && isHex(value[index + 1])
				&& isHex(value[index + 2]) && isHex(value[index + 3]) && isHex(value[index + 4])
				&& isHex(value[index + 5]) && isHex(value[index + 6]);
	}

	private static boolean isHex(char c) {
		return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
	}

	private static char lower(char c) {
		/*
		 * Everything relevant to Minecraft color syntax is ASCII. Avoid
		 * Character.toLowerCase() in the hot path.
		 */
		if (c >= 'A' && c <= 'Z')
			return (char) (c + 32);

		return c;
	}

	private static boolean isColorChar(int c) {
		return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F' || c >= 'k' && c <= 'o'
				|| c >= 'K' && c <= 'O' || c == 'r' || c == 'R' || c == 'x';
	}
}