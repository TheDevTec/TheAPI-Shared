package me.devtec.shared.sockets.implementation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.API;
import me.devtec.shared.events.EventManager;
import me.devtec.shared.events.api.ClientResponde;
import me.devtec.shared.events.api.ServerClientDisconnectedEvent;
import me.devtec.shared.events.api.ServerClientPreConnectEvent;
import me.devtec.shared.events.api.ServerClientRejectedEvent;
import me.devtec.shared.sockets.SocketClient;
import me.devtec.shared.sockets.SocketServer;
import me.devtec.shared.sockets.SocketUtils;

public class SocketServerHandler implements SocketServer {
	private final String serverName;
	private final int port;
	private ServerSocket serverSocket;
	private final List<SocketClient> connected = new ArrayList<>();
	private final String password;

	private boolean fastConnection = false; // Defaulty disabled, we want to save CPU usage

	public SocketServerHandler(String serverName, int port, String password) {
		this.serverName = serverName;
		this.port = port;
		this.password = password;
	}

	@Override
	public String serverName() {
		return serverName;
	}

	@Override
	public List<SocketClient> connectedClients() {
		return new ArrayList<>(connected);
	}

	/**
	 * @apiNote Enable fast socket client logins to the server, this can increase
	 *          CPU usage
	 */
	public void setFastConnection(boolean fastConnection) {
		this.fastConnection = fastConnection;
	}

	public boolean getFastConnection() {
		return fastConnection;
	}

	@Override
	public boolean isRunning() {
		return serverSocket != null && serverSocket.isBound() && !serverSocket.isClosed();
	}

	@Override
	public void notifyDisconnect(SocketClient client) {
		if (connected.remove(client)) {
			ServerClientDisconnectedEvent event = new ServerClientDisconnectedEvent(client);
			EventManager.call(event);
		}
	}

	@Override
	public void start() {
		try {
			serverSocket = new ServerSocket(port);
			serverSocket.setReuseAddress(true);
			serverSocket.setReceiveBufferSize(4 * 1024);

			new Thread(() -> {

				long lastConnection = System.currentTimeMillis() / 1000;
				long sleepTime = 1000;

				while (API.isEnabled() && isRunning()) {
					try {
						Socket socket = serverSocket.accept();
						if (socket != null && !isAlreadyConnected(socket)) {
							handleConnection(socket);
							lastConnection = System.currentTimeMillis() / 1000;
						}
					} catch (Exception e) {
						// Nothing is connecting
					}
					try {
						if (System.currentTimeMillis() / 1000 - lastConnection >= 15)
							switch ((int) sleepTime) {
							case 1000:
								sleepTime = 5000;
								break;
							case 5000:
								sleepTime = 15000;
								break;
							case 15000:
								sleepTime = 30000;
								break;
							}
						Thread.sleep(sleepTime);
					} catch (Exception e) {
					}
				}
			}).start();

			// ping - pong service
			new Thread(() -> {
				while (API.isEnabled()) {
					for (SocketClient client : connectedClients()) {
						if (!client.isConnected() || client.isLocked())
							continue;
						try {
							client.getOutputStream().writeInt(ClientResponde.PING.getResponde());
							client.getOutputStream().writeLong(System.currentTimeMillis() / 100);
							client.getOutputStream().flush();
						} catch (Exception e) {
							if (client.getSocket() != null && client.isRawConnected() && !client.isManuallyClosed())
								client.stop();
						}
					}
					try {
						Thread.sleep(5000);
					} catch (Exception e) {

					}
				}
			}).start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void handleConnection(Socket socket) {
		if (getFastConnection())
			try {
				if (socket.isInputShutdown() || socket.isOutputShutdown())
					return;
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());

				out.writeInt(ClientResponde.LOGIN.getResponde());

				if (socket.isInputShutdown() || socket.isOutputShutdown())
					return;
				// Receive client password
				String pass = SocketUtils.readText(in, 256);
				if (!password.equals(pass)) {
					out.writeInt(ClientResponde.REJECTED_LOGIN_PASSWORD.getResponde());
					socket.close();
					ServerClientRejectedEvent rejectedEvent = new ServerClientRejectedEvent(socket, null);
					EventManager.call(rejectedEvent);
					return;
				}
				// Receive client name
				out.writeInt(ClientResponde.REQUEST_NAME.getResponde());
				String serverName = SocketUtils.readText(in, 128);
				ServerClientPreConnectEvent event = new ServerClientPreConnectEvent(socket, serverName);
				EventManager.call(event);
				if (event.isCancelled()) {
					out.writeInt(ClientResponde.REJECTED_LOGIN_PLUGIN.getResponde());
					ServerClientRejectedEvent rejectedEvent = new ServerClientRejectedEvent(socket, serverName);
					EventManager.call(rejectedEvent);
					return;
				}
				// Connected
				out.writeInt(ClientResponde.ACCEPTED_LOGIN.getResponde());
				connected.add(new SocketServerClientHandler(this, in, out, serverName, socket));
			} catch (Exception e) {
			}
		else
			try {
				if (socket.isInputShutdown() || socket.isOutputShutdown())
					return;
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());

				if (socket.isInputShutdown() || socket.isOutputShutdown())
					return;
				out.writeInt(ClientResponde.LOGIN.getResponde());

				new Thread(() -> {
					if (API.isEnabled())
						try {
							// Receive client password
							Thread.sleep(100);
							String pass = SocketUtils.readText(in, 256);
							if (!password.equals(pass)) {
								Thread.sleep(100);
								out.writeInt(ClientResponde.REJECTED_LOGIN_PASSWORD.getResponde());
								socket.close();
								ServerClientRejectedEvent rejectedEvent = new ServerClientRejectedEvent(socket, null);
								EventManager.call(rejectedEvent);
								return;
							}
							// Receive client name
							out.writeInt(ClientResponde.REQUEST_NAME.getResponde());
							Thread.sleep(100);

							String serverName = SocketUtils.readText(in, 128);
							ServerClientPreConnectEvent event = new ServerClientPreConnectEvent(socket, serverName);
							EventManager.call(event);
							if (event.isCancelled()) {
								Thread.sleep(100);
								out.writeInt(ClientResponde.REJECTED_LOGIN_PLUGIN.getResponde());
								ServerClientRejectedEvent rejectedEvent = new ServerClientRejectedEvent(socket, serverName);
								EventManager.call(rejectedEvent);
								return;
							}
							// Connected
							Thread.sleep(100);
							out.writeInt(ClientResponde.ACCEPTED_LOGIN.getResponde());
							connected.add(new SocketServerClientHandler(this, in, out, serverName, socket));
						} catch (Exception e) {
						}
				}).start();
			} catch (Exception e) {
			}
	}

	private boolean isAlreadyConnected(Socket socket) {
		for (SocketClient client : connected)
			if (client.getSocket().equals(socket))
				return true;
		return false;
	}

	@Override
	public void stop() {
		try {
			serverSocket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (SocketClient client : connectedClients())
			client.stop();
		serverSocket = null;
	}

}
