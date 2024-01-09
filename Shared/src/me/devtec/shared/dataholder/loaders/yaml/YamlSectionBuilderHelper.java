package me.devtec.shared.dataholder.loaders.yaml;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.DataLoader;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class YamlSectionBuilderHelper {

	public static class Section {
		public String space;
		public String keyName;
		public DataValue value;
		public Map<String, Section> sub;
		public Section parent;
		public String fullName;

		public Section(Section parent, String key, DataValue val) {
			fullName = parent.fullName + '.' + key;
			this.parent = parent;
			space = parent.space + "  ";
			keyName = key;
			value = val;
		}

		public Section(String key, DataValue val) {
			parent = this;
			fullName = key;
			space = "";
			keyName = key;
			value = val;
		}

		public void add(Section sec) {
			if (sub == null)
				sub = new LinkedHashMap<>();
			sub.put(sec.keyName, sec);
		}

		public Section create(String name) {
			Section current = this;
			StringContainer sb = new StringContainer(name);

			int pos;
			int lastPos = 0;
			while ((pos = sb.indexOf('.', lastPos)) != -1) {
				String key = sb.substring(lastPos, pos);
				Map<String, Section> subSections = current.sub;
				if (subSections != null) {
					Section sec = subSections.get(key);
					if (sec == null) {
						Section newMade = new Section(current, key, null);
						current.add(newMade);
						current = newMade;
					} else
						current = sec;
				} else {
					Section newMade = new Section(current, key, null);
					current.add(newMade);
					current = newMade;
				}
				lastPos = pos + 1;
			}
			// Final
			String key = sb.substring(lastPos, sb.length());
			Map<String, Section> subSections = current.sub;
			if (subSections == null) {
				Section newMade = new Section(current, key, null);
				current.add(newMade);
				return newMade;
			}
			Section sec = subSections.get(key);
			if (sec == null) {
				Section newMade = new Section(current, key, null);
				current.add(newMade);
				return newMade;
			}
			return sec;
		}

	}

	public static void write(StringContainer builder, Set<String> primaryKeys, DataLoader dataLoader, boolean markSaved) {
		Map<String, Section> map = new HashMap<>();
		for (String primaryKey : primaryKeys)
			map.put(primaryKey, new Section(primaryKey, dataLoader.get(primaryKey)));

		Section prevParent = null;
		for (Entry<String, DataValue> entry : dataLoader.entrySet()) {
			String fullName = entry.getKey();
			Section main = null;
			if (prevParent != null && (fullName.startsWith(prevParent.fullName + '.') || fullName.startsWith((prevParent = prevParent.parent).fullName + '.'))) {
				fullName = fullName.substring(prevParent.fullName.length() + 1);
				main = prevParent;
			} else {
				int pos = fullName.indexOf('.');
				if (pos == -1)
					continue;
				String primaryKey = pos == -1 ? fullName : fullName.substring(0, pos);
				fullName = fullName.substring(primaryKey.length() + 1);
				main = prevParent = map.get(primaryKey);
			}
			Section sec = main.create(fullName);
			sec.value = entry.getValue();
			prevParent = sec.parent;
		}
		for (String primaryKey : primaryKeys) {
			Section sec = map.get(primaryKey);
			YamlSectionBuilderHelper.start(dataLoader, primaryKey, sec.value, builder, sec, markSaved);
		}
	}

	public static void start(DataLoader config, String section, DataValue dataVal, StringContainer b, Section linked, boolean markSaved) {
		try {
			if (dataVal == null) {
				appendName(b, linked.space, section).append(System.lineSeparator());
				if (linked.sub != null)
					for (Section sub : linked.sub.values())
						start(config, sub.keyName, sub.value, b, sub, markSaved);
				return;
			}
			if (markSaved)
				dataVal.modified = false;
			String commentAfterValue = dataVal.commentAfterValue;
			Collection<String> comments = dataVal.comments;
			Object value = dataVal.value;
			// write list values
			if (comments != null)
				for (String s : comments)
					b.append(linked.space).append(s).append(System.lineSeparator());

			appendName(b, linked.space, section);

			if (value == null)
				YamlSectionBuilderHelper.addCommentIfAvailable(b, commentAfterValue).append(System.lineSeparator());
			// write collection or array
			else if (value instanceof Collection || value instanceof Object[]) {
				String splitted = linked.space + "- ";
				if (value instanceof Collection) {
					if (!((Collection<?>) value).isEmpty())
						try {
							if (dataVal.writtenValue != null)
								YamlSectionBuilderHelper.addCommentIfAvailable(b.append(' ').append(dataVal.writtenValue), commentAfterValue).append(System.lineSeparator());
							else {
								YamlSectionBuilderHelper.addCommentIfAvailable(b, commentAfterValue).append(System.lineSeparator());
								for (Object a : (Collection<?>) value)
									YamlSectionBuilderHelper.writeWithoutQuotesSplit(b, splitted, a);
							}
						} catch (Exception er) {
							b.append(System.lineSeparator());
							for (Object a : (Collection<?>) value)
								YamlSectionBuilderHelper.writeWithoutQuotesSplit(b, splitted, a);
						}
					else
						YamlSectionBuilderHelper.addCommentIfAvailable(b.append(' ').append('[').append(']'), commentAfterValue).append(System.lineSeparator());
				} else if (((Object[]) value).length != 0)
					try {
						if (dataVal.writtenValue != null)
							YamlSectionBuilderHelper.addCommentIfAvailable(b.append(' ').append(dataVal.writtenValue), commentAfterValue).append(System.lineSeparator());
						else {
							YamlSectionBuilderHelper.addCommentIfAvailable(b, commentAfterValue).append(System.lineSeparator());
							for (Object a : (Object[]) value)
								YamlSectionBuilderHelper.writeWithoutQuotesSplit(b, splitted, a);
						}
					} catch (Exception er) {
						b.append(System.lineSeparator());
						for (Object a : (Object[]) value)
							YamlSectionBuilderHelper.writeWithoutQuotesSplit(b, splitted, a);
					}
				else
					YamlSectionBuilderHelper.addCommentIfAvailable(b.append(' ').append('[').append(']'), commentAfterValue).append(System.lineSeparator());
			} else // write normal value
				try {
					if (dataVal.writtenValue != null)
						YamlSectionBuilderHelper.addCommentIfAvailable(b.append(' ').append(dataVal.writtenValue), commentAfterValue).append(System.lineSeparator());
					else if (value instanceof String)
						YamlSectionBuilderHelper.addCommentIfAvailable(YamlSectionBuilderHelper.writeQuotes(b.append(' '), (String) value, '"'), commentAfterValue).append(System.lineSeparator());
					else
						YamlSectionBuilderHelper.addCommentIfAvailable(YamlSectionBuilderHelper.writeWithoutQuotes(b.append(' '), value), commentAfterValue).append(System.lineSeparator());
				} catch (Exception er) {
					if (value instanceof String)
						YamlSectionBuilderHelper.addCommentIfAvailable(YamlSectionBuilderHelper.writeQuotes(b.append(' '), (String) value, '"'), commentAfterValue).append(System.lineSeparator());
					else
						YamlSectionBuilderHelper.addCommentIfAvailable(YamlSectionBuilderHelper.writeWithoutQuotes(b.append(' '), value), commentAfterValue).append(System.lineSeparator());
				}
		} catch (Exception err) {
			err.printStackTrace();
		}
		if (linked.sub != null)
			for (Section sub : linked.sub.values())
				start(config, sub.keyName, sub.value, b, sub, markSaved);
	}

	private static StringContainer appendName(StringContainer sectionLine, String space, String section) {
		// write spaces
		sectionLine.append(space);

		// write section name
		if (section.charAt(0) == '#')
			sectionLine.append('\'').append(section).append('\'').append(':');
		else
			sectionLine.append(section).append(':');
		return sectionLine;
	}

	private static StringContainer addCommentIfAvailable(StringContainer append, String commentAfterValue) {
		if (commentAfterValue == null)
			return append;
		return append.append(' ').append(commentAfterValue);
	}

	protected static StringContainer writeWithoutQuotesSplit(StringContainer b, String split, Object value) {
		b.append(split);
		b.append(Json.writer().write(value));
		b.append(System.lineSeparator());
		return b;
	}

	protected static StringContainer writeQuotes(StringContainer b, String value, char add) {
		b.append(add);
		replaceWithEscape(b, value, add);
		b.append(add);
		return b;
	}

	protected static StringContainer replaceWithEscape(StringContainer b, String value, char add) {
		for (int i = 0; i < value.length(); ++i) {
			char c = value.charAt(i);
			if (c == add)
				b.append('\\');
			b.append(c);
		}
		return b;
	}

	protected static StringContainer writeWithoutQuotes(StringContainer b, Object value) {
		b.append(Json.writer().write(value));
		return b;
	}
}
