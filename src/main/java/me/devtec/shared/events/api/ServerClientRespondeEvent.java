package me.devtec.shared.events.api;

import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;
import me.devtec.shared.sockets.SocketClient;

public class ServerClientRespondeEvent extends Event {
	static List<ListenerHolder> handlers = new ArrayList<>();

	private final SocketClient client;
	private final int responde;

	public ServerClientRespondeEvent(SocketClient client, int responde) {
		this.client = client;
		this.responde = responde;
	}

	public SocketClient getClient() {
		return client;
	}

	public int getResponde() {
		return responde;
	}

	public ClientResponde getClientResponde() {
		return ClientResponde.fromResponde(responde);
	}

	@Override
	public List<ListenerHolder> getHandlers() {
		return handlers;
	}

	public static List<ListenerHolder> getHandlerList() {
		return handlers;
	}
}
