package me.devtec.shared.dataholder.loaders.toml;

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

public class TomlSectionBuilderHelper {

	public static class Section {
		public String space;
		public String keyName;
		public DataValue value;
		public Map<String, Section> sub;
		public Section parent;
		public String fullName;
		public boolean isKey;

		public Section(Section parent, String key, DataValue val) {
			fullName = parent.fullName + '.' + key;
			this.parent = parent;
			space = !parent.isKey ? "" : parent.space + "  ";
			isKey = val == null;
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
			TomlSectionBuilderHelper.start(dataLoader, primaryKey, sec.value, builder, sec, markSaved);
		}
	}

	public static void start(DataLoader config, String section, DataValue dataVal, StringContainer b, Section linked, boolean markSaved) {
		try {
			if (dataVal == null) {
				if (linked.sub != null)
					writeWithKeys(config, linked.sub.values(), b, markSaved);
				return;
			}
			if (markSaved)
				dataVal.modified = false;

			if (dataVal.value == null) {
				if (linked.sub != null)
					writeWithKeys(config, linked.sub.values(), b, markSaved);
				return;

			}

			String commentAfterValue = dataVal.commentAfterValue;
			Collection<String> comments = dataVal.comments;
			Object value = dataVal.value;
			// write list values
			if (comments != null)
				for (String s : comments)
					b.append(linked.space).append(s).append(System.lineSeparator());

			appendName(b, linked.space, linked, linked.isKey);

			if (value == null)
				TomlSectionBuilderHelper.addCommentIfAvailable(b, commentAfterValue).append(System.lineSeparator());
			else // write normal value
				try {
					if (dataVal.writtenValue != null)
						TomlSectionBuilderHelper.addCommentIfAvailable(TomlSectionBuilderHelper.writeQuotes(b, dataVal.writtenValue, value instanceof String ? '"' : 0), commentAfterValue)
								.append(System.lineSeparator());
					else if (value instanceof String)
						TomlSectionBuilderHelper.addCommentIfAvailable(TomlSectionBuilderHelper.writeQuotes(b, (String) value, '"'), commentAfterValue).append(System.lineSeparator());
					else
						TomlSectionBuilderHelper.addCommentIfAvailable(TomlSectionBuilderHelper.writeWithoutQuotes(b, value), commentAfterValue).append(System.lineSeparator());
				} catch (Exception er) {
					if (value instanceof String)
						TomlSectionBuilderHelper.addCommentIfAvailable(TomlSectionBuilderHelper.writeQuotes(b, (String) value, '"'), commentAfterValue).append(System.lineSeparator());
					else
						TomlSectionBuilderHelper.addCommentIfAvailable(TomlSectionBuilderHelper.writeWithoutQuotes(b, value), commentAfterValue).append(System.lineSeparator());
				}
		} catch (Exception err) {
			err.printStackTrace();
		}
		if (linked.sub != null)
			writeWithKeys(config, linked.sub.values(), b, markSaved);
	}

	public static void writeWithKeys(DataLoader config, Collection<Section> keys, StringContainer b, boolean markSaved) {
		try {

			boolean ignoreIsKey = false;
			for (Section linked : keys) {
				DataValue dataVal = linked.value;
				if (dataVal == null) {
					if (linked.sub != null)
						writeWithKeys(config, linked.sub.values(), b, markSaved);
					continue;
				}
				if (markSaved)
					dataVal.modified = false;

				if (dataVal.value == null) {
					if (linked.sub != null)
						writeWithKeys(config, linked.sub.values(), b, markSaved);
					return;

				}

				String commentAfterValue = dataVal.commentAfterValue;
				Collection<String> comments = dataVal.comments;
				Object value = dataVal.value;
				// write list values
				if (comments != null)
					for (String s : comments)
						b.append(linked.space).append(s).append(System.lineSeparator());

				appendName(b, linked.space, linked, !ignoreIsKey ? linked.isKey : false);
				ignoreIsKey = true;

				if (value == null)
					TomlSectionBuilderHelper.addCommentIfAvailable(b, commentAfterValue).append(System.lineSeparator());
				else // write normal value
					try {
						if (dataVal.writtenValue != null)
							TomlSectionBuilderHelper.addCommentIfAvailable(TomlSectionBuilderHelper.writeQuotes(b, dataVal.writtenValue, value instanceof String ? '"' : 0), commentAfterValue)
									.append(System.lineSeparator());
						else if (value instanceof String)
							TomlSectionBuilderHelper.addCommentIfAvailable(TomlSectionBuilderHelper.writeQuotes(b, (String) value, '"'), commentAfterValue).append(System.lineSeparator());
						else
							TomlSectionBuilderHelper.addCommentIfAvailable(TomlSectionBuilderHelper.writeWithoutQuotes(b, value), commentAfterValue).append(System.lineSeparator());
					} catch (Exception er) {
						if (value instanceof String)
							TomlSectionBuilderHelper.addCommentIfAvailable(TomlSectionBuilderHelper.writeQuotes(b, (String) value, '"'), commentAfterValue).append(System.lineSeparator());
						else
							TomlSectionBuilderHelper.addCommentIfAvailable(TomlSectionBuilderHelper.writeWithoutQuotes(b, value), commentAfterValue).append(System.lineSeparator());
					}
			}
		} catch (Exception err) {
			err.printStackTrace();
		}
		for (Section linked : keys)
			if (linked.sub != null)
				writeWithKeys(config, linked.sub.values(), b, markSaved);
	}

	private static StringContainer appendName(StringContainer sectionLine, String space, Section section, boolean asKey) {
		if (asKey) {
			sectionLine.append(space).append('[');
			if (section.parent.fullName.indexOf(':') != -1)
				sectionLine.append('\'').append(section.parent.fullName).append('\'');
			else
				sectionLine.append(section.parent.fullName);
			sectionLine.append(']').append(System.lineSeparator());
		}
		if (section.keyName.charAt(0) == '#' || section.keyName.indexOf(':') != -1)
			sectionLine.append('\'').append(section.keyName).append('\'');
		else
			sectionLine.append(space).append(section.keyName);
		return sectionLine.append('=');
	}

	private static StringContainer addCommentIfAvailable(StringContainer append, String commentAfterValue) {
		if (commentAfterValue == null)
			return append;
		return append.append(' ').append(commentAfterValue);
	}

	protected static StringContainer writeQuotes(StringContainer b, String value, char add) {
		if (add == 0)
			b.append(value);
		else {
			b.append(add);
			replaceWithEscape(b, value, add);
			b.append(add);
		}
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
