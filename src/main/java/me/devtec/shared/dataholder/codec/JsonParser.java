package me.devtec.shared.dataholder.codec;

import java.io.IOException;
import java.io.Reader;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.store.NodeMetadata;

public final class JsonParser {
	public static final JsonParser INSTANCE = new JsonParser();
	private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern
			.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

	private JsonParser() {
	}

	@SuppressWarnings("resource")
	public void parse(Reader reader, ConfigDocument document) throws IOException {
		ConfigInput in = new ConfigInput(reader, "json");
		in.spaces();
		if (in.peek() == '{')
			object(in, document, 0, 0);
		else if (in.peek() == '[') {
			in.read();
			in.spaces();
			while (in.peek() != ']') {
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

	private void object(ConfigInput in, ConfigDocument d, int parent, int depth) throws IOException {
		if (depth > 256)
			throw in.error("Nesting exceeds 256");
		in.expect('{');
		in.spaces();
		if (in.peek() == '}') {
			in.read();
			if (parent != 0)
				d.put(parent, new java.util.LinkedHashMap<>(), new NodeMetadata(), false);
			return;
		}
		while (true) {
			String key = string(in);
			in.spaces();
			in.expect(':');
			in.spaces();
			int n = d.child(parent, key);
			if (in.peek() == '{')
				object(in, d, n, depth + 1);
			else
				d.put(n, value(in, d, depth + 1), new NodeMetadata(), false);
			in.spaces();
			int c = in.read();
			if (c == '}')
				break;
			if (c != ',')
				throw in.error("Expected ',' or '}'");
			in.spaces();
		}
	}

	static Object value(ConfigInput in, ConfigDocument d, int depth) throws IOException {
		if (depth > 256)
			throw in.error("Nesting exceeds 256");
		in.spaces();
		int c = in.peek();
		if (c == '"')
			return stringValue(in, d);
		if (c == '[' || c == '{') {
			boolean map = c == '{';
			int end = map ? '}' : ']';
			in.read();
			AdaptiveValueBuilder b = new AdaptiveValueBuilder(d, map);
			in.spaces();
			if (in.peek() == end) {
				in.read();
				return b.finish();
			}
			while (true) {
				Object key = null;
				if (map) {
					key = string(in);
					in.spaces();
					in.expect(':');
				}
				b.add(key, value(in, d, depth + 1));
				in.spaces();
				c = in.read();
				if (c == end)
					break;
				if (c != ',')
					throw in.error("Expected collection separator");
				in.spaces();
			}
			return b.finish();
		}
		StringContainer b = new StringContainer();
		while ((c = in.peek()) >= 0 && c > 32 && c != ',' && c != ']' && c != '}') {
			b.append((char) in.read());
			if (b.length() > 1024)
				throw in.error("Invalid scalar token");
		}
		String s = b.toString();
		if (s != null)
			switch (s) {
			case "true":
				return true;
			case "false":
				return false;
			case "null":
				return null;
			default:
				break;
			}
		if (!NUMBER.matcher(s).matches())
			throw in.error("Invalid JSON scalar");
		return number(s);
	}

	static Object number(String s) {
		try {
			if (s.indexOf('.') < 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0) {
				long n = Long.parseLong(s);
				if (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE)
					return Integer.valueOf((int) n);
				return Long.valueOf(n);
			}
			double v = Double.parseDouble(s);
			return Double.isInfinite(v) ? new java.math.BigDecimal(s) : Double.valueOf(v);
		} catch (NumberFormatException e) {
			try {
				return Double.valueOf(s);
			} catch (NumberFormatException ignored) {
				return s;
			}
		}
	}

	static String string(ConfigInput in) throws IOException {
		in.expect('"');
		StringContainer b = new StringContainer();
		final long materializationLimit = Math.min(16 * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16);
		int c;
		while ((c = in.read()) != '"') {
			if (c < 0 || c < 32)
				throw in.error("Unterminated/invalid string");
			if (c == '\\') {
				c = in.read();
				switch (c) {
				case '"':
				case '\\':
				case '/':
					break;
				case 'b':
					c = '\b';
					break;
				case 'f':
					c = '\f';
					break;
				case 'n':
					c = '\n';
					break;
				case 'r':
					c = '\r';
					break;
				case 't':
					c = '\t';
					break;
				case 'u':
					int v = 0;
					for (int i = 0; i < 4; i++) {
						int h = Character.digit(in.read(), 16);
						if (h < 0)
							throw in.error("Invalid Unicode escape");
						v = v * 16 + h;
					}
					c = v;
					break;
				default:
					throw in.error("Invalid string escape");
				}
			}
			b.append((char) c);
			if (b.length() > materializationLimit)
				throw in.error("String exceeds materialization limit");
		}
		return b.toString();
	}

	static Object stringValue(ConfigInput in, ConfigDocument d) throws IOException {
		in.expect('"');
		AdaptiveTextBuilder b = new AdaptiveTextBuilder(d);
		int c;
		while ((c = in.read()) != '"') {
			if (c < 0 || c < 32)
				throw in.error("Unterminated/invalid string");
			if (c == '\\') {
				c = in.read();
				switch (c) {
				case '"':
				case '\\':
				case '/':
					break;
				case 'b':
					c = '\b';
					break;
				case 'f':
					c = '\f';
					break;
				case 'n':
					c = '\n';
					break;
				case 'r':
					c = '\r';
					break;
				case 't':
					c = '\t';
					break;
				case 'u':
					int v = 0;
					for (int i = 0; i < 4; i++) {
						int h = Character.digit(in.read(), 16);
						if (h < 0)
							throw in.error("Invalid Unicode escape");
						v = v * 16 + h;
					}
					c = v;
					break;
				default:
					throw in.error("Invalid string escape");
				}
			}
			b.append((char) c);
		}
		return b.finish();
	}
}
