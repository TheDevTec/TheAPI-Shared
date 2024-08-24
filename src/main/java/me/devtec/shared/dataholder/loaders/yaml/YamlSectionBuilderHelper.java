package me.devtec.shared.dataholder.loaders.yaml;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.DataLoader;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class YamlSectionBuilderHelper {

	public static class StringArrayList {

		private static final int BUFFER_SIZE = 1024 * 4;

		private final StringContainer container = new StringContainer(BUFFER_SIZE);

		public String add(CharSequence e) {
			if (e == null)
				return null;
			container.append(e);
			if (container.length() >= BUFFER_SIZE) {
				String result = container.toString();
				container.clear();
				return result;
			}
			return null;
		}

		public String complete() {
			if (!container.isEmpty())
				return container.toString();
			return null;
		}

		public boolean isEmpty() {
			return container.isEmpty();
		}

		public void clear() {
			container.clear();
		}
	}

	public static class Section {
		public final int space;
		public final String keyName;
		public DataValue value;
		public Section[] sub;
		public Section parent;

		public Section(Section parent, String key, DataValue val) {
			this.parent = parent;
			space = parent.space + 1;
			keyName = key;
			value = val;
		}

		public Section(String key, DataValue val) {
			space = 0;
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

		public StringContainer fullName(StringContainer container) {
			container.clear();
			for (Section parent = this; parent != null; parent = parent.parent)
				container.insert(0, '.').insert(0, parent.keyName);
			return container;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Section) {
				Section sec = (Section) obj;
                return sec.keyName.equals(keyName) && sec.space == space;
			}
			return false;
		}

	}

	public static Iterator<CharSequence> prepareBuilder(Set<String> primaryKeys, DataLoader dataLoader, boolean markSaved) {
		StringContainer container = new StringContainer(64);
		Map<String, Section> map = new LinkedHashMap<>();
		for (String primaryKey : primaryKeys)
			map.put(primaryKey, new Section(primaryKey, dataLoader.get(primaryKey)));

		Section prevParent = null;
		StringContainer fullNameContainer = new StringContainer(64);
		for (Entry<String, DataValue> entry : dataLoader.entrySet()) {
			container.clear();
			container.append(entry.getKey());
			Section main;
			if (prevParent != null && (container.startsWith(prevParent.fullName(fullNameContainer), 0)
					|| (prevParent = prevParent.parent) != null && container.startsWith(prevParent.fullName(fullNameContainer), 0))) {
				container.delete(0, prevParent.fullName(fullNameContainer).length());
				main = prevParent;
			} else {
				int pos = container.indexOf('.');
				if (pos == -1)
					continue;
				String primaryKey = container.substring(0, pos);
				container.delete(0, primaryKey.length() + 1);
				main = map.get(primaryKey);
			}
			Section sec = main.create(container.toString());
			sec.value = entry.getValue();
			prevParent = sec.parent;
		}
		container.clear();
		StringArrayList values = new StringArrayList();
		Iterator<Section> itr = map.values().iterator();
		return new Iterator<CharSequence>() {
			Iterator<CharSequence> currentItr;

			@Override
			public boolean hasNext() {
				return currentItr != null && currentItr.hasNext() || itr.hasNext() || !values.isEmpty();
			}

			@Override
			public CharSequence next() {
				if (currentItr != null && currentItr.hasNext()) {
					CharSequence next = currentItr.next();
					if (next == null) {
						if (hasNext())
							return next();
						String result = values.complete();
						values.clear();
						return result;
					}
					return next;
				}
				if (!itr.hasNext()) {
					String result = values.complete();
					values.clear();
					return result;
				}
				Section section = itr.next();
				currentItr = startIterator(container, values, section.keyName, section.value, section, markSaved);
				CharSequence next = currentItr.next();
				if (next == null) {
					if (hasNext())
						return next();
					String result = values.complete();
					values.clear();
					return result;
				}
				return next;
			}
		};
	}

	public static Iterator<CharSequence> startIterator(StringContainer container, StringArrayList values, String section, DataValue dataVal, Section linked, boolean markSaved) {
		if (dataVal == null)
			return new Iterator<CharSequence>() {
				int pos = -1;
				Iterator<CharSequence> currentItr;

				@Override
				public boolean hasNext() {
					return currentItr != null && currentItr.hasNext() || pos == -1 || linked.sub != null && linked.sub.length > pos;
				}

				@Override
				public CharSequence next() {
					if (pos == -1) {
						pos = 0;
						String result = values.add(appendName(container, linked.space, section));
						if (result != null)
							return result;
						return hasNext() ? next() : null;
					}
					if (currentItr != null && currentItr.hasNext()) {
						CharSequence result = currentItr.next();
						if (result != null)
							return result;
						return hasNext() ? next() : null;
					}
					Section sub = linked.sub[pos++];
					currentItr = startIterator(container, values, sub.keyName, sub.value, sub, markSaved);

					CharSequence result = currentItr.next();
					if (result != null)
						return result;
					return hasNext() ? next() : null;
				}

			};
		if (markSaved)
			dataVal.modified = false;
		String commentAfterValue = dataVal.commentAfterValue;
		Collection<String> comments = dataVal.comments;
		Iterator<String> commentsItr = comments != null && !comments.isEmpty() ? comments.iterator() : null;
		Object value = dataVal.value;

		if (value == null)
			return new Iterator<CharSequence>() {
				byte type = commentsItr != null ? (byte) 0 : (byte) 1;
				int pos = 0;
				Iterator<CharSequence> currentItr;

				@Override
				public boolean hasNext() {
					switch (type) {
					case 0:
						return commentsItr!=null && commentsItr.hasNext();
					case 1:
						return true;
					case 2:
						return currentItr != null && currentItr.hasNext() || pos == -1 || linked.sub != null && linked.sub.length > pos;
					}
					return false;
				}

				@Override
				public CharSequence next() {
					switch (type) {
					case 0:
						while (commentsItr!=null && commentsItr.hasNext()) {
							CharSequence result = getCharSequence(commentsItr, container, linked, values);
							if (result != null) return result;
						}
						type = 1;
						return hasNext() ? next() : null;
					case 1: {
						type = 2;
						String result = values.add(appendName(container, linked.space, section, null, (char) 0, commentAfterValue));
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
						Section sub = linked.sub[pos++];
						currentItr = startIterator(container, values, sub.keyName, sub.value, sub, markSaved);

						CharSequence result = currentItr.next();
						if (result != null)
							return result;
						if (hasNext())
							return next();
					}
					return null;
				}

			};
		int size;
		if (value instanceof Collection || value.getClass().isArray())
			if (value instanceof Collection) {
				if (((Collection<?>) value).isEmpty()) {
					return getCharSequenceIterator(container, values, section, linked, markSaved, commentAfterValue, commentsItr);
				}
				if (dataVal.writtenValue != null) {
					return getCharSequenceIterator(container, values, section, dataVal, linked, markSaved, commentAfterValue, commentsItr);
				}
				Iterator<?> itr = ((Collection<?>) value).iterator();
				return new Iterator<CharSequence>() {
					byte type = commentsItr != null ? (byte) 0 : (byte) 1;
					int pos = 0;
					Iterator<CharSequence> currentItr;

					@Override
					public boolean hasNext() {
						switch (type) {
						case 0:
							return commentsItr!=null && commentsItr.hasNext();
						case 1:
							return true;
						case 2:
							return itr.hasNext();
						case 3:
							return currentItr != null && currentItr.hasNext() || pos == -1 || linked.sub != null && linked.sub.length > pos;
						}
						return false;
					}

					@Override
					public CharSequence next() {
						switch (type) {
						case 0:
							while (commentsItr!=null && commentsItr.hasNext()) {
								CharSequence result = getCharSequence(commentsItr, container, linked, values);
								if (result != null) return result;
							}
							type = 1;
							return hasNext() ? next() : null;
						case 1: {
							type = 2;
							String result = values.add(appendName(container, linked.space, section, null, (char) 0, commentAfterValue));
							if (result != null)
								return result;
							return hasNext() ? next() : null;
						}
						case 2: {
							String result = values.add(writeListValue(container, linked.space, itr.next()));
							if (!itr.hasNext())
								type = 3;
							if (result != null)
								return result;
							return hasNext() ? next() : null;
						}
						case 3:
							if (currentItr != null && currentItr.hasNext()) {
								CharSequence result = currentItr.next();
								if (result != null)
									return result;
								return hasNext() ? next() : null;
							}
							Section sub = linked.sub[pos++];
							currentItr = startIterator(container, values, sub.keyName, sub.value, sub, markSaved);

							CharSequence result = currentItr.next();
							if (result != null)
								return result;
							if (hasNext())
								return next();
						}
						return null;
					}

				};
			} else if ((size = Array.getLength(value)) != 0)
				if (dataVal.writtenValue != null)
					return getCharSequenceIterator(container, values, section, dataVal, linked, markSaved, commentAfterValue, commentsItr);
				else
					return new Iterator<CharSequence>() {
						byte type = commentsItr != null ? (byte) 0 : (byte) 1;
						int pos = 0;
						Iterator<CharSequence> currentItr;

						@Override
						public boolean hasNext() {
							switch (type) {
							case 0:
								return commentsItr!=null && commentsItr.hasNext();
							case 1:
								return true;
							case 2:
								return size > pos;
							case 3:
								return currentItr != null && currentItr.hasNext() || pos == -1 || linked.sub != null && linked.sub.length > pos;
							}
							return false;
						}

						@Override
						public CharSequence next() {
							switch (type) {
							case 0:
								while (commentsItr!=null && commentsItr.hasNext()) {
									CharSequence result = getCharSequence(commentsItr, container, linked, values);
									if (result != null) return result;
								}
								type = 1;
								return hasNext() ? next() : null;
							case 1: {
								type = 2;
								String result = values.add(appendName(container, linked.space, section, null, (char) 0, commentAfterValue));
								if (result != null)
									return result;
								return hasNext() ? next() : null;
							}
							case 2: {
								String result = values.add(writeListValue(container, linked.space, Array.get(value, pos++)));
								if (pos == size) {
									type = 3;
									pos = 0;
								}
								if (result != null)
									return result;
								return hasNext() ? next() : null;
							}
							case 3:
								if (currentItr != null && currentItr.hasNext()) {
									CharSequence result = currentItr.next();
									if (result != null)
										return result;
									return hasNext() ? next() : null;
								}
								Section sub = linked.sub[pos++];
								currentItr = startIterator(container, values, sub.keyName, sub.value, sub, markSaved);

								CharSequence result = currentItr.next();
								if (result != null)
									return result;
								if (hasNext())
									return next();
							}
							return null;
						}

					};
			else
				return getCharSequenceIterator(container, values, section, linked, markSaved, commentAfterValue, commentsItr);
		if (dataVal.writtenValue != null)
			return new Iterator<CharSequence>() {
				byte type = commentsItr != null ? (byte) 0 : (byte) 1;
				int pos = 0;
				Iterator<CharSequence> currentItr;

				@Override
				public boolean hasNext() {
					switch (type) {
					case 0:
						return commentsItr!=null && commentsItr.hasNext();
					case 1:
						return true;
					case 2:
						return currentItr != null && currentItr.hasNext() || pos == -1 || linked.sub != null && linked.sub.length > pos;
					}
					return false;
				}

				@Override
				public CharSequence next() {
					switch (type) {
					case 0:
						while (commentsItr!=null && commentsItr.hasNext()) {
							CharSequence result = getCharSequence(commentsItr, container, linked, values);
							if (result != null) return result;
						}
						type = 1;
						return hasNext() ? next() : null;
					case 1: {
						type = 2;
						String result = values.add(appendName(container, linked.space, section, dataVal.writtenValue,
								value instanceof CharSequence ? '"' : value instanceof Number || value instanceof Character ? '\'' : (char) 0, commentAfterValue));
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
						Section sub = linked.sub[pos++];
						currentItr = startIterator(container, values, sub.keyName, sub.value, sub, markSaved);

						CharSequence result = currentItr.next();
						if (result != null)
							return result;
						if (hasNext())
							return next();
					}
					return null;
				}

			};
		if (value instanceof Number || value instanceof Character)
			return new Iterator<CharSequence>() {
				byte type = commentsItr != null ? (byte) 0 : (byte) 1;
				int pos = 0;
				Iterator<CharSequence> currentItr;

				@Override
				public boolean hasNext() {
					switch (type) {
					case 0:
						return commentsItr!=null && commentsItr.hasNext();
					case 1:
						return true;
					case 2:
						return currentItr != null && currentItr.hasNext() || pos == -1 || linked.sub != null && linked.sub.length > pos;
					}
					return false;
				}

				@Override
				public CharSequence next() {
					switch (type) {
					case 0:
						while (commentsItr!=null && commentsItr.hasNext()) {
							CharSequence result = getCharSequence(commentsItr, container, linked, values);
							if (result != null) return result;
						}
						type = 1;
						return hasNext() ? next() : null;
					case 1: {
						type = 2;
						String result = values.add(appendName(container, linked.space, section, value.toString(), '\'', commentAfterValue));
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
						Section sub = linked.sub[pos++];
						currentItr = startIterator(container, values, sub.keyName, sub.value, sub, markSaved);

						CharSequence result = currentItr.next();
						if (result != null)
							return result;
						if (hasNext())
							return next();
					}
					return null;
				}

			};
		if (value instanceof CharSequence)
			return new Iterator<CharSequence>() {
				byte type = commentsItr != null ? (byte) 0 : (byte) 1;
				int pos = 0;
				Iterator<CharSequence> currentItr;

				@Override
				public boolean hasNext() {
					switch (type) {
					case 0:
						return commentsItr!=null && commentsItr.hasNext();
					case 1:
						return true;
					case 2:
						return currentItr != null && currentItr.hasNext() || pos == -1 || linked.sub != null && linked.sub.length > pos;
					}
					return false;
				}

				@Override
				public CharSequence next() {
					switch (type) {
					case 0:
						while (commentsItr!=null && commentsItr.hasNext()) {
							CharSequence result = getCharSequence(commentsItr, container, linked, values);
							if (result != null) return result;
						}
						type = 1;
						return hasNext() ? next() : null;
					case 1: {
						type = 2;
						String result = values.add(appendName(container, linked.space, section, value instanceof String ? (String) value : value.toString(), '"', commentAfterValue));
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
						Section sub = linked.sub[pos++];
						currentItr = startIterator(container, values, sub.keyName, sub.value, sub, markSaved);

						CharSequence result = currentItr.next();
						if (result != null)
							return result;
						if (hasNext())
							return next();
					}
					return null;
				}

			};
		return new Iterator<CharSequence>() {
			byte type = commentsItr != null ? (byte) 0 : (byte) 1;
			int pos = 0;
			Iterator<CharSequence> currentItr;

			@Override
			public boolean hasNext() {
				switch (type) {
				case 0:
					return commentsItr!=null && commentsItr.hasNext();
				case 1:
					return true;
				case 2:
					return currentItr != null && currentItr.hasNext() || pos == -1 || linked.sub != null && linked.sub.length > pos;
				}
				return false;
			}

			@Override
			public CharSequence next() {
				if (currentItr != null && currentItr.hasNext()) {
					CharSequence result = currentItr.next();
					if (result != null)
						return result;
					return hasNext() ? next() : null;
				}
				switch (type) {
				case 0:
					while (commentsItr!=null && commentsItr.hasNext()) {
						CharSequence result = getCharSequence(commentsItr, container, linked, values);
						if (result != null) return result;
					}
					type = 1;
					return hasNext() ? next() : null;
				case 1: {
					type = 2;
					String result = values.add(appendName(container, linked.space, section, Json.writer().write(value), (char) 0, commentAfterValue));
					if (result != null)
						return result;
					return hasNext() ? next() : null;
				}
				case 2:
					Section sub = linked.sub[pos++];
					currentItr = startIterator(container, values, sub.keyName, sub.value, sub, markSaved);

					CharSequence result = currentItr.next();
					if (result != null)
						return result;
					if (hasNext())
						return next();
				}
				return null;
			}

		};
	}

	private static Iterator<CharSequence> getCharSequenceIterator(StringContainer container, StringArrayList values, String section, Section linked, boolean markSaved, String commentAfterValue, Iterator<String> commentsItr) {
		return new Iterator<CharSequence>() {
			byte type = commentsItr != null ? (byte) 0 : (byte) 1;
			int pos = 0;
			Iterator<CharSequence> currentItr;

			@Override
			public boolean hasNext() {
				switch (type) {
				case 0:
					return commentsItr!=null && commentsItr.hasNext();
				case 1:
					return true;
				case 2:
					return currentItr != null && currentItr.hasNext() || pos == -1 || linked.sub != null && linked.sub.length > pos;
				}
				return false;
			}

			@Override
			public CharSequence next() {
				switch (type) {
				case 0:
					while (commentsItr!=null && commentsItr.hasNext()) {
						CharSequence result = getCharSequence(commentsItr, container, linked, values);
						if (result != null) return result;
					}
					type = 1;
					return hasNext() ? next() : null;
				case 1: {
					type = 2;
					String result = values.add(appendName(container, linked.space, section, "[]", (char) 0, commentAfterValue));
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
					Section sub = linked.sub[pos++];
					currentItr = startIterator(container, values, sub.keyName, sub.value, sub, markSaved);

					CharSequence result = currentItr.next();
					if (result != null)
						return result;
					if (hasNext())
						return next();
				}
				return null;
			}

		};
	}

	private static Iterator<CharSequence> getCharSequenceIterator(StringContainer container, StringArrayList values, String section, DataValue dataVal, Section linked, boolean markSaved, String commentAfterValue, Iterator<String> commentsItr) {
		return new Iterator<CharSequence>() {
			byte type = commentsItr != null ? (byte) 0 : (byte) 1;
			int pos = 0;
			Iterator<CharSequence> currentItr;

			@Override
			public boolean hasNext() {
				switch (type) {
				case 0:
					return commentsItr.hasNext();
				case 1:
					return true;
				case 2:
					return currentItr != null && currentItr.hasNext() || pos == -1 || linked.sub != null && linked.sub.length > pos;
				}
				return false;
			}

			@Override
			public CharSequence next() {
				switch (type) {
				case 0:
					while (commentsItr.hasNext()) {
						CharSequence result = getCharSequence(commentsItr, container, linked, values);
						if (result != null) return result;
					}
					type = 1;
					return hasNext() ? next() : null;
				case 1: {
					type = 2;
					String result = values.add(appendName(container, linked.space, section, dataVal.writtenValue, (char) 0, commentAfterValue));
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
					Section sub = linked.sub[pos++];
					currentItr = startIterator(container, values, sub.keyName, sub.value, sub, markSaved);

					CharSequence result = currentItr.next();
					if (result != null)
						return result;
					if (hasNext())
						return next();
				}
				return null;
			}

		};
	}

	private static CharSequence getCharSequence(Iterator<String> commentsItr, StringContainer container, Section linked, StringArrayList values) {
		String comment = commentsItr.next();
		container.clear();
		for (int i = 0; i < linked.space; ++i)
			container.append(' ').append(' ');
        return values.add(container.append(comment).append(System.lineSeparator()));
    }

	private static CharSequence appendName(StringContainer container, int space, String section) {
		appendSectionName(container, space, section);
		container.append(System.lineSeparator());
		return container;
	}

	private static void appendSectionName(StringContainer container, int space, String section) {
		container.clear();
		for (int i = 0; i < space; ++i)
			container.append(' ').append(' ');
		if (section.charAt(0) == '#')
			container.append('\'').append(section).append('\'').append(':');
		else
			container.append(section).append(':');
	}

	private static CharSequence appendName(StringContainer container, int space, String section, String value, char queto, String comment) {
		appendSectionName(container, space, section);
		if (value != null) {
			container.append(' ');
			if (queto == 0)
				container.append(value);
			else
				container.append(queto).append(replaceWithEscape(value, queto)).append(queto);
        }
        if (comment != null && !comment.trim().isEmpty())
            if (comment.charAt(0) == ' ')
                container.append(comment);
            else
                container.append(' ').append(comment);
        container.append(System.lineSeparator());
		return container;
	}

	private static CharSequence writeListValue(StringContainer container, int space, Object value) {
		container.clear();
		for (int i = 0; i < space; ++i)
			container.append(' ').append(' ');
		container.append('-').append(' ');
		container.append(Json.writer().write(value));
		container.append(System.lineSeparator());
		return container;
	}

	public static CharSequence replaceWithEscape(String value, char add) {
		int startAt = value.indexOf(add);
		if (startAt == -1)
			return value;
		StringContainer container = new StringContainer(value);
		for (int i = startAt; i < container.length(); ++i) {
			char c = container.charAt(i);
			if (c == add)
				container.insert(i++, '\\');
		}
		return container;

	}
}
