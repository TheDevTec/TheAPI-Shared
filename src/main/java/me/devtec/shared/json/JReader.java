package me.devtec.shared.json;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import me.devtec.shared.utility.ParseUtils;

public interface JReader {
	// For complex objects
	@SuppressWarnings("unchecked")
	default Object read(String json) {
		if (json == null || json.isEmpty())
			return json;
		Object simpleRead = simpleRead(json);
		if (simpleRead instanceof Map)
			return JsonUtils.read(simpleRead);
		if (simpleRead instanceof Collection) {
			Collection<Object> c = (Collection<Object>) simpleRead;
			if (c instanceof List) {
				ListIterator<Object> itr = ((List<Object>) c).listIterator();
				while (itr.hasNext())
					itr.set(JsonUtils.read(itr.next()));
			} else {
				Object[] array = c.toArray();
				c.clear();
				for (Object o : array)
					c.add(JsonUtils.read(o));
			}
			return c;
		}
		return simpleRead;
	}

	// For lists or maps
	default Object simpleRead(String json) {
		if (json == null || json.isEmpty())
			return json;
		char first = json.charAt(0);
		char last = json.charAt(json.length() - 1);
		if (first == 'n' && last == 'l' && "null".equals(json))
			return null;
		if (first == 't' && last == 'e' && "true".equalsIgnoreCase(json))
			return true;
		if (first == 'f' && last == 'e' && "false".equalsIgnoreCase(json))
			return false;
		if (first >= '0' && first <= '9' || first == '+' || first == '-') {
			Number number = ParseUtils.getNumber(json);
			if (number != null)
				return number;
			return json;
		}
		if (first == '{' && last == '}' || first == '[' && last == ']') {
			Object read = null;
			if (first == '{')
				try {
					read = fromGson(json, Map.class);
				} catch (Exception ignored) {
				}
			else
				try {
					read = fromGson(json, Collection.class);
				} catch (Exception ignored) {

				}
			return read == null ? json : read;
		}
		return json;
	}

	Object fromGson(String json, Class<?> clazz);
}
