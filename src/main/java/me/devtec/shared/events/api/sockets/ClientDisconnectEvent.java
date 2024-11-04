package me.devtec.shared.events.api.sockets;

import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;
import me.devtec.shared.sockets.SocketServer;
import me.devtec.shared.sockets.SocketServer.SocketServerClient;

public class ClientDisconnectEvent extends Event {
	private static final List<ListenerHolder> holders = new ArrayList<>();

	private final SocketServer server;
	private final SocketServerClient client;
	private final boolean disconnectedManually;

	public ClientDisconnectEvent(SocketServer server, SocketServerClient client, boolean disconnectedManually) {
		this.server = server;
		this.client = client;
		this.disconnectedManually = disconnectedManually;
	}

	@Override
	public List<ListenerHolder> getHandlers() {
		return holders;
	}

	public static List<ListenerHolder> getHandlerList() {
		return holders;
	}

	public SocketServerClient getClient() {
		return client;
	}

	public boolean isDisconnectedManually() {
		return disconnectedManually;
	}

	public SocketServer getServer() {
		return server;
	}
}
