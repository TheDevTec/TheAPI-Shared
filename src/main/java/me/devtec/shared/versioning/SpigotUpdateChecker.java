package me.devtec.shared.versioning;

import java.io.IOException;
import java.net.URL;

import me.devtec.shared.utility.StreamUtils;

public class SpigotUpdateChecker {

	private final String pluginVersion;
	private final int id;
	private URL checkURL;

	public SpigotUpdateChecker(String pluginVersion, int id) {
		this.id = id;
		this.pluginVersion = pluginVersion;
	}

	public static SpigotUpdateChecker createUpdateChecker(String pluginVersion, int id) {
		return new SpigotUpdateChecker(pluginVersion, id);
	}

	public int getId() {
		return id;
	}

	public SpigotUpdateChecker reconnect() {
		try {
			checkURL = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + id);
		} catch (Exception ignored) {
		}
		return this;
	}

	// 0 == SAME VERSION
	// 1 == NEW VERSION
	// 2 == BETA VERSION
	public VersionUtils.Version checkForUpdates() {
		if (checkURL == null) {
			reconnect();
		}
		String reader;
		try {
			reader = StreamUtils.fromStream(checkURL.openStream(), 64);
		} catch (IOException e) {
			e.printStackTrace();
			return VersionUtils.Version.UKNOWN;
		}
		if (reader == null) {
			return VersionUtils.Version.UKNOWN;
		}
		return VersionUtils.getVersion(pluginVersion, reader);
	}
}
