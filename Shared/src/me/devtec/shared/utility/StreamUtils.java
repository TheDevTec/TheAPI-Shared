package me.devtec.shared.utility;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StreamUtils {

	/**
	 * @apiNote Read InputStream and convert into String
	 * @return String
	 */
	public static String fromStream(File file) {
		try {
			FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
			StringBuilder out = new StringBuilder();

			int bufferSize = 4096;
			if (bufferSize > channel.size())
				bufferSize = (int) channel.size();
			ByteBuffer buff = ByteBuffer.allocate(bufferSize);
			while (channel.read(buff) > 0) {
				out.append(toUTF8(buff));
				buff.clear();
			}
			return out.toString();
		} catch (Exception err) {
			return null;
		}
	}

	/**
	 * @apiNote Wrap byte[] to ByteBuffer and decode ByteBuffer bytes with UTF8
	 *          Decoder
	 * @return char[]
	 */
	public static char[] toUTF8(byte[] bytes) {
		return toUTF8(ByteBuffer.wrap(bytes));
	}

	/**
	 * @apiNote Decode ByteBuffer bytes with UTF8 Decoder
	 * @return char[]
	 */
	public static char[] toUTF8(ByteBuffer buff) {
		CharsetDecoder cd = StandardCharsets.UTF_8.newDecoder();
		CharBuffer buf = CharBuffer.allocate(buff.array().length);
		cd.decode(buff, buf, true);
		return buf.array();
	}

	/**
	 * @apiNote Read InputStream and convert into String
	 * @return String
	 */
	public static String fromStream(InputStream stream) {
		try {
			ReadableByteChannel channel = Channels.newChannel(stream);
			StringBuilder out = new StringBuilder();

			int bufferSize = 2048;
			ByteBuffer buff = ByteBuffer.allocate(bufferSize);
			while (channel.read(buff) > 0) {
				out.append(toUTF8(buff));
				buff.clear();
			}
			return out.toString();
		} catch (Exception err) {
			return null;
		}
	}

	/**
	 * @apiNote Read InputStream and convert into List<String> without seperator of
	 *          lines
	 * @return List<String>
	 */
	public static List<String> fromStreamToList(InputStream stream) {
		String readen = fromStream(stream);
		String[] splitLines = readen.split(System.lineSeparator());
		List<String> lines = new ArrayList<>();
		Collections.addAll(lines, splitLines);
		return lines;
	}
}
