package me.devtec.shared.events.api.sockets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.loaders.JsonLoader;
import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;
import me.devtec.shared.sockets.SocketClient;

public class ClientDataReceiveEvent extends Event {
	private static final List<ListenerHolder> holders = new ArrayList<>();

	private final SocketClient client;
	private Config data;
	private final Map<String, Object> json;

	public ClientDataReceiveEvent(SocketClient client, Map<String, Object> json) {
		this.client = client;
		this.json = json;
	}

	@Override
	public List<ListenerHolder> getHandlers() {
		return holders;
	}

	public static List<ListenerHolder> getHandlerList() {
		return holders;
	}

	public SocketClient getClient() {
		return client;
	}

	public Config data() {
		if (data == null)
			data = new Config(JsonLoader.parseFromJson(json));
		return data;
	}

	public Map<String, Object> json() {
		return json;
	}
}
