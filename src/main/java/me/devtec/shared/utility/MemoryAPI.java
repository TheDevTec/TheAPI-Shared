package me.devtec.shared.utility;

import me.devtec.shared.utility.StringUtils.FormatType;

public class MemoryAPI {
	private static final double mb = 1048576;
	private static final double max = Runtime.getRuntime().maxMemory() / MemoryAPI.mb;

	public static double getFreeMemory(boolean inPercentage) {
		if (!inPercentage) {
			return ParseUtils.getDouble(StringUtils.formatDouble(FormatType.BASIC, MemoryAPI.getMaxMemory() - MemoryAPI.getRawUsedMemory(false)));
		}
		return ParseUtils.getDouble(StringUtils.formatDouble(FormatType.BASIC, (MemoryAPI.getMaxMemory() - MemoryAPI.getRawUsedMemory(false)) / MemoryAPI.getMaxMemory() * 100));
	}

	public static double getMaxMemory() {
		return MemoryAPI.max;
	}

	public static double getUsedMemory(boolean inPercents) {
		if (!inPercents) {
			return ParseUtils.getDouble(StringUtils.formatDouble(FormatType.BASIC, MemoryAPI.getRawUsedMemory(false)));
		}
		return ParseUtils.getDouble(StringUtils.formatDouble(FormatType.BASIC, MemoryAPI.getRawUsedMemory(false) / MemoryAPI.getMaxMemory() * 100));
	}

	public static double getRawUsedMemory(boolean inPercents) {
		if (!inPercents) {
			return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / MemoryAPI.mb;
		}
		return MemoryAPI.getRawUsedMemory(false) / MemoryAPI.getMaxMemory() * 100;
	}

	public static double getRawFreeMemory(boolean inPercents) {
		if (!inPercents) {
			return MemoryAPI.getMaxMemory() - MemoryAPI.getRawUsedMemory(false);
		}
		return (MemoryAPI.getMaxMemory() - MemoryAPI.getRawUsedMemory(false)) / MemoryAPI.getMaxMemory() * 100;
	}
}
