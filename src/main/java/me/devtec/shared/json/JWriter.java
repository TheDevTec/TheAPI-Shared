package me.devtec.shared.json;

import me.devtec.shared.json.custom.CustomJsonWriter;

public interface JWriter {

	default Object writeWithoutParse(Object object) {
		return JsonUtils.writeWithoutParseStatic(object);
	}

	default String write(Object object) {
		try {
			if (object == null)
				return "null";

			if (object instanceof CharSequence)
				return CustomJsonWriter.parseToString(object.toString());

			if (object instanceof Number || object instanceof Character)
				return '\'' + object.toString() + '\'';

			return toGson(writeWithoutParse(object));
		} catch (Exception ignored) {
			return null;
		}
	}

	// For lists or maps
	default String simpleWrite(Object object) {
		if (object == null)
			return "null";

		if (object instanceof CharSequence || object instanceof Boolean || object instanceof Number
				|| object instanceof Character)
			return object.toString();

		return toGson(object);
	}

	String toGson(Object object);
}