package me.devtec.shared.events.api.sockets;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.events.Cancellable;
import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;
import me.devtec.shared.sockets.SocketServer;

public class ClientPreConnectEvent extends Event implements Cancellable {
	private static final List<ListenerHolder> holders = new ArrayList<>();

	private final SocketServer server;
	private final SocketAddress socketAddress;
	private boolean cancelled;

	public ClientPreConnectEvent(SocketServer server, SocketAddress socketAddress) {
		this.server = server;
		this.socketAddress = socketAddress;
	}

	@Override
	public List<ListenerHolder> getHandlers() {
		return holders;
	}

	public static List<ListenerHolder> getHandlerList() {
		return holders;
	}

	public SocketServer getServer() {
		return server;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		cancelled = cancel;
	}

	public SocketAddress getSocketAddress() {
		return socketAddress;
	}
}
