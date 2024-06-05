package me.devtec.shared.json;

import me.devtec.shared.dataholder.StringContainer;

public interface JWriter {

	default Object writeWithoutParse(Object s) {
		return JsonUtils.writeWithoutParseStatic(s);
	}

	default String write(Object s) {
		try {
			if (s == null)
				return "null";
			if (s instanceof CharSequence) {
				StringContainer container = new StringContainer(s.toString());
				int i = container.length();
				while (i != -1) {
					char c = container.charAt(i);
					if (c == '"')
						container.insert(i, '\\');
					--i;
				}
				container.insert(0, '"');
				container.append('"');
				return container.toString();
			}
			return s instanceof Number || s instanceof Character ? '\'' + s.toString() + '\'' : toGson(writeWithoutParse(s));
		} catch (Exception err) {
		}
		return null;
	}

	// For lists or maps
	default String simpleWrite(Object object) {
		if (object == null)
			return "null";
		if (object instanceof CharSequence || object instanceof Boolean || object instanceof Number || object instanceof Character)
			return object.toString();
		return toGson(object);
	}

	String toGson(Object object);
}
