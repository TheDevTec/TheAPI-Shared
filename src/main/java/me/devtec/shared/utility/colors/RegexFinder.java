package me.devtec.shared.utility.colors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devtec.shared.dataholder.StringContainer;

@Deprecated
public class RegexFinder implements GradientFinder {

	private static final String COLOR =
			"(#[A-Fa-f0-9]{6}|§x(?:§[0-9A-Fa-f]){6})";

	private static Pattern pattern;

	private static int firstLength;
	private static int secondLength;

	public static void init(
			String prefix1,
			String suffix1,
			String prefix2,
			String suffix2) {

		firstLength =
				prefix1.length()
				+ suffix1.length();

		secondLength =
				prefix2.length()
				+ suffix2.length();

		pattern = Pattern.compile(
				prefix1
				+ COLOR
				+ suffix1
				+ "(.*?)"
				+ prefix2
				+ COLOR
				+ suffix2);
	}

	private final StringContainer container;
	private final Matcher matcher;

	private int skipChars = -1;

	private String firstHex;
	private String secondHex;

	private int firstHexLength;
	private int secondHexLength;

	private int startAt;
	private int endAt;

	// <prefix1>#rrggbb<suffix1> text <prefix2>#rrggbb<suffix2>
	public RegexFinder(StringContainer container) {
		if (pattern == null)
			throw new IllegalStateException(
					"RegexFinder wasn't initialized.");

		this.container = container;
		matcher = pattern.matcher(container);
	}

	@Override
	public boolean find() {
		boolean match;

		if (skipChars >= 0) {
			int start = skipChars;

			skipChars = -1;

			if (start < 0)
				start = 0;

			if (start >= container.length())
				return false;

			match = matcher.find(start);
		} else
			match = matcher.find();

		if (!match)
			return false;

		firstHex = matcher.group(1);
		secondHex = matcher.group(3);

		firstHexLength =
				firstHex.length()
				+ firstLength;

		secondHexLength =
				secondHex.length()
				+ secondLength;

		startAt =
				matcher.start()
				+ firstHexLength;

		endAt =
				matcher.end()
				- secondHexLength;

		return true;
	}

	@Override
	public String getFirstHex() {
		return firstHex;
	}

	@Override
	public int getFirstHexLength() {
		return firstHexLength;
	}

	@Override
	public String getSecondHex() {
		return secondHex;
	}

	@Override
	public int getSecondHexLength() {
		return secondHexLength;
	}

	@Override
	public int getStart() {
		return startAt;
	}

	@Override
	public int getEnd() {
		return endAt;
	}

	@Override
	public void skip(int characters) {
		int position = endAt + characters;

		skipChars =
				position < 0
				? 0
						: position;
	}
}