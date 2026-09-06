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

/**
 * One stateful operation parses directly into node ids; no chunks, loaders or
 * merge stage.
 */
public final class YamlParser {
	public static final YamlParser INSTANCE = new YamlParser();

	private YamlParser() {
	}

	public void parse(Reader reader, ConfigDocument document) throws IOException {
		new Session(reader, document).parse();
	}

	private static final class Scalar {
		Object value;
		String raw, after;
	}

	private static final class Session {
		final ConfigInput in;
		final ConfigDocument d;
		String line;
		int indent;
		boolean extended;
		List<String> comments = new ArrayList<>();

		Session(Reader reader, ConfigDocument doc) {
			in = new ConfigInput(reader, "yaml");
			d = doc;
		}

		void next() throws IOException {
			if (extended)
				throw in.error("Unconsumed large YAML line");
			if (in.peek() < 0) {
				line = null;
				return;
			}
			StringContainer b = new StringContainer();
			int c;
			while (b.length() < 32768 && (c = in.read()) >= 0 && c != '\n')
				if (c != '\r')
					b.append((char) c);
			extended = b.length() == 32768 && in.peek() >= 0 && in.peek() != '\n';
			if (b.length() == 32768 && in.peek() == '\n')
				in.read();
			line = b.toString();
			indent = 0;
			while (indent < line.length() && (line.charAt(indent) == ' ' || line.charAt(indent) == '\t'))
				indent++;
		}

		String text() {
			return line.substring(indent).trim();
		}

		void trivia() throws IOException {
			while (line != null && (text().isEmpty() || text().startsWith("#"))) {
				if (!text().isEmpty())
					comments.add(text());
				next();
			}
		}

		List<String> take() {
			if (comments.isEmpty())
				return null;
			List<String> result = comments;
			comments = new ArrayList<>();
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
			d.footer.addAll(comments);
		}

		void mapping(int parent, int level, int depth) throws IOException {
			if (depth > 256)
				throw in.error("Nesting exceeds 256");
			while (line != null) {
				trivia();
				if (line == null || indent < level)
					return;
				if (indent != level || text().startsWith("- "))
					throw in.error("Unexpected YAML indentation");
				String t = text();
				int colon = t.indexOf(':');
				if (colon < 0)
					throw in.error("Expected mapping key and ':'");
				String key = unquote(t.substring(0, colon).trim());
				if (key.length() > 1024 * 1024)
					throw in.error("Key too long");
				String token = t.substring(colon + 1).trim();
				List<String> before = take();
				int n = d.child(parent, key);
				Scalar scalar = scalar(token, false);
				String raw = scalar.raw;
				if (extended) {
					Object value = largeScalar(token);
					d.put(n, value, new NodeMetadata(null, null, before), false);
					next();
					continue;
				}
				next();
				if (token.isEmpty() || token.startsWith("#")) {
					trivia();
					if (line != null && (indent > level || indent == level && text().startsWith("- "))
							&& text().startsWith("- ")) {
						Object list = list(indent, depth + 1);
						d.put(n, list, new NodeMetadata(null, scalar.after, before), false);
					} else if (line != null && indent > level) {
						if (before != null || scalar.after != null)
							d.put(n, null, new NodeMetadata(null, scalar.after, before), false);
						mapping(n, indent, depth + 1);
					} else if (before != null || scalar.after != null)
						d.put(n, null, new NodeMetadata(null, scalar.after, before), false);
					else
						d.storage.store().remove(d.storage.store().path(n), true);
				} else if ("|".equals(raw)) {
					AdaptiveTextBuilder b = new AdaptiveTextBuilder(d);
					int blockIndent = line == null ? level + 2 : indent;
					while (line != null && indent > level) {
						b.append(line.substring(Math.min(blockIndent, line.length())));
						if (extended) {
							int c;
							while ((c = in.read()) >= 0 && c != '\n')
								if (c != '\r')
									b.append((char) c);
							extended = false;
						}
						b.append(System.lineSeparator());
						next();
					}
					Object v = b.finish();
					d.put(n, v, new NodeMetadata(v instanceof String ? (String) v : null, scalar.after, before), false);
				} else {
					while (line != null && indent > level && !text().startsWith("#")) {
						raw = raw + ' ' + text();
						scalar.value = raw;
						next();
					}
					d.put(n, scalar.value, new NodeMetadata(raw, scalar.after, before), false);
				}
			}
		}

		Object largeScalar(final String prefix) throws IOException {
			Reader reader = new Reader() {
				int pos;
				boolean done;

				@Override
				public int read(char[] b, int off, int len) throws IOException {
					if (done)
						return -1;
					int n = 0;
					while (n < len) {
						int c = pos < prefix.length() ? prefix.charAt(pos++) : in.read();
						if (c < 0 || c == '\n') {
							done = true;
							extended = false;
							break;
						}
						if (c != '\r')
							b[off + n++] = (char) c;
					}
					return n == 0 && done ? -1 : n;
				}

				@Override
				public void close() {
				}
			};
			if (prefix.startsWith("[") || prefix.startsWith("{")) {
				ConfigInput flow = new ConfigInput(reader, "yaml-flow");
				Object v = JsonParser.value(flow, d, 0);
				flow.spaces();
				if (flow.peek() != -1)
					throw flow.error("Trailing flow value content");
				return v;
			}
			AdaptiveTextBuilder b = new AdaptiveTextBuilder(d);
			int quote = prefix.startsWith("\"") ? '"' : prefix.startsWith("'") ? '\'' : 0;
			int c;
			boolean first = true, done = false;
			while ((c = reader.read()) >= 0) {
				if (first && quote != 0) {
					first = false;
					continue;
				}
				first = false;
				if (!done && c == quote && quote != 0) {
					done = true;
					continue;
				}
				if (!done)
					b.append((char) c);
			}
			return b.finish();
		}

		Object list(int level, int depth) throws IOException {
			if (depth > 256)
				throw in.error("Nesting exceeds 256");
			AdaptiveValueBuilder result = new AdaptiveValueBuilder(d, false);
			while (line != null) {
				trivia();
				if (line == null || indent != level || !text().startsWith("- "))
					break;
				String t = text().substring(2).trim();
				comments.clear();
				next();
				if (!t.startsWith("'") && !t.startsWith("\"") && !t.startsWith("{") && !t.startsWith("[")
						&& t.indexOf(':') >= 0) {
					Map<Object, Object> map = new LinkedHashMap<>();
					pair(map, t, level, depth + 1);
					while (line != null && indent > level) {
						trivia();
						if (line == null || indent <= level)
							break;
						String entry = text();
						int entryIndent = indent;
						next();
						pair(map, entry, entryIndent, depth + 1);
					}
					result.add(null, map);
				} else if (t.isEmpty() && line != null && indent > level && text().startsWith("- "))
					result.add(null, list(indent, depth + 1));
				else
					result.add(null, scalar(t, true).value);
			}
			return result.finish();
		}

		void pair(Map<Object, Object> map, String text, int level, int depth) throws IOException {
			int colon = text.indexOf(':');
			if (colon < 0)
				throw in.error("Expected list map key");
			String key = unquote(text.substring(0, colon).trim());
			String value = text.substring(colon + 1).trim();
			if (value.isEmpty() && line != null && indent >= level && text().startsWith("- "))
				map.put(key, list(indent, depth + 1));
			else
				map.put(key, scalar(value, false).value);
		}
	}

	static String unquote(String token) {
		if (token.length() > 1 && (token.charAt(0) == '"' || token.charAt(0) == '\'')
				&& token.charAt(token.length() - 1) == token.charAt(0))
			return token.substring(1, token.length() - 1);
		return token;
	}

	private static Scalar scalar(String token, boolean list) {
		Scalar s = new Scalar();
		if (token.isEmpty()) {
			s.raw = "";
			s.value = null;
			return s;
		}
		char quote = token.charAt(0);
		boolean quoted = quote == '\'' || quote == '"';
		StringContainer b = new StringContainer();
		int i = quoted ? 1 : 0;
		for (; i < token.length(); i++) {
			char c = token.charAt(i);
			if (quoted && c == quote) {
				i++;
				break;
			}
			if (c == '\\' && i + 1 < token.length() && (token.charAt(i + 1) == quote || token.charAt(i + 1) == '\\')) {
				b.append(token.charAt(++i));
				continue;
			}
			if (!quoted && c == '#')
				break;
			b.append(c);
		}
		int hash = token.indexOf('#', i);
		if (hash >= 0)
			s.after = token.substring(hash);
		s.raw = b.toString();
		if (s.raw.isEmpty() && s.after != null) {
			s.value = null;
			return s;
		}
		s.value = list && quoted ? s.raw : parseScalar(s.raw);
		return s;
	}

	static Object parseScalar(String text) {
		if (text.isEmpty())
			return text;
		char c = text.charAt(0);
		if (c == '[' && text.endsWith("]") || c == '{' && text.endsWith("}"))
			return Json.reader().read(text);
		if (Character.isDigit(c) || c == '-' || c == '+' || c == '.' || "true".equalsIgnoreCase(text.trim())
				|| "false".equalsIgnoreCase(text.trim()) || "null".equalsIgnoreCase(text.trim()))
			return Json.reader().read(text);
		return text;
	}
}
