package me.devtec.shared.dataholder.loaders;

public class JsonLoader extends DataLoader {
	public JsonLoader() {
		super("json");
	}

	public static JsonLoader parseFromJson(java.util.Map<String, Object> map) {
		JsonLoader loader = new JsonLoader();
		loader.load(me.devtec.shared.json.Json.writer().simpleWrite(map));
		return loader;
	}
}
