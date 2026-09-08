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

		if (json.isEmpty())
			return json;

		try {
			StringParser parser = new StringParser(json);

			Object result = parser.parse();

			if (!parser.isFinished())
				return json;

			return result;
		} catch (RuntimeException ignored) {
			return json;
		}
	}

	public static Object fromJson(StringContainer text) {
		if (text == null)
			return null;

		if (text.length() == 0)
			return "";

		try {
			ContainerParser parser = new ContainerParser(text);

			Object result = parser.parse();

			if (!parser.isFinished())
				return text.toString();

			return result;
		} catch (RuntimeException ignored) {
			return text.toString();
		}
	}

	private static final class ContainerParser {
		private static final int ROOT = 0;
		private static final int MAP_KEY = 1;
		private static final int MAP_VALUE = 2;
		private static final int ARRAY_VALUE = 3;

		private final StringContainer text;
		private final int length;

		private int position;

		private ContainerParser(StringContainer text) {
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
				/*
				 * Whitespace je už odstraněný: - po { - nebo po předchozí čárce
				 */
				Object key = parseValue(MAP_KEY);

				skipWhitespace();

				if (position >= length || text.charAt(position) != COLON)
					throw invalid();

				++position; // :

				/*
				 * Za dvojtečkou whitespace být může, takže tady zůstává normální parseValue().
				 */
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
				/*
				 * Whitespace je už odstraněný: - po [ - nebo po předchozí čárce
				 */
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
			final int start = ++position;

			while (position < length) {
				char character = text.charAt(position++);

				if (character == quote)
					return text.substring(start, position - 1);

				if (character != SKIP_CHAR)
					continue;

				StringContainer result = new StringContainer(Math.max(16, position - start + 8));

				if (position - 1 > start)
					result.append(text, start, position - 1);

				if (position >= length)
					throw invalid();

				appendEscaped(result, text.charAt(position++));

				parseEscapedString(result, quote);

				return result.toString();
			}

			throw invalid();
		}

		private void appendEscaped(StringContainer result, char escaped) {
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
				result.append(SKIP_CHAR);
				result.append(escaped);
				break;
			}
		}

		private void parseEscapedString(StringContainer result, char quote) {
			while (position < length) {
				char character = text.charAt(position++);

				if (character == quote)
					return;

				if (character != SKIP_CHAR) {
					result.append(character);
					continue;
				}

				if (position >= length)
					throw invalid();

				appendEscaped(result, text.charAt(position++));
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

		// =================================================================
		// Bare values
		// =================================================================

		private Object parseBareValue(int mode) {
			int start = position;

			switch (mode) {
			case MAP_KEY:
				while (position < length && text.charAt(position) != COLON)
					++position;
				break;

			case MAP_VALUE:
				while (position < length) {
					char character = text.charAt(position);

					if (character == COMMA || character == CLOSED_BRACE)
						break;

					++position;
				}
				break;

			case ARRAY_VALUE:
				while (position < length) {
					char character = text.charAt(position);

					if (character == COMMA || character == CLOSED_BRACKET)
						break;

					++position;
				}
				break;

			case ROOT:
			default:
				position = length;
				break;
			}

			int end = position;

			while (start < end && isWhitespace(text.charAt(start)))
				++start;

			while (end > start && isWhitespace(text.charAt(end - 1)))
				--end;

			if (start >= end)
				throw invalid();

			int tokenLength = end - start;

			if (tokenLength == 4) {
				char first = text.charAt(start);

				if (first == 't' && text.charAt(start + 1) == 'r' && text.charAt(start + 2) == 'u'
						&& text.charAt(start + 3) == 'e')
					return Boolean.TRUE;

				if (first == 'n' && text.charAt(start + 1) == 'u' && text.charAt(start + 2) == 'l'
						&& text.charAt(start + 3) == 'l')
					return null;

			} else if (tokenLength == 5 && text.charAt(start) == 'f' && text.charAt(start + 1) == 'a'
					&& text.charAt(start + 2) == 'l' && text.charAt(start + 3) == 's' && text.charAt(start + 4) == 'e')
				return Boolean.FALSE;

			if (ParseUtils.isNumber(text, start, end)) {
				Number number = ParseUtils.getNumber(text, start, end);

				if (number != null)
					return number;
			}

			return text.substring(start, end);
		}

		// =================================================================
		// Whitespace
		// =================================================================

		private void skipWhitespace() {
			while (position < length && isWhitespace(text.charAt(position)))
				++position;
		}

		private IllegalArgumentException invalid() {
			return new IllegalArgumentException("Invalid JSON at position " + position);
		}
	}

	private static final class StringParser {
		private static final int ROOT = 0;
		private static final int MAP_KEY = 1;
		private static final int MAP_VALUE = 2;
		private static final int ARRAY_VALUE = 3;

		private final String text;
		private final int length;

		private int position;

		private StringParser(String text) {
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
				/*
				 * Whitespace je už odstraněný: - po { - nebo po předchozí čárce
				 */
				Object key = parseValue(MAP_KEY);

				skipWhitespace();

				if (position >= length || text.charAt(position) != COLON)
					throw invalid();

				++position; // :

				/*
				 * Za dvojtečkou whitespace být může, takže tady zůstává normální parseValue().
				 */
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
				/*
				 * Whitespace je už odstraněný: - po [ - nebo po předchozí čárce
				 */
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
			final int start = ++position;

			while (position < length) {
				char character = text.charAt(position++);

				if (character == quote)
					return text.substring(start, position - 1);

				if (character != SKIP_CHAR)
					continue;

				StringContainer result = new StringContainer(Math.max(16, position - start + 8));

				if (position - 1 > start)
					result.append(text, start, position - 1);

				if (position >= length)
					throw invalid();

				appendEscaped(result, text.charAt(position++));
				parseEscapedString(result, quote);

				return result.toString();
			}

			throw invalid();
		}

		private void appendEscaped(StringContainer result, char escaped) {
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
				result.append(SKIP_CHAR);
				result.append(escaped);
				break;
			}
		}

		private void parseEscapedString(StringContainer result, char quote) {
			while (position < length) {
				char character = text.charAt(position++);

				if (character == quote)
					return;

				if (character != SKIP_CHAR) {
					result.append(character);
					continue;
				}

				if (position >= length)
					throw invalid();

				appendEscaped(result, text.charAt(position++));
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

		// =================================================================
		// Bare values
		// =================================================================

		private Object parseBareValue(int mode) {
			int start = position;

			switch (mode) {
			case MAP_KEY:
				while (position < length && text.charAt(position) != COLON)
					++position;
				break;

			case MAP_VALUE:
				while (position < length) {
					char character = text.charAt(position);

					if (character == COMMA || character == CLOSED_BRACE)
						break;

					++position;
				}
				break;

			case ARRAY_VALUE:
				while (position < length) {
					char character = text.charAt(position);

					if (character == COMMA || character == CLOSED_BRACKET)
						break;

					++position;
				}
				break;

			case ROOT:
			default:
				position = length;
				break;
			}

			int end = position;

			while (start < end && isWhitespace(text.charAt(start)))
				++start;

			while (end > start && isWhitespace(text.charAt(end - 1)))
				--end;

			if (start >= end)
				throw invalid();

			int tokenLength = end - start;

			if (tokenLength == 4) {
				char first = text.charAt(start);

				if (first == 't' && text.charAt(start + 1) == 'r' && text.charAt(start + 2) == 'u'
						&& text.charAt(start + 3) == 'e')
					return Boolean.TRUE;

				if (first == 'n' && text.charAt(start + 1) == 'u' && text.charAt(start + 2) == 'l'
						&& text.charAt(start + 3) == 'l')
					return null;

			} else if (tokenLength == 5 && text.charAt(start) == 'f' && text.charAt(start + 1) == 'a'
					&& text.charAt(start + 2) == 'l' && text.charAt(start + 3) == 's' && text.charAt(start + 4) == 'e')
				return Boolean.FALSE;

			if (ParseUtils.isNumber(text, start, end)) {
				Number number = ParseUtils.getNumber(text, start, end);

				if (number != null)
					return number;
			}

			return text.substring(start, end);
		}
		// =================================================================
		// Whitespace
		// =================================================================

		private void skipWhitespace() {
			while (position < length && isWhitespace(text.charAt(position)))
				++position;
		}

		private IllegalArgumentException invalid() {
			return new IllegalArgumentException("Invalid JSON at position " + position);
		}
	}

	private static boolean isWhitespace(char character) {
		return character == ' ' || character == '\t' || character == '\r' || character == '\n';
	}

	private static int hexValue(char character) {
		if (character >= '0' && character <= '9')
			return character - '0';

		if (character >= 'a' && character <= 'f')
			return character - 'a' + 10;

		if (character >= 'A' && character <= 'F')
			return character - 'A' + 10;

		return -1;
	}

	@Override
	public String toString() {
		return "CustomJsonReader";
	}
}