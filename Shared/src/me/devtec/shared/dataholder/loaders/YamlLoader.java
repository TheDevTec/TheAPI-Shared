package me.devtec.shared.dataholder.loaders;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Queues;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class YamlLoader extends EmptyLoader {
	private static final Pattern pattern = Pattern.compile("( *)(['\"][^'\"]+['\"]|[^\"']?\\w+[^\"']?|.*?): ?(.*)");

	@Override
	public void load(String input) {
		reset();
		if (input == null)
			return;
		try {
			// SPACES - POSITION
			int last = 0;

			// EXTRA BUILDER TYPE
			BuilderType type = null;
			// LIST OR EXTRA BUILDER
			LinkedList<Object> items = null;
			int spaces = 0;
			// EXTRA BUILDER
			StringContainer builder = null;

			// BUILDER
			String key = "";
			String value = null;

			// COMMENTS
			LinkedList<String> comments = new LinkedList<>();

			int linePos = 0;
			for (String line : input.split(System.lineSeparator())) {
				String trim = line.trim();
				if (trim.isEmpty()) {
					if (linePos != 0)
						comments.add("");
					continue;
				}
				++linePos;
				String e = line.substring(YamlLoader.removeSpaces(line));
				if (trim.charAt(0) == '#') {
					comments.add(e);
					continue;
				}

				if (!key.equals("") && e.startsWith("- ")) {
					if (items == null)
						items = new LinkedList<>();
					items.add(Json.reader().read(YamlLoader.r(e.substring(2))));
					continue;
				}

				Matcher match = YamlLoader.pattern.matcher(line);
				if (match.find()) {
					int sub = match.group(1).length();

					if (type != null) {
						if (type == BuilderType.LIST) {
							if (sub == spaces) {
								items.add(Json.reader().read(YamlLoader.r(line.substring(sub))));
								continue;
							}
							spaces = 0;
							data.put(key, DataValue.of(null, new LinkedList<>(items), null, comments.isEmpty() ? null : Config.simple(new LinkedList<>(comments))));
							comments.clear();
							items = null;
						} else {
							if (sub == spaces) {
								builder.append(line.substring(sub));
								continue;
							}
							spaces = 0;
							data.put(key, DataValue.of(null, builder.toString(), null, comments.isEmpty() ? null : Config.simple(new LinkedList<>(comments))));
							comments.clear();
							builder = null;
						}
						type = null;
					} else if (items != null) {
						data.get(key).value = new LinkedList<>(items);
						items = null;
					}

					String keyr = YamlLoader.r(match.group(2));
					value = match.group(3);

					if (sub <= last)
						if (sub == 0)
							key = "";
						else if (sub == last) {
							int remove = key.lastIndexOf('.');
							if (remove > 0)
								key = key.substring(0, remove);
						} else
							for (int i = 0; i < Math.abs(last - sub) / 2 + 1; ++i) {
								int remove = key.lastIndexOf('.');
								if (remove < 0)
									break;
								key = key.substring(0, remove);
							}

					last = sub;
					if (!key.isEmpty())
						key += ".";
					key += keyr;

					String[] valueSplit = YamlLoader.splitFromComment(value);
					if (valueSplit[0].trim().isEmpty() && !value.contains("\"") && !value.contains("'")) {
						value = null;
						data.put(key, DataValue.of(null, null, null, comments.isEmpty() ? null : Config.simple(new LinkedList<>(comments))));
						comments.clear();
						continue;
					}

					value = valueSplit[0];

					if (value.equals("|")) {
						type = BuilderType.STRING;
						spaces = sub + 2; // DEFAULT
						builder = new StringContainer(32);
						continue;
					}
					if (value.equals("|-")) {
						type = BuilderType.LIST;
						spaces = sub + 2; // DEFAULT
						items = new LinkedList<>();
						continue;
					}
					if (value.equals("[]")) {
						data.put(key,
								DataValue.of("[]", Collections.emptyList(), valueSplit.length == 2 ? valueSplit[1] : null, comments.isEmpty() ? null : Config.simple(new LinkedList<>(comments))));
						comments.clear();
						continue;
					}
					data.put(key, DataValue.of(value, Json.reader().read(value), valueSplit.length == 2 ? valueSplit[1] : null, comments.isEmpty() ? null : Config.simple(new LinkedList<>(comments))));
					comments.clear();
				} else if (type != null)
					if (type == BuilderType.LIST) {
						int space = YamlLoader.removeSpaces(line);
						spaces = space;
						items.add(Json.reader().read(YamlLoader.r(line.substring(space))));
					} else {
						int space = YamlLoader.removeSpaces(line);
						spaces = space;
						builder.append(line.substring(space));
					}
			}
			loaded = true;
			if (type != null) {
				if (type == BuilderType.LIST)
					data.put(key, DataValue.of(null, items, null, comments.isEmpty() ? null : Config.simple(comments)));
				else
					data.put(key, DataValue.of(builder.toString(), builder.toString(), null, comments.isEmpty() ? null : Config.simple(comments)));
				return;
			}
			if (items != null) {
				data.put(key, DataValue.of(null, items, null, comments.isEmpty() ? null : Config.simple(comments)));
				return;
			}
			if (data.isEmpty())
				header.addAll(Config.simple(comments));
			else
				footer.addAll(Config.simple(comments));
		} catch (Exception er) {
			er.printStackTrace();
			loaded = false;
		}
	}

	public enum BuilderType {
		STRING, LIST
	}

	public static int removeSpaces(String s) {
		int i = 0;
		for (int d = 0; d < s.length(); ++d) {
			if (s.charAt(d) != ' ')
				break;
			++i;
		}
		return i;
	}

	protected static String r(String key) {
		String modKey = key.substring(0, key.length() - YamlLoader.removeLastSpaces(key));
		return modKey.length() > 1 && (modKey.startsWith("\"") && modKey.endsWith("\"") || modKey.startsWith("'") && modKey.endsWith("'")) ? modKey.substring(1, modKey.length() - 1) : modKey;
	}

	public static int removeLastSpaces(String s) {
		int i = 0;
		for (int d = s.length() - 1; d > 0; --d) {
			if (s.charAt(d) != ' ')
				break;
			++i;
		}
		return i;
	}

	public static String[] splitFromComment(String group) {
		if (group.isEmpty() || group.length() == 1)
			return new String[] { group };
		String[] values = null;
		StringContainer builder = new StringContainer(group.length());
		boolean insideQuetos = false;
		boolean insideJson = false;
		boolean insideQuetosJson = false;
		boolean comment = false;
		boolean spaceCounting = true;
		char quetoChar = 0;
		int spaces = 0;

		ArrayDeque<Character> jsonChars = null;

		char posChar = group.charAt(0);
		if (posChar == '"' || posChar == '\'') { // first char is often queto
			quetoChar = posChar;
			insideQuetos = true;
			spaceCounting = false;
		}
		if (posChar == '{' || posChar == '[') { // first char is often queto
			insideJson = true;
			spaceCounting = false;
			jsonChars = Queues.newArrayDeque();
			jsonChars.add(posChar);
		}

		for (int pos = insideQuetos ? 1 : 0; pos < group.length(); ++pos) {
			posChar = group.charAt(pos);

			if (comment) {
				builder.append(posChar);
				continue;
			}

			if (insideJson && (posChar == '"' || posChar == '\''))
				insideQuetosJson = !insideQuetosJson;

			if (!insideQuetosJson && insideJson && (posChar == '[' || posChar == '{'))
				jsonChars.add(posChar);

			if (posChar == '#' && !insideQuetos && !insideJson) {
				comment = true;
				values = new String[2];
				String value = builder.toString();
				values[0] = value.substring(0, value.length() - spaces);
				builder.clear();
				builder.append(posChar);
				continue;
			}

			if (!insideQuetosJson && insideJson && posChar == jsonChars.getLast()) {
				jsonChars.peekLast();
				if (jsonChars.isEmpty()) {
					insideJson = false;
					spaceCounting = true;
				}
			}
			if (insideQuetos && posChar == quetoChar) {
				insideQuetos = false;
				spaceCounting = true;
				continue;
			}

			if (spaceCounting && posChar == ' ')
				++spaces;
			else
				spaces = 0;

			builder.append(posChar);
		}
		if (values == null)
			return new String[] { builder.toString() };

		String commentValue = builder.toString();
		if (commentValue.charAt(0) == ' ')
			commentValue = commentValue.substring(1);
		values[1] = commentValue;
		return values;
	}
}
