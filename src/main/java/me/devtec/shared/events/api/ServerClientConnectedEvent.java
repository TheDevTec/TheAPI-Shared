package me.devtec.shared.events.api;

import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;
import me.devtec.shared.sockets.SocketClient;

public class ServerClientConnectedEvent extends Event {
	static final List<ListenerHolder> handlers = new ArrayList<>();

	private final SocketClient client;

	public ServerClientConnectedEvent(SocketClient client) {
		this.client = client;
	}

	public SocketClient getClient() {
		return client;
	}

	@Override
	public List<ListenerHolder> getHandlers() {
		return handlers;
	}

	public static List<ListenerHolder> getHandlerList() {
		return handlers;
	}
}
