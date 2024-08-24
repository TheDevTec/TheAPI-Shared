package me.devtec.shared.utility.colors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.devtec.shared.dataholder.StringContainer;

@Deprecated
public class RegexFinder implements GradientFinder {

	private static Pattern pattern;
	private static int firstLength;
	private static int secondLength;

	public static void init(String prefix1, String suffix1, String prefix2, String suffix2) {
		firstLength = prefix1.length() + suffix1.length();
		secondLength = prefix2.length() + suffix2.length();
		pattern = Pattern.compile(prefix1 + "(#[A-Fa-f0-9]{6}|§x(§[0-9A-Fa-f]){6})" + suffix1 + "(.*?)" + prefix2 + "(#[A-Fa-f0-9]{6}|§x(§[0-9A-Fa-f]){6})" + suffix2 + "|.*?(?=(?:" + prefix1
				+ "(#[A-Fa-f0-9]{6}|§x(§[0-9A-Fa-f]){6})" + suffix1 + ".*?" + prefix2 + "(#[A-Fa-f0-9]{6}|§x(§[0-9A-Fa-f]){6})" + suffix2 + "))");
	}

	private int skipChars;
	private final Matcher matcher;
	// Match
	private String firstHex;
	private int firstHexLength;
	private String secondHex;
	private int secondHexLength;
	private int startAt;
	private int endAt;

	// <prefix1>#rrggbb<suffix1> text <prefix2>#rrggbb<suffix2>
	public RegexFinder(StringContainer container) {
		matcher = pattern.matcher(container);
	}

	@Override
	public boolean find() {
		boolean match;
		if (skipChars != 0) {
			match = matcher.find(skipChars);
			skipChars = 0;
		} else
			match = matcher.find();
		if (match) {
			if (matcher.groupCount() == 0 || matcher.group().isEmpty())
				return find();
			firstHex = matcher.group(1);
			firstHexLength = firstHex.length() + firstLength;
			secondHex = matcher.group(4);
			secondHexLength = secondHex.length() + secondLength;

			startAt = matcher.start() + firstHexLength;
			endAt = matcher.end() - secondHexLength;
		}
		return match;
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
		skipChars = endAt + characters;
	}

}
