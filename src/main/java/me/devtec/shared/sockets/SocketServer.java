package me.devtec.shared.sockets;

import java.util.List;

import me.devtec.shared.sockets.implementation.SocketServerHandler;

public interface SocketServer {
	String serverName();

	List<SocketClient> connectedClients();

	boolean isRunning();

	void start();

	void stop();

	void notifyDisconnect(SocketClient client);

	static SocketServerHandler startServer(String serverName, int port, String password) {
		SocketServerHandler server = new SocketServerHandler(serverName, port, password);
		server.start();
		return server;
	}
}
