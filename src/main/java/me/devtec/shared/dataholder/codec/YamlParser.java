package me.devtec.shared.dataholder.codec;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.store.NodeMetadata;
import me.devtec.shared.json.Json;
import me.devtec.shared.utility.ParseUtils;

/**
 * One stateful operation parses directly into node ids; no chunks, loaders or
 * merge stage.
 */
public final class YamlParser {

	public static final YamlParser INSTANCE = new YamlParser();

	private static final int MAX_DEPTH = 256;
	private static final int MAX_KEY_LENGTH = 1024 * 1024;
	private static final int INLINE_LINE_LIMIT = 32768;

	private static final String LINE_SEPARATOR = System.lineSeparator();

	private YamlParser() {
	}

	public void parse(Reader reader, ConfigDocument document) throws IOException {
		new Session(reader, document).parse();
	}

	private static final class Scalar {
		Object value;
		String raw;
		String after;
	}

	private static final class Session {

		final ConfigInput in;
		final ConfigDocument d;

		String line;
		String text;

		int indent;
		boolean extended;

		final List<String> comments = new ArrayList<>();

		Session(Reader reader, ConfigDocument document) {
			in = new ConfigInput(reader, "yaml");
			d = document;
		}

		void next() throws IOException {
			if (extended)
				throw in.error("Unconsumed large YAML line");

			if (in.peek() < 0) {
				line = null;
				text = null;
				indent = 0;
				return;
			}

			StringContainer builder = new StringContainer(128);

			int c;

			while (builder.length() < INLINE_LINE_LIMIT && (c = in.read()) >= 0 && c != '\n')
				if (c != '\r')
					builder.append((char) c);

			int length = builder.length();

			if (length == INLINE_LINE_LIMIT) {
				int next = in.peek();

				extended = next >= 0 && next != '\n';

				if (!extended && next == '\n')
					in.read();
			} else
				extended = false;

			line = builder.toString();

			indent = 0;

			while (indent < length) {
				c = line.charAt(indent);

				if (c != ' ' && c != '\t')
					break;

				indent++;
			}

			int end = length;

			while (end > indent && line.charAt(end - 1) <= ' ')
				end--;

			if (indent == 0 && end == length)
				text = line;
			else if (indent == end)
				text = "";
			else
				text = line.substring(indent, end);
		}

		void trivia() throws IOException {
			while (line != null) {
				if (text.isEmpty()) {
					next();
					continue;
				}

				if (text.charAt(0) == '#') {
					comments.add(text);
					next();
					continue;
				}

				return;
			}
		}

		List<String> take() {
			if (comments.isEmpty())
				return null;

			List<String> result = new ArrayList<>(comments);
			comments.clear();
			return result;
		}

		void parse() throws IOException {
			next();
			trivia();

			if (line != null)
				mapping(0, indent, 0);

			trivia();

			if (line != null)
				throw in.error("Unexpected YAML content");

			if (!comments.isEmpty())
				d.footer.addAll(comments);
		}

		void mapping(int parent, int level, int depth) throws IOException {
			if (depth > MAX_DEPTH)
				throw in.error("Nesting exceeds " + MAX_DEPTH);

			while (line != null) {
				trivia();

				if (line == null || indent < level)
					return;

				if (indent != level || startsList(text))
					throw in.error("Unexpected YAML indentation");

				String current = text;
				int colon = current.indexOf(':');

				if (colon < 0)
					throw in.error("Expected mapping key and ':'");

				String key = unquoteTrimmed(current, 0, colon);

				if (key.length() > MAX_KEY_LENGTH)
					throw in.error("Key too long");

				int tokenStart = colon + 1;
				int tokenEnd = current.length();

				while (tokenStart < tokenEnd && current.charAt(tokenStart) <= ' ')
					tokenStart++;

				String token = tokenStart == tokenEnd ? "" : current.substring(tokenStart);

				List<String> before = take();
				int node = d.child(parent, key);

				Scalar scalar = scalar(token, false);
				String raw = scalar.raw;

				if (extended) {
					Object value = largeScalar(token);

					d.put(node, value, new NodeMetadata(null, null, before), false);

					next();
					continue;
				}

				next();

				if (token.isEmpty() || token.charAt(0) == '#') {
					trivia();

					if (line != null && startsList(text) && (indent > level || indent == level)) {

						Object list = list(indent, depth + 1);

						d.put(node, list, new NodeMetadata(null, scalar.after, before), false);
					} else if (line != null && indent > level) {
						if (before != null || scalar.after != null)
							d.put(node, null, new NodeMetadata(null, scalar.after, before), false);

						mapping(node, indent, depth + 1);
					} else if (before != null || scalar.after != null)
						d.put(node, null, new NodeMetadata(null, scalar.after, before), false);
					else
						d.storage.store().remove(d.storage.store().path(node), true);

					continue;
				}

				if (raw.length() == 1 && raw.charAt(0) == '|') {
					AdaptiveTextBuilder builder = new AdaptiveTextBuilder(d);

					int blockIndent = line == null ? level + 2 : indent;

					while (line != null && indent > level) {
						int from = Math.min(blockIndent, line.length());

						if (from < line.length())
							builder.append(line.substring(from));

						if (extended) {
							int c;

							while ((c = in.read()) >= 0 && c != '\n')
								if (c != '\r')
									builder.append((char) c);

							extended = false;
						}

						builder.append(LINE_SEPARATOR);
						next();
					}

					Object value = builder.finish();

					d.put(node, value,
							new NodeMetadata(value instanceof String ? (String) value : null, scalar.after, before),
							false);

					continue;
				}

				if (line != null && indent > level && !isComment(text)) {
					StringContainer continuation = new StringContainer(raw, 0, 64);

					while (line != null && indent > level && !isComment(text)) {
						if (!continuation.isEmpty())
							continuation.append(' ');

						continuation.append(text);
						next();
					}

					raw = continuation.toString();
					scalar.value = raw;
				}

				d.put(node, scalar.value, new NodeMetadata(raw, scalar.after, before), false);
			}
		}

		Object largeScalar(final String prefix) throws IOException {
			Reader reader = new Reader() {

				int position;
				boolean done;

				@Override
				public int read(char[] buffer, int offset, int length) throws IOException {
					if (done)
						return -1;

					if (length == 0)
						return 0;

					int written = 0;

					while (written < length) {
						int c = position < prefix.length() ? prefix.charAt(position++) : in.read();

						if (c < 0 || c == '\n') {
							done = true;
							extended = false;
							break;
						}

						if (c != '\r')
							buffer[offset + written++] = (char) c;
					}

					return written == 0 && done ? -1 : written;
				}

				@Override
				public void close() {
				}
			};

			char first = prefix.isEmpty() ? 0 : prefix.charAt(0);

			if (first == '[' || first == '{') {
				ConfigInput flow = new ConfigInput(reader, "yaml-flow");

				Object value = JsonParser.value(flow, d, 0);

				flow.spaces();

				if (flow.peek() != -1)
					throw flow.error("Trailing flow value content");

				return value;
			}

			AdaptiveTextBuilder builder = new AdaptiveTextBuilder(d);

			int quote = first == '"' ? '"' : first == '\'' ? '\'' : 0;

			int c;
			boolean firstChar = true;
			boolean finished = false;

			while ((c = reader.read()) >= 0) {
				if (firstChar) {
					firstChar = false;

					if (quote != 0)
						continue;
				}

				if (!finished && quote != 0 && c == quote) {
					finished = true;
					continue;
				}

				if (!finished)
					builder.append((char) c);
			}

			return builder.finish();
		}

		Object list(int level, int depth) throws IOException {
			if (depth > MAX_DEPTH)
				throw in.error("Nesting exceeds " + MAX_DEPTH);

			AdaptiveValueBuilder result = new AdaptiveValueBuilder(d, false);

			while (line != null) {
				trivia();

				if (line == null || indent != level || !startsList(text))
					break;

				String current = text;

				int start = 2;
				int length = current.length();

				while (start < length && current.charAt(start) <= ' ')
					start++;

				String token = start == length ? "" : current.substring(start);

				comments.clear();
				next();

				char first = token.isEmpty() ? 0 : token.charAt(0);

				if (first != '\'' && first != '"' && first != '{' && first != '[' && token.indexOf(':') >= 0) {

					Map<Object, Object> map = new LinkedHashMap<>();

					pair(map, token, level, depth + 1);

					while (line != null && indent > level) {
						trivia();

						if (line == null || indent <= level)
							break;

						String entry = text;
						int entryIndent = indent;

						next();

						pair(map, entry, entryIndent, depth + 1);
					}

					result.add(null, map);
				} else if (token.isEmpty() && line != null && indent > level && startsList(text))
					result.add(null, list(indent, depth + 1));
				else
					result.add(null, scalar(token, true).value);
			}

			return result.finish();
		}

		void pair(Map<Object, Object> map, String entry, int level, int depth) throws IOException {
			int colon = entry.indexOf(':');

			if (colon < 0)
				throw in.error("Expected list map key");

			String key = unquoteTrimmed(entry, 0, colon);

			int valueStart = colon + 1;
			int valueEnd = entry.length();

			while (valueStart < valueEnd && entry.charAt(valueStart) <= ' ')
				valueStart++;

			String value = valueStart == valueEnd ? "" : entry.substring(valueStart);

			if (value.isEmpty() && line != null && indent >= level && startsList(text))
				map.put(key, list(indent, depth + 1));
			else
				map.put(key, scalar(value, false).value);
		}
	}

	static String unquote(String token) {
		int length = token.length();

		if (length > 1) {
			char first = token.charAt(0);

			if ((first == '"' || first == '\'') && token.charAt(length - 1) == first)
				return token.substring(1, length - 1);
		}

		return token;
	}

	private static String unquoteTrimmed(String value, int start, int end) {
		while (start < end && value.charAt(start) <= ' ')
			start++;

		while (end > start && value.charAt(end - 1) <= ' ')
			end--;

		if (end - start > 1) {
			char first = value.charAt(start);

			if ((first == '"' || first == '\'') && value.charAt(end - 1) == first) {
				start++;
				end--;
			}
		}

		if (start == 0 && end == value.length())
			return value;

		return value.substring(start, end);
	}

	private static Scalar scalar(String token, boolean list) {
		Scalar scalar = new Scalar();

		int length = token.length();

		if (length == 0) {
			scalar.raw = "";
			scalar.value = null;
			return scalar;
		}

		char quote = token.charAt(0);
		boolean quoted = quote == '\'' || quote == '"';

		int position = quoted ? 1 : 0;
		int end = length;
		int commentAt = -1;

		StringContainer builder = null;
		int segmentStart = position;

		while (position < length) {
			char c = token.charAt(position);

			if (quoted) {
				if (c == quote) {
					end = position;
					position++;
					break;
				}

				if (c == '\\' && position + 1 < length) {
					char next = token.charAt(position + 1);

					if (next == quote || next == '\\') {
						if (builder == null)
							builder = new StringContainer(length);

						if (segmentStart < position)
							builder.append(token, segmentStart, position);

						builder.append(next);

						position += 2;
						segmentStart = position;
						continue;
					}
				}
			} else if (c == '#') {
				end = position;
				commentAt = position;
				break;
			}

			position++;
		}

		if (builder != null && segmentStart < end)
			builder.append(token, segmentStart, end);

		if (quoted) {
			while (position < length && token.charAt(position) <= ' ')
				position++;

			if (position < length && token.charAt(position) == '#')
				commentAt = position;
		}

		if (commentAt >= 0)
			scalar.after = token.substring(commentAt);

		if (builder != null)
			scalar.raw = builder.toString();
		else {
			int rawStart = quoted ? 1 : 0;
			int rawEnd = end;

			if (!quoted)
				while (rawEnd > rawStart && token.charAt(rawEnd - 1) <= ' ')
					rawEnd--;

			scalar.raw = rawStart == 0 && rawEnd == length ? token : token.substring(rawStart, rawEnd);
		}

		if (scalar.raw.isEmpty() && scalar.after != null) {
			scalar.value = null;
			return scalar;
		}

		// Quoted YAML scalars are strings.
		if (quoted)
			scalar.value = scalar.raw;
		else
			scalar.value = parseScalar(scalar.raw);

		return scalar;
	}

	static Object parseScalar(String text) {
		int start = 0;
		int end = text.length();

		while (start < end && text.charAt(start) <= ' ')
			start++;

		while (end > start && text.charAt(end - 1) <= ' ')
			end--;

		if (start == end)
			return "";

		char first = text.charAt(start);
		char last = text.charAt(end - 1);

		if (first == '[' && last == ']' || first == '{' && last == '}') {
			if (start == 0 && end == text.length())
				return Json.reader().read(text);

			return Json.reader().read(text.substring(start, end));
		}

		int length = end - start;

		if (length == 4) {
			if (equalsIgnoreCase(text, start, 't', 'r', 'u', 'e'))
				return Boolean.TRUE;

			if (equalsIgnoreCase(text, start, 'n', 'u', 'l', 'l'))
				return null;
		} else if (length == 5 && equalsIgnoreCase(text, start, 'f', 'a', 'l', 's', 'e'))
			return Boolean.FALSE;

		if (ParseUtils.isNumber(text, start, end)) {
			Number number = ParseUtils.getNumber(text, start, end);

			if (number != null)
				return number;
		}

		return start == 0 && end == text.length() ? text : text.substring(start, end);
	}

	private static boolean startsList(String text) {
		return text.length() >= 2 && text.charAt(0) == '-' && text.charAt(1) == ' ';
	}

	private static boolean isComment(String text) {
		return !text.isEmpty() && text.charAt(0) == '#';
	}

	private static boolean equalsIgnoreCase(String value, int offset, char a, char b, char c, char d) {
		return lower(value.charAt(offset)) == a && lower(value.charAt(offset + 1)) == b
				&& lower(value.charAt(offset + 2)) == c && lower(value.charAt(offset + 3)) == d;
	}

	private static boolean equalsIgnoreCase(String value, int offset, char a, char b, char c, char d, char e) {
		return lower(value.charAt(offset)) == a && lower(value.charAt(offset + 1)) == b
				&& lower(value.charAt(offset + 2)) == c && lower(value.charAt(offset + 3)) == d
				&& lower(value.charAt(offset + 4)) == e;
	}

	private static char lower(char c) {
		return c >= 'A' && c <= 'Z' ? (char) (c + 32) : c;
	}
}