package me.devtec.shared.components;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import me.devtec.shared.json.Json;

public class ComponentEntity extends Component {

	private String type;
	private UUID id;
	private Component name;

	public ComponentEntity(String type, UUID id) {
		this.type = type.toLowerCase();
		this.id = id;
	}

	@Override
	public String getText() {
		return getName().toString();
	}

	@Override
	public Component setText(String value) {
		return setName(ComponentAPI.fromString(value));
	}

	public UUID getId() {
		return id;
	}

	public ComponentEntity setId(UUID value) {
		id = value;
		return this;
	}

	public String getType() {
		return type;
	}

	public ComponentEntity setType(String value) {
		type = value;
		return this;
	}

	public Component getName() {
		return name;
	}

	public ComponentEntity setName(Component value) {
		name = value;
		return this;
	}

	@Override
	public Map<String, Object> toJsonMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", getId().toString());
		map.put("type", getType());
		if (getName() != null)
			map.put("name", getName().toJsonMap());
		return map;
	}

	@SuppressWarnings("unchecked")
	public static ComponentEntity fromJson(String json) {
		Object read = Json.reader().simpleRead(json);
		if (read instanceof Map)
			return fromJson((Map<String, Object>) read);
		return null;
	}

	@SuppressWarnings("unchecked")
	public static ComponentEntity fromJson(Map<String, Object> json) {
		if (json.containsKey("id") && json.containsKey("type")) {
			ComponentEntity comp = new ComponentEntity(json.get("type").toString(), UUID.fromString(json.get("id").toString()));
			if (json.containsKey("name"))
				if (json.get("name") instanceof Map)
					comp.setName(ComponentAPI.fromJson((Map<String, Object>) json.get("name")));
				else
					comp.setText(json.get("name").toString());
			return comp;
		}
		return null;
	}
}
