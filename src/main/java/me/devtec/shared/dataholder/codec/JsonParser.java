package me.devtec.shared.dataholder.codec;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.LinkedHashMap;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.utility.ParseUtils;

public final class JsonParser {

	public static final JsonParser INSTANCE = new JsonParser();

	private static final int MAX_DEPTH = 256;
	private static final int MAX_SCALAR_LENGTH = 1024;
	private static final long MATERIALIZATION_LIMIT = Math.min(16L * 1024 * 1024,
			Runtime.getRuntime().maxMemory() / 16);

	private JsonParser() {
	}

	public void parse(Reader reader, ConfigDocument document) throws IOException {
		try (ConfigInput in = new ConfigInput(reader, "json")) {
			in.spaces();

			int c = in.peek();

			if (c == '{')
				object(in, document, 0, 0);
			else if (c == '[') {
				in.read();
				in.spaces();

				if (in.peek() != ']')
					while (true) {
						object(in, document, 0, 0);
						in.spaces();

						if (in.peek() != ',')
							break;

						in.read();
						in.spaces();
					}

				in.expect(']');
			} else
				throw in.error("Config JSON root must be object or legacy object array");

			in.spaces();

			if (in.peek() != -1)
				throw in.error("Trailing JSON content");
		}
	}

	private void object(ConfigInput in, ConfigDocument document, int parent, int depth) throws IOException {
		if (depth > MAX_DEPTH)
			throw in.error("Nesting exceeds " + MAX_DEPTH);

		in.expect('{');
		in.spaces();

		if (in.peek() == '}') {
			in.read();

			if (parent != 0)
				document.put(parent, new LinkedHashMap<>(), null, false);

			return;
		}

		while (true) {
			String key = string(in);

			in.spaces();
			in.expect(':');
			in.spaces();

			int node = document.child(parent, key);

			if (in.peek() == '{')
				object(in, document, node, depth + 1);
			else
				document.put(node, value(in, document, depth + 1), null, false);

			in.spaces();

			int c = in.read();

			if (c == '}')
				return;

			if (c != ',')
				throw in.error("Expected ',' or '}'");

			in.spaces();
		}
	}

	static Object value(ConfigInput in, ConfigDocument document, int depth) throws IOException {
		if (depth > MAX_DEPTH)
			throw in.error("Nesting exceeds " + MAX_DEPTH);

		in.spaces();

		int c = in.peek();

		if (c == '"')
			return stringValue(in, document);

		if (c == '[' || c == '{') {
			boolean map = c == '{';
			int end = map ? '}' : ']';

			in.read();

			AdaptiveValueBuilder builder = new AdaptiveValueBuilder(document, map);

			in.spaces();

			if (in.peek() == end) {
				in.read();
				return builder.finish();
			}

			while (true) {
				Object key = null;

				if (map) {
					key = string(in);
					in.spaces();
					in.expect(':');
				}

				builder.add(key, value(in, document, depth + 1));

				in.spaces();

				c = in.read();

				if (c == end)
					return builder.finish();

				if (c != ',')
					throw in.error("Expected collection separator");

				in.spaces();
			}
		}

		StringContainer token = new StringContainer(24);

		while ((c = in.peek()) >= 0 && c > 32 && c != ',' && c != ']' && c != '}') {
			token.append((char) in.read());

			if (token.length() > MAX_SCALAR_LENGTH)
				throw in.error("Invalid scalar token");
		}

		int length = token.length();

		if (length == 4) {
			if (equals(token, 't', 'r', 'u', 'e'))
				return Boolean.TRUE;

			if (equals(token, 'n', 'u', 'l', 'l'))
				return null;
		} else if (length == 5 && equals(token, 'f', 'a', 'l', 's', 'e'))
			return Boolean.FALSE;

		if (!isJsonNumber(token))
			throw in.error("Invalid JSON scalar");

		return number(token);
	}

	static Object number(CharSequence value) {
		Number number = ParseUtils.getNumber(value);

		if (number == null)
			return value.toString();

		if (number instanceof Double && Double.isInfinite(number.doubleValue()))
			try {
				return new BigDecimal(value.toString());
			} catch (NumberFormatException ignored) {
			}

		return number;
	}

	private static boolean isJsonNumber(CharSequence value) {
		int length = value.length();

		if (length == 0)
			return false;

		int index = 0;

		if (value.charAt(index) == '-')
			if (++index == length)
				return false;

		char c = value.charAt(index);

		if (c == '0') {
			index++;

			// JSON nepovoluje např. 00, 01, -01
			if (index < length) {
				c = value.charAt(index);

				if (c >= '0' && c <= '9')
					return false;
			}
		} else {
			if (c < '1' || c > '9')
				return false;

			do {
				index++;

				if (index == length)
					return true;

				c = value.charAt(index);
			} while (c >= '0' && c <= '9');
		}

		if (index < length && value.charAt(index) == '.') {
			if (++index == length)
				return false;

			c = value.charAt(index);

			if (c < '0' || c > '9')
				return false;

			do {
				index++;

				if (index == length)
					return true;

				c = value.charAt(index);
			} while (c >= '0' && c <= '9');
		}

		if (index < length) {
			c = value.charAt(index);

			if (c != 'e' && c != 'E' || ++index == length)
				return false;

			c = value.charAt(index);

			if (c == '+' || c == '-')
				if (++index == length)
					return false;

			c = value.charAt(index);

			if (c < '0' || c > '9')
				return false;

			do
				index++;
			while (index < length && value.charAt(index) >= '0' && value.charAt(index) <= '9');
		}

		return index == length;
	}

	static String string(ConfigInput in) throws IOException {
		in.expect('"');

		StringContainer builder = new StringContainer(32);

		while (true) {
			int c = in.read();

			if (c == '"')
				return builder.toString();

			if (c < 0 || c < 32)
				throw in.error("Unterminated/invalid string");

			if (c == '\\')
				c = escape(in);

			builder.append((char) c);

			if (builder.length() > MATERIALIZATION_LIMIT)
				throw in.error("String exceeds materialization limit");
		}
	}

	static Object stringValue(ConfigInput in, ConfigDocument document) throws IOException {
		in.expect('"');

		AdaptiveTextBuilder builder = new AdaptiveTextBuilder(document);

		while (true) {
			int c = in.read();

			if (c == '"')
				return builder.finish();

			if (c < 0 || c < 32)
				throw in.error("Unterminated/invalid string");

			if (c == '\\')
				c = escape(in);

			builder.append((char) c);
		}
	}

	private static int escape(ConfigInput in) throws IOException {
		int c = in.read();

		switch (c) {
		case '"':
		case '\\':
		case '/':
			return c;

		case 'b':
			return '\b';

		case 'f':
			return '\f';

		case 'n':
			return '\n';

		case 'r':
			return '\r';

		case 't':
			return '\t';

		case 'u':
			return unicode(in);

		default:
			throw in.error("Invalid string escape");
		}
	}

	private static int unicode(ConfigInput in) throws IOException {
		int a = hex(in.read());
		int b = hex(in.read());
		int c = hex(in.read());
		int d = hex(in.read());

		if ((a | b | c | d) < 0)
			throw in.error("Invalid Unicode escape");

		return a << 12 | b << 8 | c << 4 | d;
	}

	private static int hex(int c) {
		if (c >= '0' && c <= '9')
			return c - '0';

		if (c >= 'a' && c <= 'f')
			return c - 'a' + 10;

		if (c >= 'A' && c <= 'F')
			return c - 'A' + 10;

		return -1;
	}

	private static boolean equals(CharSequence value, char a, char b, char c, char d) {
		return value.charAt(0) == a && value.charAt(1) == b && value.charAt(2) == c && value.charAt(3) == d;
	}

	private static boolean equals(CharSequence value, char a, char b, char c, char d, char e) {
		return value.charAt(0) == a && value.charAt(1) == b && value.charAt(2) == c && value.charAt(3) == d
				&& value.charAt(4) == e;
	}
}