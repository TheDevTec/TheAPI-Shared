package me.devtec.shared.utility;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import me.devtec.shared.dataholder.StringContainer;

public class StreamUtils {

	/**
	 * @apiNote Read InputStream and convert into String
	 * @return String
	 */
	public static String fromStream(File file) {
		if (!file.exists())
			return null;
		try {
			return fromStream(new FileInputStream(file));
		} catch (Exception err) {
			return null;
		}
	}

	/**
	 * @apiNote Read InputStream and convert into String
	 * @return String
	 */
	public static String fromStream(InputStream stream) {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8), 4096);
			StringContainer sb = new StringContainer(512);
			String content;
			while ((content = br.readLine()) != null) {
				if (sb.length() != 0)
					sb.append(System.lineSeparator());
				sb.append(content);
			}
			stream.close();
			return sb.toString();
		} catch (Exception err) {
			return null;
		}
	}
}
