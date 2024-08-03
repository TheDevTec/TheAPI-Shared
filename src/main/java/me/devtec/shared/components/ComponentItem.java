package me.devtec.shared.components;

import java.util.LinkedHashMap;
import java.util.Map;

import me.devtec.shared.json.Json;

public class ComponentItem extends Component {

	private String id;
	private int count = 1;
	private String nbt;

	public ComponentItem(String id, int count) {
		this.id = id.toLowerCase();
		this.count = count;
	}

	@Override
	public String getText() {
		return getId();
	}

	@Override
	public Component setText(String value) {
		return setId(value);
	}

	public String getId() {
		return id;
	}

	public ComponentItem setId(String value) {
		id = value;
		return this;
	}

	public int getCount() {
		return count;
	}

	public ComponentItem setCount(int value) {
		count = value;
		return this;
	}

	public String getNbt() {
		return nbt;
	}

	public ComponentItem setNbt(String value) {
		nbt = value;
		return this;
	}

	@Override
	public Map<String, Object> toJsonMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", getId());
		map.put("count", getCount());
		if (getNbt() != null)
			map.put("tag", getNbt());
		return map;
	}

	@SuppressWarnings("unchecked")
	public static ComponentItem fromJson(String json) {
		Object read = Json.reader().simpleRead(json);
		if (read instanceof Map)
			return fromJson((Map<String, Object>) read);
		return null;
	}

	public static ComponentItem fromJson(Map<String, Object> json) {
		if (json.containsKey("id")) {
			ComponentItem comp = new ComponentItem(json.get("id").toString(), ((Number) json.getOrDefault("count", 1)).intValue());
			if (json.containsKey("tag"))
				comp.setNbt(Json.writer().simpleWrite(json.get("tag")));
			return comp;
		}
		return null;
	}
}
