package me.devtec.shared.dataholder.loaders.toml;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.DataLoader;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.dataholder.loaders.yaml.YamlSectionBuilderHelper;
import me.devtec.shared.dataholder.loaders.yaml.YamlSectionBuilderHelper.StringArrayList;
import me.devtec.shared.json.Json;

public class TomlSectionBuilderHelper {

	public static class Section {
		public final String keyName;
		public DataValue value;
		public Section[] sub;
		public Section parent;
		public boolean isKey;

		public Section(Section parent, String key, DataValue val) {
			this.parent = parent;
			isKey = val == null;
			keyName = key;
			value = val;
		}

		public Section(String key, DataValue val) {
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

		public StringContainer fullName(StringContainer container, boolean correct) {
			container.clear();
			for (Section parent = this; parent != null; parent = parent.parent)
				container.insert(0, '.').insert(0, parent.keyName);
			if (correct && container.charAt(container.length() - 1) == '.')
				container.deleteCharAt(container.length() - 1);
			return container;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Section) {
				Section sec = (Section) obj;
                return sec.keyName.equals(keyName);
			}
			return false;
		}

	}

	public static Iterator<CharSequence> prepareBuilder(Set<String> primaryKeys, DataLoader dataLoader, boolean markSaved) {
		StringContainer container = new StringContainer(64);
		Map<String, Section> map = new LinkedHashMap<>();
		Map<String, Section> keysWithoutSub = new LinkedHashMap<>();
		for (String primaryKey : primaryKeys)
			keysWithoutSub.put(primaryKey, new Section(primaryKey, dataLoader.get(primaryKey)));

		Section prevParent = null;
		StringContainer fullNameContainer = new StringContainer(64);
		for (Entry<String, DataValue> entry : dataLoader.entrySet()) {
			container.clear();
			container.append(entry.getKey());
			Section main;
			if (prevParent != null && (container.startsWith(prevParent.fullName(fullNameContainer, false), 0)
					|| (prevParent = prevParent.parent) != null && container.startsWith(prevParent.fullName(fullNameContainer, false), 0))) {
				container.delete(0, prevParent.fullName(fullNameContainer, false).length());
				main = prevParent;
			} else {
				int pos = container.indexOf('.');
				if (pos == -1)
					continue;
				String primaryKey = container.substring(0, pos);
				container.delete(0, primaryKey.length() + 1);
				main = map.get(primaryKey);
				if (main == null)
					map.put(primaryKey, main = keysWithoutSub.remove(primaryKey));
			}
			Section sec = main.create(container.toString());
			sec.value = entry.getValue();
			prevParent = sec.parent;
		}
		container.clear();
		StringArrayList values = new StringArrayList();
		Iterator<Section> itr = keysWithoutSub.values().iterator();
		Iterator<Section> itrSecond = map.values().iterator();
		return new Iterator<CharSequence>() {
			byte modeStep = 0;

			Iterator<CharSequence> currentItr;

			@Override
			public boolean hasNext() {
				switch (modeStep) {
				case 0:
					return currentItr != null && currentItr.hasNext() || itr.hasNext() || !values.isEmpty();
				case 1:
					return currentItr != null && currentItr.hasNext() || itrSecond.hasNext() || !values.isEmpty();
				}
				return false;
			}

			@Override
			public CharSequence next() {
				switch (modeStep) {
				case 0: {
					if (!itr.hasNext()) {
						String result = values.complete();
						values.clear();
						modeStep = 1;
						return result;
					}
					Section section = itr.next();
					currentItr = startIterator(container, values, section.value, section, markSaved);
					CharSequence next = currentItr.next();
					if (next == null) {
						if (currentItr != null && currentItr.hasNext() || itr.hasNext())
							return next();
						String result = values.complete();
						values.clear();
						modeStep = 1;
						return result;
					}
					return next;
				}
				case 1:
					if (!itrSecond.hasNext()) {
						String result = values.complete();
						values.clear();
						return result;
					}
					Section section = itrSecond.next();
					currentItr = startIterator(container, values, section.value, section, markSaved);
					CharSequence next = currentItr.next();
					if (next == null) {
						if (currentItr != null && currentItr.hasNext() || itrSecond.hasNext())
							return next();
						String result = values.complete();
						values.clear();
						return result;
					}
					return next;
				}
				return null;
			}
		};
	}

	public static Iterator<CharSequence> startIterator(StringContainer container, StringArrayList values, DataValue dataVal, Section linked, boolean markSaved) {
		if (dataVal == null)
			return getCharSequenceIterator(container, values, linked, markSaved);
		if (markSaved)
			dataVal.modified = false;

		if (dataVal.value == null)
			return getCharSequenceIterator(container, values, linked, markSaved);
		String commentAfterValue = dataVal.commentAfterValue;
		Collection<String> comments = dataVal.comments;
		Iterator<String> commentsItr = comments != null && !comments.isEmpty() ? comments.iterator() : null;
		Object value = dataVal.value;
		if (value != null)
			if (dataVal.writtenValue != null)
				return new Iterator<CharSequence>() {
					byte type = commentsItr != null ? (byte) 0 : (byte) 1;
					final Iterator<CharSequence> currentItr = linked.sub == null ? null : startMultipleIterate(container, values, linked.sub, markSaved);

					@Override
					public boolean hasNext() {
						switch (type) {
						case 0:
						case 2:
							return commentsItr!=null && commentsItr.hasNext();
						case 1:
							return true;
						}
						return false;
					}

					@Override
					public CharSequence next() {
						switch (type) {
						case 0:
							while (commentsItr!=null && commentsItr.hasNext()) {
								String comment = commentsItr.next();
								container.clear();
								String result = values.add(container.append(comment).append(System.lineSeparator()));
								if (result != null)
									return result;
							}
							type = 1;
							return hasNext() ? next() : null;
						case 1: {
							type = 2;
							String result = values.add(appendName(container, linked, linked.isKey, dataVal.writtenValue, value instanceof String ? '"' : (char) 0, commentAfterValue));
							if (result != null)
								return result;
							return hasNext() ? next() : null;
						}
						case 2:
							if (currentItr != null && currentItr.hasNext()) {
								CharSequence result = currentItr.next();
								if (result != null)
									return result;
								return hasNext() ? next() : null;
							}
						}
						return null;
					}

				};
			else if (value instanceof CharSequence)
				return new Iterator<CharSequence>() {
					byte type = commentsItr != null ? (byte) 0 : (byte) 1;
					final Iterator<CharSequence> currentItr = linked.sub == null ? null : startMultipleIterate(container, values, linked.sub, markSaved);

					@Override
					public boolean hasNext() {
						switch (type) {
						case 0:
						case 2:
							return commentsItr!=null && commentsItr.hasNext();
						case 1:
							return true;
						}
						return false;
					}

					@Override
					public CharSequence next() {
						switch (type) {
						case 0:
							while (commentsItr!=null && commentsItr.hasNext()) {
								String comment = commentsItr.next();
								container.clear();
								String result = values.add(container.append(comment).append(System.lineSeparator()));
								if (result != null)
									return result;
							}
							type = 1;
							return hasNext() ? next() : null;
						case 1: {
							type = 2;
							String result = values.add(appendName(container, linked, linked.isKey, value instanceof String ? (String) value : value.toString(), '"', commentAfterValue));
							if (result != null)
								return result;
							return hasNext() ? next() : null;
						}
						case 2:
							if (currentItr != null && currentItr.hasNext()) {
								CharSequence result = currentItr.next();
								if (result != null)
									return result;
								return hasNext() ? next() : null;
							}
						}
						return null;
					}

				};
			else
				return new Iterator<CharSequence>() {
					byte type = commentsItr != null ? (byte) 0 : (byte) 1;
					final Iterator<CharSequence> currentItr = linked.sub == null ? null : startMultipleIterate(container, values, linked.sub, markSaved);

					@Override
					public boolean hasNext() {
						switch (type) {
						case 0:
						case 2:
							return commentsItr!=null && commentsItr.hasNext();
						case 1:
							return true;
						}
						return false;
					}

					@Override
					public CharSequence next() {
						switch (type) {
						case 0:
							while (commentsItr!=null && commentsItr.hasNext()) {
								String comment = commentsItr.next();
								container.clear();
								String result = values.add(container.append(comment).append(System.lineSeparator()));
								if (result != null)
									return result;
							}
							type = 1;
							return hasNext() ? next() : null;
						case 1: {
							type = 2;
							String result = values.add(appendName(container, linked, linked.isKey, Json.writer().write(value), (char) 0, commentAfterValue));
							if (result != null)
								return result;
							return hasNext() ? next() : null;
						}
						case 2:
							if (currentItr != null && currentItr.hasNext()) {
								CharSequence result = currentItr.next();
								if (result != null)
									return result;
								return hasNext() ? next() : null;
							}
						}
						return null;
					}

				};
		return getCharSequenceIterator(container, values, linked, markSaved);
	}

	private static Iterator<CharSequence> getCharSequenceIterator(StringContainer container, StringArrayList values, Section linked, boolean markSaved) {
		return new Iterator<CharSequence>() {
			final Iterator<CharSequence> currentItr = linked.sub != null ? startMultipleIterate(container, values, linked.sub, markSaved) : null;

			@Override
			public boolean hasNext() {
				return currentItr != null && currentItr.hasNext();
			}

			@Override
			public CharSequence next() {
				if (currentItr.hasNext()) {
					CharSequence result = currentItr.next();
					if (result != null)
						return result;
					return currentItr.hasNext() ? next() : null;
				}
				return null;
			}

		};
	}

	public static Iterator<CharSequence> startMultipleIterate(StringContainer container, StringArrayList values, Section[] keys, boolean markSaved) {
		return new Iterator<CharSequence>() {
			int pos = 0;
			byte mode = 0;
			Section section;
			boolean ignoreIsKey = false;
			Iterator<String> commentsItr;
			Iterator<CharSequence> currentItr;
			boolean foundAnySub;

			@Override
			public boolean hasNext() {
				nextSection();
				switch (mode) {
				case 0:
					return commentsItr != null && commentsItr.hasNext() && section.value != null && section.value.value != null;
				case 1:
					return section.value != null && section.value.value != null;
				case 2:
					return pos <= keys.length || foundAnySub;
				case 3:
					while (currentItr == null && pos < keys.length) {
						Section sub = keys[pos++];
						if (sub.sub != null) {
							currentItr = startMultipleIterate(container, values, sub.sub, markSaved);
							if (currentItr.hasNext())
								break;
						}
					}
					return currentItr != null && currentItr.hasNext() || pos < keys.length;
				}
				return false;
			}

			private void nextSection() {
				if (section == null) {
					section = keys[pos++];
					commentsItr = section.value != null && section.value.comments != null ? section.value.comments.iterator() : null;
					mode = commentsItr == null ? (byte) 1 : 0;
					if (!hasNext())
						mode = 2;
					foundAnySub |= section.sub != null;
				}
			}

			@Override
			public CharSequence next() {
				nextSection();
				switch (mode) {
				case 0: {
					CharSequence result = currentItr.next();
					if (!commentsItr.hasNext())
						if (hasNext())
							mode = 1;
						else
							mode = 2;
					if (result != null)
						return result;
					return hasNext() ? next() : null;
				}
				case 1: {
					String result;
					if (section.value.writtenValue != null)
						result = values.add(appendName(container, section, !ignoreIsKey && section.isKey, section.value.writtenValue, section.value.value instanceof String ? '"' : (char) 0,
								section.value.commentAfterValue));
					else if (section.value.value instanceof CharSequence)
						result = values.add(appendName(container, section, !ignoreIsKey && section.isKey,
								section.value.value instanceof String ? (String) section.value.value : section.value.value.toString(), '"', section.value.commentAfterValue));
					else
						result = values.add(appendName(container, section, !ignoreIsKey && section.isKey, Json.writer().write(section.value.value), (char) 0, section.value.commentAfterValue));
					if(markSaved)
						section.value.modified=false;
					ignoreIsKey = true;
					mode = 2;
					if (result != null)
						return result;
					return hasNext() ? next() : null;
				}
				case 2: {
					if (keys.length <= pos) {
						mode = 3;
						pos = 0;
						return hasNext() ? next() : null;
					}
					section = keys[pos++];
					commentsItr = section.value != null && section.value.comments != null ? section.value.comments.iterator() : null;
					mode = commentsItr == null ? (byte) 1 : 0;
					foundAnySub |= section.sub != null;
					return hasNext() ? next() : null;
				}
				case 3:
					if (currentItr != null && currentItr.hasNext())
						return currentItr.next();
					while (currentItr == null && pos < keys.length) {
						Section sub = keys[pos++];
						if (sub.sub != null) {
							currentItr = startMultipleIterate(container, values, sub.sub, markSaved);
							if (currentItr.hasNext())
								return next();
						}
					}
					return next();
				}
				return null;
			}
		};

	}

	private static StringContainer appendName(StringContainer container, Section section, boolean asKey, String value, char queto, String comment) {
		container.clear();
		if (asKey) {
			container.append('[');
			StringContainer fullName = section.parent.fullName(new StringContainer(), true);
			if (fullName.indexOf(':') != -1 || fullName.indexOf('=') != -1 || fullName.indexOf('"') != -1)
				container.append('\'').append(YamlSectionBuilderHelper.replaceWithEscape(fullName.toString(), '"')).append('\'');
			else
				container.append(section.parent.fullName(new StringContainer(), true));
			container.append(']').append(System.lineSeparator());
		}
		if (section.keyName.charAt(0) == '#' || section.keyName.indexOf(':') != -1)
			container.append('\'').append(section.keyName).append('\'');
		else
			container.append(section.keyName);
		container.append('=');
		if (queto == 0)
			container.append(value);
		else
			container.append(queto).append(YamlSectionBuilderHelper.replaceWithEscape(value, queto)).append(queto);
		if (comment != null && !comment.trim().isEmpty())
			if (comment.charAt(0) == ' ')
				container.append(comment);
			else
				container.append(' ').append(comment);
		return container.append(System.lineSeparator());
	}
}
