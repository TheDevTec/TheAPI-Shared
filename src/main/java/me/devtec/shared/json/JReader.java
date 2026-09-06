package me.devtec.shared.json;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.RandomAccess;

import me.devtec.shared.utility.ParseUtils;

public interface JReader {

	// For complex objects
	@SuppressWarnings("unchecked")
	default Object read(String json) {
		if (json == null || json.isEmpty())
			return json;

		final Object simpleRead = simpleRead(json);

		if (simpleRead instanceof Map)
			return JsonUtils.read(simpleRead);

		if (!(simpleRead instanceof Collection))
			return simpleRead;

		final Collection<Object> collection = (Collection<Object>) simpleRead;

		if (collection instanceof List) {
			final List<Object> list = (List<Object>) collection;

			if (list instanceof RandomAccess)
				for (int i = 0, size = list.size(); i < size; ++i)
					list.set(i, JsonUtils.read(list.get(i)));
			else {
				final ListIterator<Object> iterator = list.listIterator();

				while (iterator.hasNext())
					iterator.set(JsonUtils.read(iterator.next()));
			}
		} else {
			final Object[] values = collection.toArray();

			collection.clear();

			for (Object value : values)
				collection.add(JsonUtils.read(value));
		}

		return collection;
	}

	// For lists or maps
	default Object simpleRead(String json) {
		if (json == null || json.isEmpty())
			return json;

		final int length = json.length();
		final char first = json.charAt(0);
		final char last = json.charAt(length - 1);

		if (first == 'n' && last == 'l' && length == 4 && "null".equals(json))
			return null;

		if (first == 't' && last == 'e' && length == 4 && "true".equalsIgnoreCase(json))
			return Boolean.TRUE;

		if (first == 'f' && last == 'e' && length == 5 && "false".equalsIgnoreCase(json))
			return Boolean.FALSE;

		if (first >= '0' && first <= '9' || first == '+' || first == '-') {
			final Number number = ParseUtils.getNumber(json);

			if (number != null)
				return number;

			return json;
		}

		if (first == '{' && last == '}')
			try {
				final Object read = fromGson(json, Map.class);
				return read == null ? json : read;
			} catch (Exception ignored) {
				return json;
			}

		if (first == '[' && last == ']')
			try {
				final Object read = fromGson(json, Collection.class);
				return read == null ? json : read;
			} catch (Exception ignored) {
			}

		return json;
	}

	Object fromGson(String json, Class<?> clazz);
}