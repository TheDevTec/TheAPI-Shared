package me.devtec.shared.versioning;

import me.devtec.shared.utility.StringUtils;

public class VersionUtils {
	public static enum Version implements Comparable<Version> {
		OLDER_VERSION(1), NEWER_VERSION(3), SAME_VERSION(2), UKNOWN(0);

		private int id;

		Version(int i) {
			id = i;
		}

		public int getId() {
			return id;
		}
	}

	public static Version getVersion(String currentVersion, String version) {
		if (currentVersion == null || version == null || currentVersion.replaceAll("[^0-9.]+", "").trim().isEmpty() || version.replaceAll("[^0-9.]+", "").trim().isEmpty())
			return Version.UKNOWN;
		int count = 0;
		String[] cver = currentVersion.replaceAll("[^0-9.]+", "").split("\\.");
		for (String ver : version.replaceAll("[^0-9.]+", "").split("\\.")) {
			int next = StringUtils.getInt(ver);
			int current = cver.length <= count ? 0 : StringUtils.getInt(cver[count++]);
			if (next != current)
				return next > current ? Version.NEWER_VERSION : Version.OLDER_VERSION;
		}
		return Version.SAME_VERSION;
	}
}
