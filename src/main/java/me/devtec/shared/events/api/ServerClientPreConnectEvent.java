package me.devtec.shared.events.api;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.events.Cancellable;
import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;

public class ServerClientPreConnectEvent extends Event implements Cancellable {
	static final List<ListenerHolder> handlers = new ArrayList<>();

	private final Socket socket;
	private final String serverName;
	private boolean cancelled;

	public ServerClientPreConnectEvent(Socket socket, String serverName) {
		this.socket = socket;
		this.serverName = serverName;
	}

	public Socket getSocket() {
		return socket;
	}

	public String getServerName() {
		return serverName;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		cancelled = cancel;
	}

	@Override
	public List<ListenerHolder> getHandlers() {
		return handlers;
	}

	public static List<ListenerHolder> getHandlerList() {
		return handlers;
	}
}
