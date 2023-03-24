package me.devtec.shared.json;

import java.util.Collection;
import java.util.Map;

import me.devtec.shared.utility.ParseUtils;

public interface JReader {
	// For complex objects
	public default Object read(String json) {
		if (json == null || json.isEmpty())
			return json;
		Object simpleRead = simpleRead(json);
		if (simpleRead instanceof Map)
			return JsonUtils.read(simpleRead);
		return simpleRead;
	}

	// For lists or maps
	public default Object simpleRead(String json) {
		if (json == null || json.trim().isEmpty())
			return json;
		char first = json.charAt(0);
		if (first == 'n' && json.equals("null"))
			return null;
		if (first == 't' && json.equalsIgnoreCase("true"))
			return true;
		if (first == 'f' && json.equalsIgnoreCase("false"))
			return false;
		if (first >= 48 && first <= 57 || first == '+' || first == '-') {
			Number number = ParseUtils.getNumber(json);
			if (number != null)
				return number;
		}
		if (first == '{' || first == '[') {
			Object read = null;
			try {
				read = fromGson(json, Map.class);
			} catch (Exception er) {
			}
			if (read == null)
				try {
					read = fromGson(json, Collection.class);
				} catch (Exception err) {

				}
			return read == null ? json : read;
		}
		return json;
	}

	public Object fromGson(String json, Class<?> clazz);
}
