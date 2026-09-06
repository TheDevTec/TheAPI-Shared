package me.devtec.shared.utility;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import me.devtec.shared.dataholder.StringContainer;

public class StreamUtils {

	private static final int DEFAULT_BUFFER_SIZE = 8192;
	private static final int DEFAULT_CONTAINER_SIZE = 512;

	/**
	 * @apiNote Read file and convert into String
	 * @return String
	 */
	public static String fromStream(File file) {
		if (file == null || !file.exists() || !file.isFile())
			return null;

		try {
			long size = file.length();

			if (size > Integer.MAX_VALUE) {
				InputStream stream = Files.newInputStream(file.toPath());
				return fromStream(stream, DEFAULT_BUFFER_SIZE);
			}

			return decode(Files.readAllBytes(file.toPath()));
		} catch (Exception e) {
			return null;
		}
	}

	public static String decode(ByteBuffer buffer) {
		int remaining = buffer.remaining();

		if (remaining == 0)
			return "";

		if (buffer.hasArray()) {
			int position = buffer.position();
			String result = decode(buffer.array(), buffer.arrayOffset() + position, remaining);
			buffer.position(buffer.limit());
			return result;
		}

		char[] chars = new char[remaining];
		int charPos = 0;
		int pos = buffer.position();
		int limit = buffer.limit();

		while (pos < limit) {
			int first = buffer.get(pos++) & 0xFF;

			if (first <= 0x7F) {
				chars[charPos++] = (char) first;
				continue;
			}

			if (first >> 5 == 0x6) {
				if (pos >= limit)
					throw invalidUtf8();

				int second = buffer.get(pos++) & 0xFF;

				chars[charPos++] = (char) ((first & 0x1F) << 6 | second & 0x3F);
				continue;
			}

			if (first >> 4 == 0xE) {
				if (pos + 1 >= limit)
					throw invalidUtf8();

				int second = buffer.get(pos++) & 0xFF;
				int third = buffer.get(pos++) & 0xFF;

				chars[charPos++] = (char) ((first & 0x0F) << 12 | (second & 0x3F) << 6 | third & 0x3F);
				continue;
			}

			if (first >> 3 == 0x1E) {
				if (pos + 2 >= limit)
					throw invalidUtf8();

				int second = buffer.get(pos++) & 0xFF;
				int third = buffer.get(pos++) & 0xFF;
				int fourth = buffer.get(pos++) & 0xFF;

				int codePoint = (first & 0x07) << 18
						| (second & 0x3F) << 12
						| (third & 0x3F) << 6
						| fourth & 0x3F;

				codePoint -= 0x10000;

				chars[charPos++] = (char) ((codePoint >> 10) + 0xD800);
				chars[charPos++] = (char) ((codePoint & 0x3FF) + 0xDC00);
				continue;
			}

			throw invalidUtf8();
		}

		buffer.position(limit);
		return new String(chars, 0, charPos);
	}

	public static String decode(byte[] bytes) {
		if (bytes == null)
			return null;

		return decode(bytes, 0, bytes.length);
	}

	private static String decode(byte[] bytes, int offset, int length) {
		if (length == 0)
			return "";

		char[] chars = new char[length];
		int charPos = 0;
		int pos = offset;
		int end = offset + length;

		while (pos < end) {
			int first = bytes[pos++] & 0xFF;

			if (first <= 0x7F) {
				chars[charPos++] = (char) first;
				continue;
			}

			if (first >> 5 == 0x6) {
				if (pos >= end)
					throw invalidUtf8();

				int second = bytes[pos++] & 0xFF;

				chars[charPos++] = (char) ((first & 0x1F) << 6 | second & 0x3F);
				continue;
			}

			if (first >> 4 == 0xE) {
				if (pos + 1 >= end)
					throw invalidUtf8();

				int second = bytes[pos++] & 0xFF;
				int third = bytes[pos++] & 0xFF;

				chars[charPos++] = (char) ((first & 0x0F) << 12 | (second & 0x3F) << 6 | third & 0x3F);
				continue;
			}

			if (first >> 3 == 0x1E) {
				if (pos + 2 >= end)
					throw invalidUtf8();

				int second = bytes[pos++] & 0xFF;
				int third = bytes[pos++] & 0xFF;
				int fourth = bytes[pos++] & 0xFF;

				int codePoint = (first & 0x07) << 18
						| (second & 0x3F) << 12
						| (third & 0x3F) << 6
						| fourth & 0x3F;

				codePoint -= 0x10000;

				chars[charPos++] = (char) ((codePoint >> 10) + 0xD800);
				chars[charPos++] = (char) ((codePoint & 0x3FF) + 0xDC00);
				continue;
			}

			throw invalidUtf8();
		}

		return new String(chars, 0, charPos);
	}

	private static IllegalArgumentException invalidUtf8() {
		return new IllegalArgumentException("Invalid UTF-8 encoding detected.");
	}

	/**
	 * @apiNote Read InputStream and convert into String
	 * @return String
	 */
	public static String fromStream(InputStream stream) {
		return fromStream(stream, DEFAULT_CONTAINER_SIZE);
	}

	/**
	 * @apiNote Read InputStream and convert into String with prepared
	 *          StringContainer size
	 * @return String
	 */
	public static String fromStream(InputStream stream, int containerSize) {
		if (stream == null)
			return null;

		int initialCapacity = containerSize <= 0 ? DEFAULT_CONTAINER_SIZE : containerSize;

		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			StringContainer container = new StringContainer(initialCapacity);
			char[] buffer = new char[DEFAULT_BUFFER_SIZE];

			int read;

			while ((read = reader.read(buffer, 0, buffer.length)) != -1)
				if (read != 0)
					container.append(buffer, 0, read);

			return container.toString();
		} catch (Exception e) {
			return null;
		}
	}
}