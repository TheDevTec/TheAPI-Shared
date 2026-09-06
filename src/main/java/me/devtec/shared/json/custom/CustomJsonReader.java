package me.devtec.shared.json.custom;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.json.JReader;
import me.devtec.shared.utility.ParseUtils;

public final class CustomJsonReader implements JReader {

	static final char COLON = ':';
	static final char QUOTES = '"';
	static final char QUOTES2 = '\'';
	static final char COMMA = ',';

	static final char OPEN_BRACE = '{';
	static final char OPEN_BRACKET = '[';
	static final char CLOSED_BRACE = '}';
	static final char CLOSED_BRACKET = ']';

	static final char SKIP_CHAR = '\\';

	public CustomJsonReader() {
	}

	@Override
	public Object fromGson(String json, Class<?> clazz) {
		return fromJson(json);
	}

	public static Object fromJson(String json) {
		if (json == null)
			return null;

		return fromJson(new StringContainer(json));
	}

	public static Object fromJson(StringContainer text) {
		if (text == null)
			return null;

		String original = text.toString();

		if (original.isEmpty())
			return original;

		try {
			Parser parser = new Parser(text);

			Object result = parser.parse();

			if (!parser.isFinished())
				return original;

			return result;
		} catch (Throwable ignored) {
			return original;
		}
	}

	private static final class Parser {

		private static final int ROOT = 0;
		private static final int MAP_KEY = 1;
		private static final int MAP_VALUE = 2;
		private static final int ARRAY_VALUE = 3;

		private final StringContainer text;
		private final int length;

		private int position;

		private Parser(StringContainer text) {
			this.text = text;
			length = text.length();
		}

		private Object parse() {
			skipWhitespace();

			/*
			 * UTF-8 BOM converted into a Java char.
			 */
			if (position < length && text.charAt(position) == '\uFEFF') {
				++position;
				skipWhitespace();
			}

			if (position >= length)
				return "";

			Object result = parseValue(ROOT);

			skipWhitespace();

			return result;
		}

		private boolean isFinished() {
			skipWhitespace();
			return position >= length;
		}

		// =================================================================
		// Value
		// =================================================================

		private Object parseValue(int mode) {
			skipWhitespace();

			if (position >= length)
				throw invalid();

			char character = text.charAt(position);

			switch (character) {
			case OPEN_BRACE:
				return parseMap();

			case OPEN_BRACKET:
				return parseList();

			case QUOTES:
			case QUOTES2:
				return parseString(character);

			default:
				return parseBareValue(mode);
			}
		}

		// =================================================================
		// Map
		// =================================================================

		private Map<Object, Object> parseMap() {
			++position; // {

			Map<Object, Object> result = new LinkedHashMap<>();

			skipWhitespace();

			if (position < length && text.charAt(position) == CLOSED_BRACE) {

				++position;
				return result;
			}

			while (position < length) {
				Object key = parseValue(MAP_KEY);

				skipWhitespace();

				if (position >= length || text.charAt(position) != COLON)
					throw invalid();

				++position; // :

				Object value = parseValue(MAP_VALUE);

				result.put(key, value);

				skipWhitespace();

				if (position >= length)
					throw invalid();

				char character = text.charAt(position);

				if (character == COMMA) {
					++position;

					skipWhitespace();

					/*
					 * Don't allow:
					 *
					 * {"a":1,}
					 */
					if (position < length && text.charAt(position) == CLOSED_BRACE)
						throw invalid();

					continue;
				}

				if (character == CLOSED_BRACE) {
					++position;
					return result;
				}

				throw invalid();
			}

			throw invalid();
		}

		// =================================================================
		// List
		// =================================================================

		private List<Object> parseList() {
			++position; // [

			List<Object> result = new ArrayList<>();

			skipWhitespace();

			if (position < length && text.charAt(position) == CLOSED_BRACKET) {

				++position;
				return result;
			}

			while (position < length) {
				result.add(parseValue(ARRAY_VALUE));

				skipWhitespace();

				if (position >= length)
					throw invalid();

				char character = text.charAt(position);

				if (character == COMMA) {
					++position;

					skipWhitespace();

					/*
					 * Don't allow:
					 *
					 * [1,2,]
					 */
					if (position < length && text.charAt(position) == CLOSED_BRACKET)
						throw invalid();

					continue;
				}

				if (character == CLOSED_BRACKET) {
					++position;
					return result;
				}

				throw invalid();
			}

			throw invalid();
		}

		// =================================================================
		// String
		// =================================================================

		private String parseString(char quote) {
			++position;

			StringContainer result = new StringContainer();

			while (position < length) {
				char character = text.charAt(position++);

				if (character == quote)
					return result.toString();

				if (character != SKIP_CHAR) {
					result.append(character);
					continue;
				}

				if (position >= length) {
					result.append(SKIP_CHAR);
					break;
				}

				char escaped = text.charAt(position++);

				switch (escaped) {
				case '"':
					result.append('"');
					break;

				case '\'':
					result.append('\'');
					break;

				case '\\':
					result.append('\\');
					break;

				case '/':
					result.append('/');
					break;

				case 'b':
					result.append('\b');
					break;

				case 'f':
					result.append('\f');
					break;

				case 'n':
					result.append('\n');
					break;

				case 'r':
					result.append('\r');
					break;

				case 't':
					result.append('\t');
					break;

				case 'u':
					result.append(readUnicode());
					break;

				default:
					/*
					 * Preserve unknown escape sequences for backwards compatibility with the old
					 * parser.
					 *
					 * "\x" -> "\x"
					 */
					result.append(SKIP_CHAR);
					result.append(escaped);
					break;
				}
			}

			throw invalid();
		}

		private char readUnicode() {
			if (position + 4 > length)
				throw invalid();

			int value = 0;

			for (int i = 0; i < 4; ++i) {
				int hex = hexValue(text.charAt(position++));

				if (hex == -1)
					throw invalid();

				value = value << 4 | hex;
			}

			return (char) value;
		}

		private int hexValue(char character) {
			if (character >= '0' && character <= '9')
				return character - '0';

			if (character >= 'a' && character <= 'f')
				return character - 'a' + 10;

			if (character >= 'A' && character <= 'F')
				return character - 'A' + 10;

			return -1;
		}

		// =================================================================
		// Bare values
		// =================================================================

		private Object parseBareValue(int mode) {
			int start = position;

			while (position < length) {
				char character = text.charAt(position);

				if (isBareEnding(character, mode))
					break;

				++position;
			}

			int end = position;

			while (start < end && isWhitespace(text.charAt(start)))
				++start;

			while (end > start && isWhitespace(text.charAt(end - 1)))
				--end;

			if (start >= end)
				throw invalid();

			String value = text.substring(start, end);

			if (value != null)
				switch (value) {
				case "true":
					return Boolean.TRUE;
				case "false":
					return Boolean.FALSE;
				case "null":
					return null;
				default:
					break;
				}

			if (isNumber(value)) {
				Number number = ParseUtils.getNumber(new StringContainer(value));

				if (number != null)
					return number;
			}

			/*
			 * Legacy / lenient syntax:
			 *
			 * [hello, world] {hello: world}
			 *
			 * remains supported and becomes String.
			 */
			return value;
		}

		private boolean isBareEnding(char character, int mode) {
			switch (mode) {
			case MAP_KEY:
				return character == COLON;

			case MAP_VALUE:
				return character == COMMA || character == CLOSED_BRACE;

			case ARRAY_VALUE:
				return character == COMMA || character == CLOSED_BRACKET;

			case ROOT:
			default:
				return false;
			}
		}

		// =================================================================
		// Number
		// =================================================================

		private boolean isNumber(String value) {
			if (value == null || value.isEmpty())
				return false;

			int length = value.length();
			int index = 0;

			char character = value.charAt(index);

			/*
			 * + isn't valid strict JSON, but the old reader supported it.
			 */
			if (character == '-' || character == '+') {
				++index;

				if (index >= length)
					return false;
			}

			boolean digits = false;

			while (index < length) {
				character = value.charAt(index);

				if (character < '0' || character > '9')
					break;

				digits = true;
				++index;
			}

			/*
			 * Keep backwards compatibility with .5
			 */
			if (index < length && value.charAt(index) == '.') {
				++index;

				boolean decimalDigits = false;

				while (index < length) {
					character = value.charAt(index);

					if (character < '0' || character > '9')
						break;

					digits = true;
					decimalDigits = true;
					++index;
				}

				if (!decimalDigits && !digits)
					return false;
			}

			if (!digits)
				return false;

			if (index < length && (value.charAt(index) == 'e' || value.charAt(index) == 'E')) {

				++index;

				if (index < length && (value.charAt(index) == '-' || value.charAt(index) == '+'))
					++index;

				int exponentStart = index;

				while (index < length) {
					character = value.charAt(index);

					if (character < '0' || character > '9')
						break;

					++index;
				}

				if (index == exponentStart)
					return false;
			}

			return index == length;
		}

		// =================================================================
		// Whitespace
		// =================================================================

		private void skipWhitespace() {
			while (position < length && isWhitespace(text.charAt(position)))
				++position;
		}

		private boolean isWhitespace(char character) {
			return character == ' ' || character == '\t' || character == '\r' || character == '\n';
		}

		private IllegalArgumentException invalid() {
			return new IllegalArgumentException("Invalid JSON at position " + position);
		}
	}

	@Override
	public String toString() {
		return "CustomJsonReader";
	}
}