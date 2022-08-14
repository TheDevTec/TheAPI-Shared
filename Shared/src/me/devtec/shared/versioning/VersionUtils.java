package me.devtec.shared.versioning;

import me.devtec.shared.utility.StringUtils;

public class VersionUtils {
	public static enum Version {
		OLDER_VERSION, NEWER_VERSION, SAME_VERSION, UKNOWN;
	}

	public static Version getVersion(String version, String compareVersion) {
		if (version == null || compareVersion == null || version.replaceAll("[^0-9.]+", "").trim().isEmpty() || compareVersion.replaceAll("[^0-9.]+", "").trim().isEmpty())
			return Version.UKNOWN;
		double versionD = convertToDouble(version);
		double compareVersionD = convertToDouble(compareVersion);
		if (versionD == compareVersionD)
			return Version.SAME_VERSION;
		return versionD > compareVersionD ? Version.OLDER_VERSION : Version.NEWER_VERSION;
	}

	public static double convertToDouble(String version) {
		double ver = 0;
		int dotPos = 1;
		for (String split : version.replaceAll("[^0-9.]+", "").split("\\.")) {
			ver += StringUtils.getDouble(split) / dotPos;
			dotPos *= 10;
		}
		return ver;
	}
}
