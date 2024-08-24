package me.devtec.shared.utility;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;

public interface LibraryLoader {
	default void downloadFileFromUrl(String fileUrl, String pathFile) {
		try {
			this.downloadFileFromUrl(new URL(fileUrl), new File(pathFile));
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	default void downloadFileFromUrl(String fileUrl, File file) {
		try {
			this.downloadFileFromUrl(new URL(fileUrl), file);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}

	default void downloadFileFromUrl(URL url, File file) {
		try {
			if (file.exists() && !file.delete())
				return;
			if (!file.exists()) {
				if (file.getParentFile() != null)
					file.getParentFile().mkdirs();
				file.createNewFile();
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestProperty("User-Agent", "DevTec-JavaClient");
				try (InputStream in = conn.getInputStream()) {
					byte[] buf = new byte[4096];
					int r;
					try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file.toPath()))) {
						while ((r = in.read(buf)) != -1)
							out.write(buf, 0, r);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void load(File file);

	boolean isLoaded(File file);
}
