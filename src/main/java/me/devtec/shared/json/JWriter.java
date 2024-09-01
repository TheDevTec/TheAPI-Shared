package me.devtec.shared.json;

import me.devtec.shared.json.custom.CustomJsonWriter;

public interface JWriter {

	default Object writeWithoutParse(Object s) {
		return JsonUtils.writeWithoutParseStatic(s);
	}

	default String write(Object s) {
		try {
			if (s == null)
				return "null";
			if (s instanceof CharSequence) {
				return CustomJsonWriter.parseToString(s.toString());
			}
			return s instanceof Number || s instanceof Character ? '\'' + s.toString() + '\'' : toGson(writeWithoutParse(s));
		} catch (Exception ignored) {
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
