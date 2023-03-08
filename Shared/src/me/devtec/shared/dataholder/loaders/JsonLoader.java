package me.devtec.shared.dataholder.loaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
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

	private static String replace(String string) {
		StringContainer builder = new StringContainer(string.length());
		for (int i = 0; i < string.length(); ++i) {
			char c = string.charAt(i);
			if (c == '\n' || c == '\r')
				continue;
			builder.append(c);
		}
		return builder.toString();
	}

	@Override
	public String saveAsString(Config config, boolean markSaved) {
		List<Map<String, String>> list = new ArrayList<>();
		for (String key : config.getDataLoader().getPrimaryKeys())
			addKeys(config, list, key, markSaved);
		return Json.writer().simpleWrite(list);
	}

	@Override
	public byte[] save(Config config, boolean markSaved) {
		return saveAsString(config, markSaved).getBytes();
	}

	protected void addKeys(Config config, List<Map<String, String>> list, String key, boolean markSaved) {
		DataValue data = config.getDataLoader().get(key);
		if (data != null) {
			if (markSaved)
				data.modified = false;
			Map<String, String> a = new HashMap<>();
			a.put(key, Json.writer().write(data.value));
			list.add(a);
		}
		for (String keyer : config.getDataLoader().keySet(key, false))
			addKeys(config, list, key + '.' + keyer, markSaved);
	}

	@Override
	public String name() {
		return "json";
	}
}
