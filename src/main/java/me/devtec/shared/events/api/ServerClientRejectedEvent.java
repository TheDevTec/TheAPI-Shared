package me.devtec.shared.events.api;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;

public class ServerClientRejectedEvent extends Event {
	static final List<ListenerHolder> handlers = new ArrayList<>();

	private final Socket socket;
	private final String serverName;

	public ServerClientRejectedEvent(Socket socket, String serverName) {
		this.socket = socket;
		this.serverName = serverName;
	}

	public Socket getSocket() {
		return socket;
	}

	/**
	 * @apiNote Server name is nullable
	 */
	public String getServerName() {
		return serverName;
	}

	@Override
	public List<ListenerHolder> getHandlers() {
		return handlers;
	}

	public static List<ListenerHolder> getHandlerList() {
		return handlers;
	}
}
