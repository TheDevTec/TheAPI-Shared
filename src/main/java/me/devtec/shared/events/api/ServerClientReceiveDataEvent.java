package me.devtec.shared.events.api;

import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;
import me.devtec.shared.sockets.SocketClient;

public class ServerClientReceiveDataEvent extends Event {
	static final List<ListenerHolder> handlers = new ArrayList<>();

	private final Config data;
	private final SocketClient client;

	public ServerClientReceiveDataEvent(SocketClient client, Config data) {
		this.data = data;
		this.client = client;
	}

	public SocketClient getClient() {
		return client;
	}

	public Config getData() {
		return data;
	}

	@Override
	public List<ListenerHolder> getHandlers() {
		return handlers;
	}

	public static List<ListenerHolder> getHandlerList() {
		return handlers;
	}
}
