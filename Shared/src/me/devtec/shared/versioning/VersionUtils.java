package me.devtec.shared.versioning;

import me.devtec.shared.utility.ParseUtils;

public class VersionUtils {
	public static enum Version {
		OLDER_VERSION, NEWER_VERSION, SAME_VERSION, UKNOWN;
	}

	public static Version getVersion(String version, String compareVersion) {
		if (version == null || compareVersion == null)
			return Version.UKNOWN;

		version = version.replaceAll("[^0-9.]+", "").trim();
		compareVersion = compareVersion.replaceAll("[^0-9.]+", "").trim();

		if (version.isEmpty() || compareVersion.isEmpty())
			return Version.UKNOWN;

		String[] primaryVersion = version.split("\\.");
		String[] compareToVersion = compareVersion.split("\\.");

		for (int i = 0; i < Math.max(primaryVersion.length, compareToVersion.length); ++i) {
			String number = i >= primaryVersion.length ? "0" : "1" + primaryVersion[i];
			if (compareToVersion.length <= i)
				break;
			if (ParseUtils.getInt(number) > ParseUtils.getInt("1" + compareToVersion[i]))
				return Version.NEWER_VERSION;
			if (ParseUtils.getInt(number) < ParseUtils.getInt("1" + compareToVersion[i]))
				return Version.OLDER_VERSION;
		}
		return Version.SAME_VERSION;
	}

	public static double convertToDouble(String version) {
		double ver = 0;
		int dotPos = 1;
		for (String split : version.replaceAll("[^0-9.]+", "").split("\\.")) {
			int additional = 1;
			if (split.length() > 1)
				for (int i = 1; i < split.length(); ++i)
					additional *= 10;

			ver += ParseUtils.getDouble(split) / dotPos / additional;
			dotPos = dotPos * 10;
		}
		return ver;
	}
}
