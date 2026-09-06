package me.devtec.shared.dataholder.store;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TempStoragePolicy {
	public static volatile File directory = new File(
			System.getProperty("theapi.config.temp", System.getProperty("java.io.tmpdir")));
	public static volatile long minimumReserveBytes = 16L * 1024 * 1024;

	private TempStoragePolicy() {
	}

	public static Path createDirectory() throws IOException {
		Files.createDirectories(directory.toPath());
		long usable = directory.getUsableSpace();
		if (usable > 0 && usable < minimumReserveBytes)
			throw new IOException("Insufficient free space for Config backing store: " + directory);
		return Files.createTempDirectory(directory.toPath(), "theapi-config-");
	}
}