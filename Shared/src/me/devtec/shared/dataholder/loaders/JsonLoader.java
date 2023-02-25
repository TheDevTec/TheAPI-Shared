package me.devtec.shared.dataholder.loaders;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class JsonLoader extends EmptyLoader {

	@SuppressWarnings("unchecked")
	@Override
	public void load(String input) {
		if (input == null || input.length() == 0)
			return;
		char startChar = input.charAt(0);
		if (!(startChar == '{' || startChar == '['))
			return;
		reset();
		try {
			Object read = Json.reader().read(input.replace(System.lineSeparator(), ""));
			if (read instanceof Map)
				for (Entry<Object, Object> keyed : ((Map<Object, Object>) read).entrySet()) {
					primaryKeys.add(splitFirst(keyed.getKey() + ""));
					data.put(keyed.getKey() + "", DataValue.of(null, Json.reader().read(keyed.getValue() + ""), null));
				}
			else
				for (Object o : (Collection<Object>) read)
					for (Entry<Object, Object> keyed : ((Map<Object, Object>) o).entrySet()) {
						primaryKeys.add(splitFirst(keyed.getKey() + ""));
						data.put(keyed.getKey() + "", DataValue.of(null, Json.reader().read(keyed.getValue() + ""), null));
					}
			loaded = true;
		} catch (Exception er) {
			loaded = false;
		}
	}

	private static String splitFirst(String text) {
		int next = text.indexOf('.');
		return next != -1 ? text.substring(0, next) : text;
	}
}
