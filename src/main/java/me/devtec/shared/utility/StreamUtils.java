package me.devtec.shared.utility;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;

import me.devtec.shared.dataholder.StringContainer;

public class StreamUtils {
	private static final int DEFAULT_BUFFER_SIZE = 512;

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
			((Buffer)buffer).flip();
			return decode(buffer);
		} catch (Exception e) {
			return null;
		}
	}

	public static String decode(ByteBuffer buffer) {
		char[] charBuffer = new char[buffer.remaining()];
		int charPos = 0;

		while (buffer.hasRemaining()) {
			int firstByte = buffer.get() & 0xFF;

			if (firstByte <= 0x7F)
				// 1-byte (ASCII)
				charBuffer[charPos++] = (char) firstByte;
			else if (firstByte >> 5 == 0x6) {
				// 2-byte
				int secondByte = buffer.get() & 0xFF;
				charBuffer[charPos++] = (char) ((firstByte & 0x1F) << 6 | secondByte & 0x3F);
			} else if (firstByte >> 4 == 0xE) {
				// 3-byte
				int secondByte = buffer.get() & 0xFF;
				int thirdByte = buffer.get() & 0xFF;
				charBuffer[charPos++] = (char) ((firstByte & 0x0F) << 12 | (secondByte & 0x3F) << 6 | thirdByte & 0x3F);
			} else if (firstByte >> 3 == 0x1E) {
				// 4-byte
				int secondByte = buffer.get() & 0xFF;
				int thirdByte = buffer.get() & 0xFF;
				int fourthByte = buffer.get() & 0xFF;
				int codePoint = (firstByte & 0x07) << 18 | (secondByte & 0x3F) << 12 | (thirdByte & 0x3F) << 6
						| fourthByte & 0x3F;
				codePoint -= 0x10000;
				charBuffer[charPos++] = (char) ((codePoint >> 10) + 0xD800);
				charBuffer[charPos++] = (char) ((codePoint & 0x3FF) + 0xDC00);
			} else
				throw new IllegalArgumentException("Invalid UTF-8 encoding detected.");
		}
		return new String(charBuffer, 0, charPos);
	}

	public static String decode(byte[] bytes) {
		char[] charBuffer = new char[bytes.length];
		int charPos = 0;

		for (int i = 0; i < bytes.length; ++i) {
			int firstByte = bytes[i] & 0xFF;

			if (firstByte <= 0x7F)
				// 1-byte (ASCII)
				charBuffer[charPos++] = (char) firstByte;
			else if (firstByte >> 5 == 0x6) {
				// 2-byte
				int secondByte = bytes[++i] & 0xFF;
				charBuffer[charPos++] = (char) ((firstByte & 0x1F) << 6 | secondByte & 0x3F);
			} else if (firstByte >> 4 == 0xE) {
				// 3-byte
				int secondByte = bytes[++i] & 0xFF;
				int thirdByte = bytes[++i] & 0xFF;
				charBuffer[charPos++] = (char) ((firstByte & 0x0F) << 12 | (secondByte & 0x3F) << 6 | thirdByte & 0x3F);
			} else if (firstByte >> 3 == 0x1E) {
				// 4-byte
				int secondByte = bytes[++i] & 0xFF;
				int thirdByte = bytes[++i] & 0xFF;
				int fourthByte = bytes[++i] & 0xFF;
				int codePoint = (firstByte & 0x07) << 18 | (secondByte & 0x3F) << 12 | (thirdByte & 0x3F) << 6
						| fourthByte & 0x3F;
				codePoint -= 0x10000;
				charBuffer[charPos++] = (char) ((codePoint >> 10) + 0xD800);
				charBuffer[charPos++] = (char) ((codePoint & 0x3FF) + 0xDC00);
			} else
				throw new IllegalArgumentException("Invalid UTF-8 encoding detected.");
		}
		return new String(charBuffer, 0, charPos);
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
			} catch (Exception ignored) {
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
			}
			return sb.toString();
		} catch (Exception err) {
			return null;
		}
	}
}
