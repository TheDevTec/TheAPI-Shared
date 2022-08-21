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
		return versionD > compareVersionD ? Version.NEWER_VERSION : Version.OLDER_VERSION;
	}

	public static double convertToDouble(String version) {
		double ver = 0;
		int dotPos = 1;
		for (String split : version.replaceAll("[^0-9.]+", "").split("\\.")) {
			int additional = 1;
			if (split.length() > 1)
				for (int i = 1; i < split.length(); ++i)
					additional *= 10;

			ver += StringUtils.getDouble(split) / dotPos / additional;
			dotPos = dotPos * 10;
		}
		return ver;
	}
}
