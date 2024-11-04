package me.devtec.shared.events.api.sockets;

import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;
import me.devtec.shared.sockets.SocketClient;

public class ClientClosedEvent extends Event {
	private static final List<ListenerHolder> holders = new ArrayList<>();

	private final SocketClient client;
	private final boolean disconnectedManually;

	public ClientClosedEvent(SocketClient client, boolean disconnectedManually) {
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

	public SocketClient getClient() {
		return client;
	}

	public boolean isDisconnectedManually() {
		return disconnectedManually;
	}
}
