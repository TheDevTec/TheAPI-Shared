package me.devtec.shared.utility;

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
			return fromStream(new FileInputStream(file), (int) file.length());
		} catch (Exception err) {
			return null;
		}
	}

	/**
	 * @apiNote Read InputStream and convert into String
	 * @return String
	 */
	public static String fromStream(InputStream stream) {
		return fromStream(stream, 512);
	}

	/**
	 * @apiNote Read InputStream and convert into String with prepared
	 *          StringContainer size
	 * @return String
	 */
	public static String fromStream(InputStream stream, int containerSize) {
		try {
			InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
			StringContainer sb = new StringContainer(containerSize);
			char[] buffer = new char[containerSize <= 0 ? 512 : containerSize];
			int res;
			while ((res = reader.read(buffer)) != -1) {
				sb.ensureCapacity(sb.length() + res);
				System.arraycopy(buffer, 0, sb.getValueWithoutTrim(), sb.length(), res);
				sb.increaseCount(res);
			}
			stream.close();
			return sb.toString();
		} catch (Exception err) {
			return null;
		}
	}
}
