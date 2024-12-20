package me.devtec.shared.json;

import java.util.Collection;
import java.util.Map;

import me.devtec.shared.utility.ParseUtils;

public interface JReader {
	// For complex objects
	default Object read(String json) {
		if (json == null || json.isEmpty()) {
			return json;
		}
		Object simpleRead = simpleRead(json);
		if (simpleRead instanceof Map) {
			return JsonUtils.read(simpleRead);
		}
		return simpleRead;
	}

	// For lists or maps
	default Object simpleRead(String json) {
		if (json == null || json.isEmpty()) {
			return json;
		}
		char first = json.charAt(0);
		char last = json.charAt(json.length() - 1);
		if (first == 'n' && last == 'l' && json.equals("null")) {
			return null;
		}
		if (first == 't' && last == 'e' && json.equalsIgnoreCase("true")) {
			return true;
		}
		if (first == 'f' && last == 'e' && json.equalsIgnoreCase("false")) {
			return false;
		}
		if (first >= '0' && first <= '9' || first == '+' || first == '-') {
			Number number = ParseUtils.getNumber(json);
			if (number != null) {
				return number;
			}
		}
		if (first == '{' && last == '}' || first == '[' && last == ']') {
			Object read = null;
			if (first == '{') {
				try {
					read = fromGson(json, Map.class);
				} catch (Exception ignored) {
				}
			} else {
				try {
				    read = fromGson(json, Collection.class);
				} catch (Exception ignored) {

				}
			}
			return read == null ? json : read;
		}
		return json;
	}

	Object fromGson(String json, Class<?> clazz);
}
