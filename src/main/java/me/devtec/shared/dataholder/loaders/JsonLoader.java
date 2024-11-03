package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class JsonLoader extends EmptyLoader {

	@SuppressWarnings("unchecked")
	@Override
	public void load(String input) {
		if (input == null)
			return;
		char startChar = input.isEmpty() ? 0 : lookupForStart(input);
		char endChar = input.isEmpty() ? 0 : lookupForEnd(input);
		if ((startChar != '{' || endChar != '}') && (startChar != '[' || endChar != ']'))
			return;
		reset();
		try {
			Object read = Json.reader().read(replace(input));
			if (read instanceof Map)
				for (Entry<Object, Object> keyed : ((Map<Object, Object>) read).entrySet())
					set(keyed.getKey() + "", DataValue.of(null, Json.reader().read(keyed.getValue() + ""), null));
			else
				for (Object o : (Collection<Object>) read)
					for (Entry<Object, Object> keyed : ((Map<Object, Object>) o).entrySet())
						set(keyed.getKey() + "", DataValue.of(null, Json.reader().read(keyed.getValue() + ""), null));
			loaded = true;
		} catch (Exception er) {
			loaded = false;
		}
	}

	private char lookupForStart(String input) {
		for (int i = 0; i < input.length(); ++i) {
			char c = input.charAt(i);
			if (c <= ' ')
				continue;
			return c;
		}
		return 0;
	}

	private char lookupForEnd(String input) {
		for (int i = input.length() - 1; i > 0; --i) {
			char c = input.charAt(i);
			if (c <= ' ')
				continue;
			return c;
		}
		return 0;
	}

	private static String replace(String string) {
		return new StringContainer(string).removeAllChars('\n', '\r').toString();
	}

	@Override
	public String saveAsString(Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		List<Map<String, Object>> list = new ArrayList<>();
		for (String key : config.getDataLoader().getPrimaryKeys())
			addKeys(config, list, key, markSaved);
		return Json.writer().simpleWrite(list);
	}

	@Override
	public byte[] save(Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		return saveAsString(config, markSaved).getBytes();
	}

	protected void addKeys(Config config, List<Map<String, Object>> list, String key, boolean markSaved) {
		DataValue data = config.getDataLoader().get(key);
		if (data != null) {
			if (markSaved)
				data.modified = false;
			Map<String, Object> a = new HashMap<>();
			a.put(key, isApplicable(data.value) ? Json.writer().writeWithoutParse(data.value) : data.value);
			list.add(a);
		}
		for (String keyer : config.getDataLoader().keySet(key, false))
			addKeys(config, list, key + '.' + keyer, markSaved);
	}

	private boolean isApplicable(Object value) {
		return !(value instanceof CharSequence) && !(value instanceof Number) && !(value instanceof Boolean);
	}

	@Override
	public String name() {
		return "json";
	}
}
