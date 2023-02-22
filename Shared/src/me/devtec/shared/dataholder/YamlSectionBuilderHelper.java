package me.devtec.shared.dataholder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

class YamlSectionBuilderHelper {

	public static void write(StringContainer builder, List<String> keys, Map<String, DataValue> map, boolean markSaved) {
		Map<String, SectionHolder> secs = new LinkedHashMap<>(map.size()); // correct size of map

		// Prepare sections in map
		for (String d : keys) {
			SectionHolder sec = new SectionHolder(d);
			sec.space = "";
			secs.put(d, sec);
		}

		// Build subkeys
		for (Entry<String, DataValue> e : map.entrySet()) {
			if (markSaved && e.getValue() != null)
				e.getValue().modified = false;
			int startPos = e.getKey().indexOf('.');
			if (startPos > -1) {
				List<String> split = YamlSectionBuilderHelper.split(e.getKey(), startPos);
				String first = split.get(0);
				SectionHolder holder = secs.get(first);
				if (holder == null) {
					SectionHolder sec = new SectionHolder(first);
					sec.space = "";
					secs.put(first, holder = sec);
				}
				// DEEP FIND SECTION
				for (int i = 1; i < split.size(); ++i) {
					first = split.get(i);
					SectionHolder f = holder.find(first);
					if (f == null)
						f = holder.create(first);
					holder = f;
				}
				// SET VALUE
				holder.val = e.getValue();
			} else {
				SectionHolder holder = secs.get(e.getKey());
				if (holder != null) // Fix for random errors.. maybe caused by wrong section name ".something" or
									// "something."
					holder.val = e.getValue();
			}
		}
		for (SectionHolder section : secs.values())
			YamlSectionBuilderHelper.start(section, builder);
	}

	private static List<String> split(String text, int startPos) {
		if (startPos == -1)
			return Collections.singletonList(text);

		int off = 0;
		int next = startPos;
		List<String> list = new ArrayList<>();

		// First known value
		list.add(text.substring(off, next));
		off = next + 1;

		// Continue via loop
		while ((next = text.indexOf('.', off)) != -1) {
			list.add(text.substring(off, next));
			off = next + 1;
		}
		// Remaining chars
		list.add(text.substring(off));
		return list;
	}

	public synchronized static void start(SectionHolder section, StringContainer b) {
		try {
			DataValue dataVal = section.val;
			if (dataVal == null) {
				appendName(b, section).append(System.lineSeparator());
				for (SectionHolder d : section.holders)
					YamlSectionBuilderHelper.start(d, b);
				return;
			}
			String commentAfterValue = dataVal.commentAfterValue;
			Collection<String> comments = dataVal.comments;
			Object value = dataVal.value;
			// write list values
			if (comments != null)
				for (String s : comments)
					b.append(section.space).append(s).append(System.lineSeparator());
			// if value is null, empty key
			if (value == null)
				YamlSectionBuilderHelper.addCommentIfAvailable(appendName(b, section), commentAfterValue).append(System.lineSeparator());
			// write collection or array
			else if (value instanceof Collection || value instanceof Object[]) {
				String splitted = section.space + '-' + ' ';
				if (value instanceof Collection) {
					if (!((Collection<?>) value).isEmpty())
						try {
							if (dataVal.writtenValue != null)
								YamlSectionBuilderHelper
										.addCommentIfAvailable(YamlSectionBuilderHelper.writeQuotes(appendName(b, section).append(' '), dataVal.writtenValue, value instanceof String ? '"' : 0),
												commentAfterValue)
										.append(System.lineSeparator());
							else {
								YamlSectionBuilderHelper.addCommentIfAvailable(appendName(b, section), commentAfterValue).append(System.lineSeparator());
								for (Object a : (Collection<?>) value)
									YamlSectionBuilderHelper.writeWithoutQuotesSplit(b, splitted, a);
							}
						} catch (Exception er) {
							appendName(b, section).append(System.lineSeparator());
							for (Object a : (Collection<?>) value)
								YamlSectionBuilderHelper.writeWithoutQuotesSplit(b, splitted, a);
						}
					else
						YamlSectionBuilderHelper.addCommentIfAvailable(appendName(b, section).append(' ').append('[').append(']'), commentAfterValue).append(System.lineSeparator());
				} else if (((Object[]) value).length != 0)
					try {
						if (dataVal.writtenValue != null)
							YamlSectionBuilderHelper
									.addCommentIfAvailable(YamlSectionBuilderHelper.writeQuotes(appendName(b, section).append(' '), dataVal.writtenValue, value instanceof String ? '"' : 0),
											commentAfterValue)
									.append(System.lineSeparator());
						else {
							YamlSectionBuilderHelper.addCommentIfAvailable(appendName(b, section), commentAfterValue).append(System.lineSeparator());
							for (Object a : (Object[]) value)
								YamlSectionBuilderHelper.writeWithoutQuotesSplit(b, splitted, a);
						}
					} catch (Exception er) {
						appendName(b, section).append(System.lineSeparator());
						for (Object a : (Object[]) value)
							YamlSectionBuilderHelper.writeWithoutQuotesSplit(b, splitted, a);
					}
				else
					YamlSectionBuilderHelper.addCommentIfAvailable(appendName(b, section).append(' ').append('[').append(']'), commentAfterValue).append(System.lineSeparator());
			} else // write normal value
				try {
					if (dataVal.writtenValue != null)
						YamlSectionBuilderHelper
								.addCommentIfAvailable(YamlSectionBuilderHelper.writeQuotes(appendName(b, section).append(' '), dataVal.writtenValue, value instanceof String ? '"' : '\''),
										commentAfterValue)
								.append(System.lineSeparator());
					else if (value instanceof String)
						YamlSectionBuilderHelper.addCommentIfAvailable(YamlSectionBuilderHelper.writeQuotes(appendName(b, section).append(' '), (String) value, '"'), commentAfterValue)
								.append(System.lineSeparator());
					else
						YamlSectionBuilderHelper.addCommentIfAvailable(YamlSectionBuilderHelper.writeWithoutQuotes(appendName(b, section).append(' '), value), commentAfterValue)
								.append(System.lineSeparator());
				} catch (Exception er) {
					if (value instanceof String)
						YamlSectionBuilderHelper.addCommentIfAvailable(YamlSectionBuilderHelper.writeQuotes(appendName(b, section).append(' '), (String) value, '"'), commentAfterValue)
								.append(System.lineSeparator());
					else
						YamlSectionBuilderHelper.addCommentIfAvailable(YamlSectionBuilderHelper.writeWithoutQuotes(appendName(b, section).append(' '), value), commentAfterValue)
								.append(System.lineSeparator());
				}
		} catch (Exception err) {
			err.printStackTrace();
		}
		if (section.holders != null)
			for (SectionHolder d : section.holders)
				YamlSectionBuilderHelper.start(d, b);
	}

	private static StringContainer appendName(StringContainer sectionLine, SectionHolder section) {
		// write lines before section
		sectionLine.append(section.space);

		// write section name
		if (section.name.length() == 1 || section.name.indexOf(':') != -1 || section.name.charAt(0) == '#')
			sectionLine.append('\'').append(section.name).append('\'').append(':');
		else
			sectionLine.append(section.name).append(':');
		return sectionLine;
	}

	private static StringContainer addCommentIfAvailable(StringContainer append, String commentAfterValue) {
		if (commentAfterValue == null)
			return append;
		return append.append(' ').append(commentAfterValue);
	}

	protected static StringContainer writeQuotesSplit(StringContainer b, String split, String value) {
		b.append(split);
		b.append('"');
		replaceWithEscape(b, value, '"');
		b.append('"');
		b.append(System.lineSeparator());
		return b;
	}

	protected static StringContainer writeWithoutQuotesSplit(StringContainer b, String split, Object value) {
		b.append(split);
		b.append(Json.writer().write(value));
		b.append(System.lineSeparator());
		return b;
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

	public static class SectionHolder {

		List<SectionHolder> holders;
		String name;
		DataValue val;
		String space;

		public SectionHolder(String d) {
			name = d;
		}

		public SectionHolder find(String name) {
			if (holders != null)
				for (SectionHolder section : holders)
					if (section.name.equals(name))
						return section;
			return null;
		}

		public SectionHolder create(String name) {
			SectionHolder sec = new SectionHolder(name);
			sec.space = space + "  ";
			if (holders == null)
				holders = new ArrayList<>();
			holders.add(sec);
			return sec;
		}
	}
}
