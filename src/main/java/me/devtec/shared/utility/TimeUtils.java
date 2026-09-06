package me.devtec.shared.utility;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;

import me.devtec.shared.dataholder.StringContainer;

public class TimeUtils {

	public static final Map<TimeFormat, TimeFormatter> timeConvertor = new EnumMap<>(TimeFormat.class);
	public static String timeSplit = " ";

	private static final int ALL_FORMATS = (1 << 6) - 1;
	private static final TimeFormat[] FORMATS = TimeFormat.values();

	private static final long[][] COLON_MULTIPLIERS = {
			null,
			null,
			{ TimeFormat.MINUTES.seconds(), TimeFormat.SECONDS.seconds() },
			{ TimeFormat.HOURS.seconds(), TimeFormat.MINUTES.seconds(), TimeFormat.SECONDS.seconds() },
			{ TimeFormat.DAYS.seconds(), TimeFormat.HOURS.seconds(), TimeFormat.MINUTES.seconds(), TimeFormat.SECONDS.seconds() },
			{ TimeFormat.MONTHS.seconds(), TimeFormat.DAYS.seconds(), TimeFormat.HOURS.seconds(),
				TimeFormat.MINUTES.seconds(), TimeFormat.SECONDS.seconds() },
			{ TimeFormat.YEARS.seconds(), TimeFormat.MONTHS.seconds(), TimeFormat.DAYS.seconds(),
					TimeFormat.HOURS.seconds(), TimeFormat.MINUTES.seconds(), TimeFormat.SECONDS.seconds() }
	};

	public enum TimeFormat {
		YEARS(31556952, 365, "y"),
		MONTHS(2629746, 12, "mon"),
		DAYS(86400, 31, "d"),
		HOURS(3600, 24, "h"),
		MINUTES(60, 60, "m"),
		SECONDS(1, 60, "s");

		private final long seconds;
		private final double cast;
		private final String defaultSuffix;

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
		String toString(long value);

		Matcher matcher(String text);
	}

	public static String timeToString(long period) {
		return timeToString(period, timeSplit, 0);
	}

	public static String timeToString(long period, TimeFormat... disabled) {
		return timeToString(period, timeSplit, disabledMask(disabled));
	}

	public static String timeToString(long period, String split, TimeFormat... disabled) {
		return timeToString(period, split, disabledMask(disabled));
	}

	private static String timeToString(long period, String split, int disabled) {
		boolean digit = split.length() == 1 && split.charAt(0) == ':';

		if (period == 0)
			return digit ? "0" : timeConvertor.get(TimeFormat.SECONDS).toString(0);

		if (disabled == ALL_FORMATS)
			return digit ? String.valueOf(period) : timeConvertor.get(TimeFormat.SECONDS).toString(period);

		long years = 0;
		long months = 0;
		long days = 0;
		long hours = 0;
		long minutes = 0;
		long seconds = 0;

		if ((disabled & bit(TimeFormat.YEARS)) == 0) {
			years = period / TimeFormat.YEARS.seconds();
			period %= TimeFormat.YEARS.seconds();
		}

		if ((disabled & bit(TimeFormat.MONTHS)) == 0) {
			months = period / TimeFormat.MONTHS.seconds();
			period %= TimeFormat.MONTHS.seconds();
		}

		if ((disabled & bit(TimeFormat.DAYS)) == 0) {
			days = period / TimeFormat.DAYS.seconds();
			period %= TimeFormat.DAYS.seconds();
		}

		if ((disabled & bit(TimeFormat.HOURS)) == 0) {
			hours = period / TimeFormat.HOURS.seconds();
			period %= TimeFormat.HOURS.seconds();
		}

		if ((disabled & bit(TimeFormat.MINUTES)) == 0) {
			minutes = period / TimeFormat.MINUTES.seconds();
			period %= TimeFormat.MINUTES.seconds();
		}

		if ((disabled & bit(TimeFormat.SECONDS)) == 0)
			seconds = period;

		int capacity = digit ? 24 : 40;

		if (split.length() > 1)
			capacity += split.length() * 5;

		StringContainer builder = new StringContainer(capacity);

		addFormat(builder, split, TimeFormat.YEARS, digit, years);
		addFormat(builder, split, TimeFormat.MONTHS, digit, months);
		addFormat(builder, split, TimeFormat.DAYS, digit, days);
		addFormat(builder, split, TimeFormat.HOURS, digit, hours);
		addFormat(builder, split, TimeFormat.MINUTES, digit, minutes);
		addFormat(builder, split, TimeFormat.SECONDS, digit, seconds);

		return builder.toString();
	}

	private static int disabledMask(TimeFormat[] disabled) {
		if (disabled == null || disabled.length == 0)
			return 0;

		int result = 0;

		for (TimeFormat format : disabled)
			if (format != null)
				result |= bit(format);

		return result;
	}

	private static int bit(TimeFormat format) {
		return 1 << format.ordinal();
	}

	public static long timeFromString(String original) {
		if (original == null || original.isEmpty())
			return 0;

		if (ParseUtils.isLong(original))
			return ParseUtils.getLong(original);

		if (original.indexOf(':') != -1)
			return parseColonTime(original);

		long time = 0;
		String period = original;

		for (TimeFormat format : FORMATS) {
			TimeFormatter formatter = timeConvertor.get(format);

			if (formatter == null)
				continue;

			Matcher matcher = formatter.matcher(period);
			boolean found = false;

			while (matcher.find()) {
				time += ParseUtils.getLong(matcher.group()) * format.seconds();
				found = true;
			}

			if (found)
				period = matcher.replaceAll("");
		}

		return time;
	}

	private static long parseColonTime(String value) {
		int length = value.length();
		int segments = 1;

		for (int i = 0; i < length; ++i)
			if (value.charAt(i) == ':')
				++segments;

		int usedSegments = segments > 6 ? 6 : segments;

		if (usedSegments < 2)
			return ParseUtils.getLong(value);

		long[] multipliers = COLON_MULTIPLIERS[usedSegments];

		long result = 0;
		int start = 0;
		int segment = 0;

		for (int i = 0; i <= length && segment < usedSegments; ++i) {
			if (i != length && value.charAt(i) != ':')
				continue;

			result += parseLong(value, start, i) * multipliers[segment++];
			start = i + 1;
		}

		return result;
	}

	private static long parseLong(String value, int start, int end) {
		if (start >= end)
			return 0;

		int pos = start;
		boolean negative = false;

		char first = value.charAt(pos);

		if (first == '-' || first == '+') {
			negative = first == '-';

			if (++pos == end)
				return ParseUtils.getLong(value.substring(start, end));
		}

		long result = 0;
		long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
		long multiplyLimit = limit / 10;

		while (pos < end) {
			char c = value.charAt(pos++);

			if (c < '0' || c > '9')
				return ParseUtils.getLong(value.substring(start, end));

			int digit = c - '0';

			if (result < multiplyLimit)
				return ParseUtils.getLong(value.substring(start, end));

			result *= 10;

			if (result < limit + digit)
				return ParseUtils.getLong(value.substring(start, end));

			result -= digit;
		}

		return negative ? result : -result;
	}

	private static void addFormat(StringContainer builder, String split, TimeFormat format, boolean digit, long time) {
		if (time > 0) {
			boolean notFirst = !builder.isEmpty();

			if (notFirst)
				builder.append(split);

			if (digit) {
				if (time < 10 && notFirst)
					builder.append('0');

				builder.append(time);
			} else {
				TimeFormatter formatter = timeConvertor.get(format);

				if (formatter != null)
					builder.append(formatter.toString(time));
			}

			return;
		}

		if (digit && !builder.isEmpty())
			builder.append(split).append('0').append('0');
	}
}