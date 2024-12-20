package me.devtec.shared.utility;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import me.devtec.shared.dataholder.StringContainer;

public class TimeUtils {
	public static final Map<TimeFormat, TimeFormatter> timeConvertor = new HashMap<>();
	public static String timeSplit = " ";

	public enum TimeFormat {
		YEARS(31556952, 365, "y"), MONTHS(2629746, 12, "mon"), DAYS(86400, 31, "d"), HOURS(3600, 24, "h"), MINUTES(60, 60, "m"), SECONDS(1, 60, "s");

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

	/**
	 * @apiNote Convert long time to String
	 * @param period long Time to convert
	 * @return String
	 */
	public static String timeToString(long period) {
		return timeToString(period, timeSplit);
	}

	/**
	 * @apiNote Convert long time to String
	 * @param period long Time to convert
	 * @return String
	 */
	public static String timeToString(long period, TimeFormat... disabled) {
		return timeToString(period, timeSplit, disabled);
	}

	/**
	 * @apiNote Convert long time to String
	 * @param period   long Time to convert
	 * @param split    String Split between time
	 * @param disabled TimeFormat... disabled time formats
	 * @return String
	 */
	public static String timeToString(long period, String split, TimeFormat... disabled) {
		boolean digit = split.length() == 1 && split.charAt(0) == ':';

		if (period == 0L) {
			return digit ? "0" : timeConvertor.get(TimeFormat.SECONDS).toString(0);
		}

		boolean skipYear = false;
		boolean skipMonth = false;
		boolean skipDay = false;
		boolean skipHour = false;
		boolean skipMinute = false;
		boolean skipSecond = false;

		if (disabled != null) {
			for (TimeFormat format : disabled) {
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
			}
		}

		if (skipYear && skipMonth && skipDay && skipHour && skipMinute && skipSecond) {
			return digit ? String.valueOf(period) : timeConvertor.get(TimeFormat.SECONDS).toString(period);
		}

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
		addFormat(builder, split, TimeFormat.YEARS, digit, years);
		addFormat(builder, split, TimeFormat.MONTHS, digit, months);
		addFormat(builder, split, TimeFormat.DAYS, digit, days);
		addFormat(builder, split, TimeFormat.HOURS, digit, hours);
		addFormat(builder, split, TimeFormat.MINUTES, digit, minutes);
		addFormat(builder, split, TimeFormat.SECONDS, digit, seconds);
		return builder.toString();
	}

	/**
	 * @apiNote Get long from string
	 * @param original String
	 * @return long
	 */
	public static long timeFromString(String original) {
		if (original == null || original.isEmpty()) {
			return 0;
		}

		String period = original;

		if (ParseUtils.isLong(period)) {
			return ParseUtils.getLong(period);
		}

		long time = 0;

		if (period.indexOf(':')!=-1) {
			String[] split = period.split(":");
			switch (split.length) {
			case 2: // mm:ss
				time += ParseUtils.getLong(split[0]) * TimeFormat.MINUTES.seconds();
				time += ParseUtils.getLong(split[1]);
				break;
			case 3: // hh:mm:ss
				time += ParseUtils.getLong(split[0]) * TimeFormat.HOURS.seconds();
				time += ParseUtils.getLong(split[1]) * TimeFormat.MINUTES.seconds();
				time += ParseUtils.getLong(split[2]);
				break;
			case 4: // dd:hh:mm:ss
				time += ParseUtils.getLong(split[0]) * TimeFormat.DAYS.seconds();
				time += ParseUtils.getLong(split[1]) * TimeFormat.HOURS.seconds();
				time += ParseUtils.getLong(split[2]) * TimeFormat.MINUTES.seconds();
				time += ParseUtils.getLong(split[3]);
				break;
			case 5: // mm:dd:hh:mm:ss
				time += ParseUtils.getLong(split[0]) * TimeFormat.MONTHS.seconds();
				time += ParseUtils.getLong(split[1]) * TimeFormat.DAYS.seconds();
				time += ParseUtils.getLong(split[2]) * TimeFormat.HOURS.seconds();
				time += ParseUtils.getLong(split[3]) * TimeFormat.MINUTES.seconds();
				time += ParseUtils.getLong(split[4]);
				break;
			default: // yy:mm:dd:hh:mm:ss
				time += ParseUtils.getLong(split[0]) * TimeFormat.YEARS.seconds();
				time += ParseUtils.getLong(split[1]) * TimeFormat.MONTHS.seconds();
				time += ParseUtils.getLong(split[2]) * TimeFormat.DAYS.seconds();
				time += ParseUtils.getLong(split[3]) * TimeFormat.HOURS.seconds();
				time += ParseUtils.getLong(split[4]) * TimeFormat.MINUTES.seconds();
				time += ParseUtils.getLong(split[5]);
				break;
			}
			return time;
		}

		for (TimeFormat format : TimeFormat.values()) {
			Matcher matcher = timeConvertor.get(format).matcher(period);
			while (matcher.find()) {
				time += ParseUtils.getLong(matcher.group()) * format.seconds();
			}
			period = matcher.replaceAll("");
		}
		return time;
	}

	private static void addFormat(StringContainer builder, String split, TimeFormat format, boolean digit, long time) {
		if (time > 0) {
			boolean notFirst = !builder.isEmpty();
			if (notFirst) {
				builder.append(split);
			}
			if (digit) {
				if (time < 10 && notFirst) {
					builder.append('0');
				}
				builder.append(time);
			} else {
				builder.append(timeConvertor.get(format).toString(time));
			}
		} else if (digit) {
			boolean notFirst = !builder.isEmpty();
			if (notFirst) {
				builder.append(split).append('0').append('0');
			}
		}
	}
}
