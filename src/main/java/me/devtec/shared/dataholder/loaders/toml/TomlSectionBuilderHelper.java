package me.devtec.shared.dataholder.loaders.toml;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import me.devtec.shared.API;
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
		public int id;

		public Section(Section parent, String key, DataValue val) {
			this.parent = parent;
			isKey = true;
			keyName = key;
			value = val;
		}

		public Section(String key, DataValue val) {
			keyName = key;
			value = val;
		}

		public boolean isKey() {
			return isKey;
		}

		public synchronized void add(Section sec) {
			if (sub == null) {
				sub = new Section[] { sec };
			} else {
				Section[] copy = Arrays.copyOf(sub, sub.length + 1);
				copy[copy.length - 1] = sec;
				sub = copy;
				Arrays.sort(copy, (o1, o2) -> o1.id - o2.id);
			}
		}

		public Section get(String name) {
			if (sub != null) {
				for (Section section : sub) {
					if (section.keyName.equals(name)) {
						return section;
					}
				}
			}
			return null;
		}

		public synchronized Section create(int id, String name) {
			Section current = this;
			int start = 0;
			int end = name.indexOf('.');
			if (end == -1) {
				end = name.length();
			}
			while (end != -1) {
				String key = name.substring(start, end);
				Section sec = current.get(key);
				if (sec == null) {
					sec = new Section(current, key, null);
					sec.id = id;
					current.add(sec);
				}
				current = sec;

				if (end >= name.length() || start >= name.length()) {
					break;
				}
				start = end + 1;
				end = name.indexOf('.', start);
				if (end == -1) {
					end = name.length();
				}
			}
			current.id = id;
			return current;
		}

		public StringContainer fullName(StringContainer container, boolean correct) {
			container.clear();
			for (Section parent = this; parent != null; parent = parent.parent) {
				container.insert(0, '.').insert(0, parent.keyName);
			}
			if (correct && container.charAt(container.length() - 1) == '.') {
				container.deleteCharAt(container.length() - 1);
			}
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

	private static void processEntries(Map<String, Section> keysWithoutSub, Map<String, Section> map, Set<Entry<String, DataValue>> set) {
		if (set.size() >= 512) {
			CountDownLatch latch = new CountDownLatch(set.size());
			int id = 0;
			for (Entry<String, DataValue> entry : set) {
				int privateId = id++;
				API.getExecutor().submit(() -> {
					try {
						processEntry(privateId, keysWithoutSub, map, entry.getKey(), entry.getValue());
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
			}

			try {
				latch.await();
			} catch (InterruptedException e) {
			}
			return;
		}
		StringContainer container = new StringContainer(64);
		Section prevParent = null;
		StringContainer fullNameContainer = new StringContainer(64);
		int id = 0;
		for (Entry<String, DataValue> entry : set) {
			int privateId = id++;
			container.clear();
			container.append(entry.getKey());
			Section main;
			if (prevParent != null && (container.startsWith(prevParent.fullName(fullNameContainer, false), 0)
					|| (prevParent = prevParent.parent) != null && container.startsWith(prevParent.fullName(fullNameContainer, false), 0))) {
				container.delete(0, prevParent.fullName(fullNameContainer, false).length());
				main = prevParent;
			} else {
				int pos = container.indexOf('.');
				if (pos == -1) {
					continue;
				}
				String primaryKey = container.substring(0, pos);
				container.delete(0, primaryKey.length() + 1);
				main = map.get(primaryKey);
				if (main == null) {
					main = keysWithoutSub.remove(primaryKey);
					if (main == null) {
						continue;
					}
					map.put(primaryKey, main);
				}
			}
			Section sec = main.create(privateId, container.toString());
			sec.value = entry.getValue();
			sec.isKey = false;
			prevParent = sec.parent;
		}
		container.clear();
	}

	private static void processEntry(int privateId, Map<String, Section> keysWithoutSub, Map<String, Section> map, String key, DataValue value) {
		int pos = key.indexOf('.');
		String primaryKey = key.substring(0, pos);
		Section currentSection = map.get(primaryKey);
		if (currentSection == null) {
			currentSection = keysWithoutSub.remove(primaryKey);
			if (currentSection == null) {
				return;
			}
			map.put(primaryKey, currentSection);
		}
		currentSection = currentSection.create(privateId, key.substring(pos + 1));
		currentSection.value = value;
	}

	public static Iterator<CharSequence> prepareBuilder(Set<String> primaryKeys, DataLoader dataLoader, boolean markSaved) {
		StringContainer container = new StringContainer(64);
		Map<String, Section> map = new LinkedHashMap<>();
		Map<String, Section> keysWithoutSub = new LinkedHashMap<>();
		for (String primaryKey : primaryKeys) {
			keysWithoutSub.put(primaryKey, new Section(primaryKey, dataLoader.get(primaryKey)));
		}

		processEntries(keysWithoutSub, map, dataLoader.entrySet());
		StringArrayList values = new StringArrayList();
		Iterator<Section> itr = keysWithoutSub.values().iterator();
		Iterator<Section> itrSecond = map.values().iterator();
		return new Iterator<CharSequence>() {
			byte modeStep = (byte) (itr.hasNext() ? 0 : 1);

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
				while (currentItr != null && currentItr.hasNext()) {
					CharSequence next = values.add(currentItr.next());
					if (next != null) {
						return next;
					}
				}
				switch (modeStep) {
				case 0: {
					if (!itr.hasNext()) {
						modeStep = 1;
						return next();
					}
					Section section = itr.next();
					currentItr = startIterator(container, section.value, section, markSaved);
					if (hasNext()) {
						return next();
					}
					String result = values.complete();
					values.clear();
					return result;
				}
				case 1:
					if (!itrSecond.hasNext()) {
						String result = values.complete();
						values.clear();
						return result;
					}
					Section section = itrSecond.next();
					currentItr = startIterator(container, section.value, section, markSaved);
					if (hasNext()) {
						return next();
					}
					String result = values.complete();
					values.clear();
					return result;
				}
				return null;
			}
		};
	}

	public static Iterator<CharSequence> startIterator(StringContainer container, DataValue dataVal, Section linked, boolean markSaved) {
		if (dataVal == null) {
			return startMultipleIterate(container, linked.sub, markSaved);
		}
		if (markSaved) {
			dataVal.modified = false;
		}

		if (dataVal.value == null) {
			return startMultipleIterate(container, linked.sub, markSaved);
		}
		String commentAfterValue = dataVal.commentAfterValue;
		Collection<String> comments = dataVal.comments;
		Iterator<String> commentsItr = comments != null && !comments.isEmpty() ? comments.iterator() : null;
		Object value = dataVal.value;
		if (value != null) {
			if (dataVal.writtenValue != null) {
				return new Iterator<CharSequence>() {
					byte type = commentsItr != null ? (byte) 0 : (byte) 1;
					final Iterator<CharSequence> currentItr = linked.sub == null ? null : startMultipleIterate(container, linked.sub, markSaved);

					@Override
					public boolean hasNext() {
						switch (type) {
						case 0:
							return commentsItr != null && commentsItr.hasNext();
						case 2:
							return currentItr != null && currentItr.hasNext();
						case 1:
							return true;
						}
						return false;
					}

					@Override
					public CharSequence next() {
						switch (type) {
						case 0:
							if (commentsItr != null && commentsItr.hasNext()) {
								container.clear();
								return container.append(commentsItr.next()).append(System.lineSeparator());
							}
							type = 1;
							return hasNext() ? next() : null;
						case 1: {
							type = 2;
							return appendName(container, linked, dataVal.writtenValue, value instanceof String ? '"' : (char) 0, commentAfterValue);
						}
						case 2:
							if (currentItr != null && currentItr.hasNext()) {
								CharSequence result = currentItr.next();
								if (result != null) {
									return result;
								}
							}
						}
						return null;
					}

				};
			} else if (value instanceof CharSequence) {
				return new Iterator<CharSequence>() {
					byte type = commentsItr != null ? (byte) 0 : (byte) 1;
					final Iterator<CharSequence> currentItr = linked.sub == null ? null : startMultipleIterate(container, linked.sub, markSaved);

					@Override
					public boolean hasNext() {
						switch (type) {
						case 0:
							return commentsItr != null && commentsItr.hasNext();
						case 2:
							return currentItr != null && currentItr.hasNext();
						case 1:
							return true;
						}
						return false;
					}

					@Override
					public CharSequence next() {
						switch (type) {
						case 0:
							if (commentsItr != null && commentsItr.hasNext()) {
								container.clear();
								return container.append(commentsItr.next()).append(System.lineSeparator());
							}
							type = 1;
							return hasNext() ? next() : null;
						case 1: {
							type = 2;
							return appendName(container, linked, value instanceof String ? (String) value : value.toString(), '"', commentAfterValue);
						}
						case 2:
							if (currentItr != null && currentItr.hasNext()) {
								CharSequence result = currentItr.next();
								if (result != null) {
									return result;
								}
							}
						}
						return null;
					}

				};
			} else {
				return new Iterator<CharSequence>() {
					byte type = commentsItr != null ? (byte) 0 : (byte) 1;
					final Iterator<CharSequence> currentItr = linked.sub == null ? null : startMultipleIterate(container, linked.sub, markSaved);

					@Override
					public boolean hasNext() {
						switch (type) {
						case 0:
							return commentsItr != null && commentsItr.hasNext();
						case 2:
							return currentItr != null && currentItr.hasNext();
						case 1:
							return true;
						}
						return false;
					}

					@Override
					public CharSequence next() {
						switch (type) {
						case 0:
							if (commentsItr != null && commentsItr.hasNext()) {
								container.clear();
								return container.append(commentsItr.next()).append(System.lineSeparator());
							}
							type = 1;
							return hasNext() ? next() : null;
						case 1: {
							type = 2;
							return appendName(container, linked, Json.writer().write(value), (char) 0, commentAfterValue);
						}
						case 2:
							if (currentItr != null && currentItr.hasNext()) {
								CharSequence result = currentItr.next();
								if (result != null) {
									return result;
								}
							}
						}
						return null;
					}

				};
			}
		}
		return startMultipleIterate(container, linked.sub, markSaved);
	}

	public static Iterator<CharSequence> startMultipleIterate(StringContainer container, Section[] keys, boolean markSaved) {
		return new Iterator<CharSequence>() {
			int pos = 0;
			byte mode = 0;
			Section section;
			Iterator<String> commentsItr;
			Iterator<CharSequence> currentItr;
			boolean foundAnySub;

			@Override
			public boolean hasNext() {
				nextSection();
				boolean b = false;
				switch (mode) {
				case 0:
					b = section.value != null && section.value.value != null;
					break;
				case 1:
					b = commentsItr != null && commentsItr.hasNext() || section.value != null && section.value.value != null;
					break;
				case 2:
					b = section.value != null && section.value.value != null;
					break;
				case 3:
					b = currentItr == null || pos < keys.length;
					break;
				case 4:
					if (currentItr == null && foundAnySub) {
						b = true;
						break;
					}
					b = currentItr != null && currentItr.hasNext() || pos < keys.length;
					break;
				}
				return b;
			}

			private void nextSection() {
				if (mode == 0 && section == null) {
					while (keys.length > pos) {
						section = keys[pos++];
						commentsItr = section.value != null && section.value.comments != null ? section.value.comments.iterator() : null;
						foundAnySub |= section.sub != null;
						if (hasNext()) {
							return;
						}
					}
					pos = 0;
					mode = 4;
				}
			}

			@Override
			public CharSequence next() {
				nextSection();
				switch (mode) {
				case 0: {
					mode = commentsItr != null ? (byte) 1 : 2;
					return appendParentName(container, section.parent);
				}
				case 1: {
					if (commentsItr != null && commentsItr.hasNext()) {
						container.clear();
						return container.append(commentsItr.next()).append(System.lineSeparator());
					}
					mode = 2;
					if (!hasNext()) {
						mode = 3;
					}
					return hasNext() ? next() : null;
				}
				case 2: {
					StringContainer result;
					if (section.value.writtenValue != null) {
						result = appendName(container, section, section.value.writtenValue, section.value.value instanceof String ? '"' : (char) 0, section.value.commentAfterValue);
					} else if (section.value.value instanceof CharSequence) {
						result = appendName(container, section, section.value.value instanceof String ? (String) section.value.value : section.value.value.toString(), '"',
								section.value.commentAfterValue);
					} else {
						result = appendName(container, section, Json.writer().write(section.value.value), (char) 0, section.value.commentAfterValue);
					}
					if (markSaved) {
						section.value.modified = false;
					}
					mode = 3;
					return result;
				}
				case 3: {
					if (keys.length <= pos) {
						mode = 4;
						pos = 0;
						return hasNext() ? next() : null;
					}
					section = keys[pos++];
					commentsItr = section.value != null && section.value.comments != null ? section.value.comments.iterator() : null;
					mode = commentsItr != null ? (byte) 1 : 2;
					foundAnySub |= section.sub != null;
					if (!hasNext()) {
						mode = 4;
						pos = 0;
					}
					return hasNext() ? next() : null;
				}
				case 4:
					if (currentItr != null && currentItr.hasNext()) {
						CharSequence next = currentItr.next();
						if (next != null) {
							return next;
						}
					}
					while (pos < keys.length) {
						Section sub = keys[pos++];
						if (sub.sub != null) {
							currentItr = startMultipleIterate(container, sub.sub, markSaved);
							if (currentItr.hasNext()) {
								CharSequence next = currentItr.next();
								if (next != null) {
									return next;
								}
							}
						}
					}
					return null;
				}
				return null;
			}
		};

	}

	private static StringContainer appendParentName(StringContainer container, Section section) {
		container.clear();
		container.append('[');
		StringContainer fullName = section.fullName(new StringContainer(), true);
		if (fullName.indexOf(':') != -1 || fullName.indexOf('=') != -1 || fullName.indexOf('"') != -1) {
			container.append('\'').append(YamlSectionBuilderHelper.replaceWithEscape(fullName.toString(), '"')).append('\'');
		} else {
			container.append(section.fullName(new StringContainer(), true));
		}
		container.append(']');
		return container.append(System.lineSeparator());
	}

	private static StringContainer appendName(StringContainer container, Section section, String value, char queto, String comment) {
		container.clear();
		if (section.keyName.indexOf('#') != -1 || section.keyName.indexOf(':') != -1) {
			container.append('\'').append(section.keyName).append('\'');
		} else {
			container.append(section.keyName);
		}
		container.append('=');
		if (queto == 0) {
			container.append(value);
		} else {
			container.append(queto).append(YamlSectionBuilderHelper.replaceWithEscape(value, queto)).append(queto);
		}
		if (comment != null && !comment.trim().isEmpty()) {
			if (comment.charAt(0) == ' ') {
				container.append(comment);
			} else {
				container.append(' ').append(comment);
			}
		}
		return container.append(System.lineSeparator());
	}
}
