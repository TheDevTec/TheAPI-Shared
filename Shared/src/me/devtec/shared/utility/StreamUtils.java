package me.devtec.shared.utility;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;

import me.devtec.shared.dataholder.StringContainer;

public class StreamUtils {
	private static final int DEFAULT_BUFFER_SIZE = 512;
	private static final CharsetDecoder charset = StandardCharsets.UTF_8.newDecoder();

	/**
	 * @apiNote Read InputStream and convert into String
	 * @return String
	 */
	public static String fromStream(File file) {
		if (file == null || !file.exists())
			return null;
		try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
			ByteBuffer buffer = ByteBuffer.allocateDirect((int) channel.size());
			channel.read(buffer);
			channel.close();
			buffer.flip();
			return charset.decode(buffer).toString();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @apiNote Read InputStream and convert into String
	 * @return String
	 */
	public static String fromStream(InputStream stream) {
		try {
			return fromStream(stream, DEFAULT_BUFFER_SIZE);
		} finally {
			try {
				stream.close();
			} catch (Exception e) {
			}
		}
	}

	/**
	 * @apiNote Read InputStream and convert into String with prepared
	 *          StringContainer size
	 * @return String
	 */
	public static String fromStream(InputStream stream, int containerSize) {
		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {

			int finalSize = containerSize <= 0 ? DEFAULT_BUFFER_SIZE : containerSize;

			StringContainer sb = new StringContainer(finalSize);
			char[] buffer = new char[finalSize];
			int res;
			while ((res = reader.read(buffer)) != -1) {
				sb.ensureCapacity(sb.length() + res);
				System.arraycopy(buffer, 0, sb.getValueWithoutTrim(), sb.length(), res);
				sb.increaseCount(res);
				if (!(stream instanceof FileInputStream)) {
					int i;
					for (i = sb.length() - res; i < sb.length(); ++i) {
						char c = sb.charAt(i);
						if (c == '\n')
							sb.replace(i, ++i, System.lineSeparator());
					}
				}
			}
			return sb.toString();
		} catch (Exception err) {
			return null;
		}
	}
}
