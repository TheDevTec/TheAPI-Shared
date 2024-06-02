package me.devtec.shared.dataholder.loaders.toml;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.DataLoader;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.loaders.yaml.YamlSectionBuilderHelper.StringArrayList;
import me.devtec.shared.json.Json;

public class TomlSectionBuilderHelper {

	public static class Section {
		public String space;
		public String keyName;
		public DataValue value;
		public Section[] sub;
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
				sub = new Section[] { sec };
			else {
				Section[] copy = new Section[sub.length + 1];
				System.arraycopy(sub, 0, copy, 0, sub.length);
				copy[sub.length] = sec;
				sub = copy;
			}
		}

		public Section get(String name) {
			for (Section section : sub)
				if (section.keyName.equals(name))
					return section;
			return null;
		}

		public Section create(String name) {
			Section current = this;

			int pos;
			int lastPos = 0;
			while ((pos = name.indexOf('.', lastPos)) != -1) {
				String key = name.substring(lastPos, pos);
				if (current.sub != null) {
					Section sec = current.get(key);
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
			String key = name.substring(lastPos);
			if (current.sub == null) {
				Section newMade = new Section(current, key, null);
				current.add(newMade);
				return newMade;
			}
			Section sec = current.get(key);
			if (sec == null) {
				Section newMade = new Section(current, key, null);
				current.add(newMade);
				return newMade;
			}
			return sec;
		}

	}

	public static List<String> prepareBuilder(Set<String> primaryKeys, DataLoader dataLoader, boolean markSaved) {
		Map<String, Section> map = new WeakHashMap<>();
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
		StringArrayList values = new StringArrayList();
		StringContainer container = new StringContainer(128);
		for (Section section : map.values())
			startIterate(container, values, section.keyName, section.value, section, markSaved);
		values.complete();
		return values;
	}

	public static void startIterate(StringContainer container, List<String> values, String section, DataValue dataVal, Section linked, boolean markSaved) {
		if (dataVal == null) {
			if (linked.sub != null)
				startMultipleIterate(container, values, linked.sub, markSaved);
			return;
		}
		if (markSaved)
			dataVal.modified = false;

		if (dataVal.value == null) {
			if (linked.sub != null)
				startMultipleIterate(container, values, linked.sub, markSaved);
			return;

		}
		String commentAfterValue = dataVal.commentAfterValue;
		Collection<String> comments = dataVal.comments;
		Object value = dataVal.value;
		// write list values
		if (comments != null)
			for (String comment : comments) {
				container.clear();
				values.add(container.append(linked.space).append(comment).append(System.lineSeparator()).toString());
			}

		if (value != null)
			if (dataVal.writtenValue != null)
				values.add(appendName(container, linked.space, linked, linked.isKey, dataVal.writtenValue, value instanceof String ? '"' : (char) 0, commentAfterValue));
			else if (value instanceof CharSequence)
				values.add(appendName(container, linked.space, linked, linked.isKey, value instanceof String ? (String) value : value.toString(), '"', commentAfterValue));
			else
				values.add(appendName(container, linked.space, linked, linked.isKey, Json.writer().write(value), (char) 0, commentAfterValue));
		if (linked.sub != null)
			for (Section sub : linked.sub)
				startIterate(container, values, sub.keyName, sub.value, sub, markSaved);
	}

	public static void startMultipleIterate(StringContainer container, List<String> values, Section[] keys, boolean markSaved) {
		boolean ignoreIsKey = false;
		for (Section linked : keys) {
			DataValue dataVal = linked.value;
			if (dataVal == null) {
				if (linked.sub != null)
					startMultipleIterate(container, values, linked.sub, markSaved);
				continue;
			}
			if (markSaved)
				dataVal.modified = false;

			if (dataVal.value == null) {
				if (linked.sub != null)
					startMultipleIterate(container, values, linked.sub, markSaved);
				return;

			}

			String commentAfterValue = dataVal.commentAfterValue;
			Collection<String> comments = dataVal.comments;
			Object value = dataVal.value;
			// write list values
			if (comments != null)
				for (String comment : comments) {
					container.clear();
					values.add(container.append(linked.space).append(comment).append(System.lineSeparator()).toString());
				}

			if (value != null)
				if (dataVal.writtenValue != null)
					values.add(appendName(container, linked.space, linked, !ignoreIsKey ? linked.isKey : false, dataVal.writtenValue, value instanceof String ? '"' : (char) 0, commentAfterValue));
				else if (value instanceof CharSequence)
					values.add(appendName(container, linked.space, linked, !ignoreIsKey ? linked.isKey : false, value instanceof String ? (String) value : value.toString(), '"', commentAfterValue));
				else
					values.add(appendName(container, linked.space, linked, !ignoreIsKey ? linked.isKey : false, Json.writer().write(value), (char) 0, commentAfterValue));
			ignoreIsKey = true;
		}
		for (Section linked : keys)
			if (linked.sub != null)
				startMultipleIterate(container, values, linked.sub, markSaved);
	}

	private static String appendName(StringContainer container, String space, Section section, boolean asKey, String value, char queto, String comment) {
		container.clear();
		if (asKey) {
			container.append(space).append('[');
			if (section.parent.fullName.indexOf(':') != -1)
				container.append('\'').append(section.parent.fullName).append('\'');
			else
				container.append(section.parent.fullName);
			container.append(']').append(System.lineSeparator());
		}
		if (section.keyName.charAt(0) == '#' || section.keyName.indexOf(':') != -1)
			container.append('\'').append(section.keyName).append('\'');
		else
			container.append(space).append(section.keyName);
		container.append('=');
		if (queto == 0)
			container.append(value);
		else
			container.append(queto).append(replaceWithEscape(value, queto)).append(queto);
		if (comment != null && !comment.trim().isEmpty())
			if (comment.charAt(0) == ' ')
				container.append(comment);
			else
				container.append(' ').append(comment);
		return container.append(System.lineSeparator()).toString();
	}

	private static String replaceWithEscape(String value, char add) {
		int startAt = value.indexOf(add);
		if (startAt == -1)
			return value;
		StringContainer container = new StringContainer(value);
		for (int i = startAt; i < container.length(); ++i) {
			char c = container.charAt(i);
			if (c == add)
				container.insert(i++, '\\');
		}
		return container.toString();
	}
}
