package me.devtec.shared.dataholder.codec;

import java.io.EOFException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.store.NodeMetadata;

/**
 * Shared codecs, operation-local readers and transactional candidate documents.
 */
public final class FormatRegistry {
	private FormatRegistry() {
	}

	public static String detect(String sample) {
		if (sample == null || sample.isEmpty())
			return "empty";

		char[] chars = sample.toCharArray();
		return detect(chars, 0, chars.length);
	}

	public static String detect(char[] sample) {
		return detect(sample, 0, sample == null ? 0 : sample.length);
	}

	public static String detect(char[] sample, int offset, int length) {
		if (sample == null || length <= 0)
			return "empty";

		int end = offset + length;

		boolean jsonPossible = true;
		boolean tomlPossible = true;
		boolean yamlPossible = true;
		boolean bytePossible = true;

		boolean foundContent = false;
		boolean foundTomlSection = false;
		boolean foundEquals = false;
		boolean foundColon = false;

		int lineStart = offset;

		for (int i = offset; i <= end; i++) {
			char c = i < end ? sample[i] : '\n';

			if (i < end && bytePossible && c > ' ')
				if (((c < 'A') || (c > 'Z')) && ((c < 'a') || (c > 'z')) && ((c < '0') || (c > '9')) && c != '+'
						&& c != '/' && c != '=')
					bytePossible = false;

			if (c != '\n' && i < end)
				continue;

			int start = lineStart;
			int lineEnd = i;

			if (lineEnd > start && sample[lineEnd - 1] == '\r')
				lineEnd--;

			while (start < lineEnd && sample[start] <= ' ')
				start++;

			while (lineEnd > start && sample[lineEnd - 1] <= ' ')
				lineEnd--;

			lineStart = i + 1;

			if (start >= lineEnd)
				continue;

			char first = sample[start];

			if (first == '#')
				continue;

			foundContent = true;

			if (jsonPossible)
				if (first != '{' && first != '[')
					jsonPossible = false;
				else if (first == '[' && start + 1 < lineEnd && sample[start + 1] != '{')
					jsonPossible = false;

			if ((first == '[' && sample[lineEnd - 1] == ']') && (start + 1 >= lineEnd || sample[start + 1] != '{')) {
				foundTomlSection = true;
				yamlPossible = false;
				jsonPossible = false;
				continue;
			}

			int equals = -1;
			int colon = -1;

			boolean inString = false;
			boolean escaped = false;
			char quote = 0;

			for (int j = start; j < lineEnd; j++) {
				char ch = sample[j];

				if (inString) {
					if (escaped) {
						escaped = false;
						continue;
					}

					if (ch == '\\') {
						escaped = true;
						continue;
					}

					if (ch == quote) {
						inString = false;
						quote = 0;
					}

					continue;
				}

				if (ch == '"' || ch == '\'') {
					inString = true;
					quote = ch;
					continue;
				}

				if (ch == '#')
					break;

				if (ch == '=' && equals == -1)
					equals = j;
				else if (ch == ':' && colon == -1)
					colon = j;

				if (equals != -1 && colon != -1)
					break;
			}

			if (equals >= 0) {
				foundEquals = true;

				if (colon < 0 || equals < colon)
					yamlPossible = false;
			}

			if (colon >= 0) {
				foundColon = true;

				if (equals < 0 || colon < equals)
					tomlPossible = false;
			}

			if (equals < 0 && colon < 0 && !foundTomlSection)
				tomlPossible = false;

			if (first != '{' && first != '[')
				jsonPossible = false;
		}

		if (!foundContent)
			return "empty";

		if (jsonPossible)
			return "json";

		if (foundTomlSection && tomlPossible)
			return "toml";

		if (foundEquals && tomlPossible)
			return "properties";

		if (foundColon && yamlPossible)
			return "yaml";

		if (bytePossible)
			return "byte";

		return "yaml";
	}

	public static void parse(Reader source, String format, ConfigDocument document) throws IOException {
		document.format = format;
		if (format == null)
			throw new IOException("Unknown Config format: " + format);
		switch (format) {
		case "empty":
			return;
		case "yaml":
			YamlParser.INSTANCE.parse(source, document);
			break;
		case "json":
			JsonParser.INSTANCE.parse(source, document);
			break;
		case "byte":
			readByte(source, document);
			break;
		case "toml":
		case "properties":
			readProperties(source, document, "toml".equals(format));
			break;
		default:
			throw new IOException("Unknown Config format: " + format);
		}
		document.storage.store().flush();
	}

	private static void readProperties(Reader source, ConfigDocument d, boolean toml) throws IOException {
		try (ConfigInput in = new ConfigInput(source, toml ? "toml" : "properties")) {
			String line, section = "";
			List<String> comments = new ArrayList<>();
			while ((line = in.line()) != null) {
				line = line.trim();
				if (line.isEmpty())
					continue;
				if (line.startsWith("#")) {
					comments.add(line);
					continue;
				}
				if (toml && line.startsWith("[") && line.endsWith("]")) {
					section = line.substring(1, line.length() - 1) + '.';
					continue;
				}
				int eq = line.indexOf('=');
				if (eq < 0)
					throw in.error("Expected '='");
				String key = section + YamlParser.unquote(line.substring(0, eq).trim());
				String raw = line.substring(eq + 1).trim();
				Object value = YamlParser.parseScalar(YamlParser.unquote(raw));
				d.put(d.child(0, key), value,
						new NodeMetadata(YamlParser.unquote(raw), null, comments.isEmpty() ? null : comments), false);
				comments = new ArrayList<>();
			}
			d.footer.addAll(comments);
		}
	}

	private static void readByte(Reader reader, ConfigDocument d) throws IOException {
		InputStream ascii = new InputStream() {
			@Override
			public int read() throws IOException {
				int c;
				do
					c = reader.read();
				while (c >= 0 && c <= 32);
				if (c > 127)
					throw new IOException("Non-ASCII Base64 input");
				return c;
			}
		};
		InputStream in = Base64.getDecoder().wrap(ascii);
		int count = in.read();
		if (count < 0)
			throw new EOFException("Missing Byte entry count");
		for (int i = 0; i < count; i++) {
			String key = byteString(in), raw = byteString(in);
			d.put(d.child(0, key), YamlParser.parseScalar(raw), new NodeMetadata(raw, null, null), false);
		}
		if (in.read() != -1)
			throw new IOException("Trailing proprietary Byte data");
	}

	private static String byteString(InputStream in) throws IOException {
		int n = in.read();
		if (n < 0)
			throw new EOFException();
		byte[] b = new byte[n];
		int p = 0;
		while (p < n) {
			int r = in.read(b, p, n - p);
			if (r < 0)
				throw new EOFException();
			p += r;
		}
		return new String(b, StandardCharsets.UTF_8);
	}

	public static void writeByte(ConfigDocument d, OutputStream output) throws IOException {
		if (d.storage.store().size() > 255)
			throw new IOException("Legacy Byte format supports at most 255 entries; use YAML/JSON");
		OutputStream out = Base64.getEncoder().wrap(new FilterOutputStream(output) {
			@Override
			public void close() throws IOException {
				flush();
			}
		});
		out.write(d.storage.store().size());
		for (int n = d.storage.store().firstEntry(); n != 0; n = d.storage.store().nextEntry(n)) {
			byte[] key = d.storage.store().path(n).getBytes(StandardCharsets.UTF_8);
			NodeMetadata m = d.storage.store().metadata(n);
			Object v = d.storage.store().value(n).get();
			String raw = m.writtenValue != null ? m.writtenValue
					: v instanceof CharSequence || v instanceof Number ? String.valueOf(v)
							: me.devtec.shared.json.Json.writer().simpleWrite(v);
			byte[] value = raw.getBytes(StandardCharsets.UTF_8);
			if (key.length > 255 || value.length > 255)
				throw new IOException("Legacy Byte key/value exceeds 255 UTF-8 bytes");
			out.write(key.length);
			out.write(key);
			out.write(value.length);
			out.write(value);
		}
		out.close();
	}
}