package me.devtec.shared.sockets.implementation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Queues;

import me.devtec.shared.API;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.loaders.ByteLoader;
import me.devtec.shared.events.EventManager;
import me.devtec.shared.events.api.ClientResponde;
import me.devtec.shared.events.api.ServerClientConnectedEvent;
import me.devtec.shared.sockets.SocketClient;
import me.devtec.shared.sockets.SocketServer;
import me.devtec.shared.sockets.SocketUtils;

public class SocketServerClientHandler implements SocketClient {
	private final String serverName;
	private final Socket socket;
	private final SocketServer socketServer;

	private DataInputStream in;
	private DataOutputStream out;
	private boolean connected = true;
	private boolean manuallyClosed;
	private int ping;
	private Queue<SocketAction> actions = Queues.newLinkedBlockingDeque();
	private Queue<Integer> unlockReadActions = Queues.newLinkedBlockingDeque();

	private Map<Integer, SocketAction> writeActions = new ConcurrentHashMap<>();

	private boolean lock;

	public SocketServerClientHandler(SocketServer server, String serverName, Socket socket) throws IOException {
		this(server, new DataInputStream(socket.getInputStream()), new DataOutputStream(socket.getOutputStream()), serverName, socket);
	}

	public SocketServerClientHandler(SocketServer server, DataInputStream in, DataOutputStream out, String serverName, Socket socket) {
		this.socket = socket;
		socketServer = server;
		this.in = in;
		this.out = out;
		this.serverName = serverName;
		// LOGGED IN, START READER
		new Thread(() -> {
			ServerClientConnectedEvent connectedEvent = new ServerClientConnectedEvent(SocketServerClientHandler.this);
			EventManager.call(connectedEvent);
			while (API.isEnabled() && isConnected()) {
				try {
					Thread.sleep(100);
				} catch (Exception e) {
				}
				if (isLocked())
					continue;

				try {
					int task = in.readInt();
					ClientResponde responde = ClientResponde.fromResponde(task);
					if (responde == ClientResponde.PONG) {
						ping = in.readInt();
						continue;
					}
					lock();
					if (responde == ClientResponde.RECEIVE_ACTION)
						SocketUtils.process(this, in.readInt());
					if (responde == ClientResponde.READ_ACTION)
						SocketUtils.postAction(this, in.readInt());
					unlock();
				} catch (Exception destroy) {
					break;
				}
			}
			if (connected && !manuallyClosed)
				stop();
		}).start();
	}

	@Override
	public String serverName() {
		return serverName;
	}

	@Override
	public String ip() {
		return socket.getInetAddress().getHostName();
	}

	@Override
	public int port() {
		return socket.getPort();
	}

	@Override
	public int ping() {
		return ping;
	}

	@Override
	public boolean isConnected() {
		return connected && socket != null && !socket.isInputShutdown() && !socket.isOutputShutdown() && !socket.isClosed() && socket.isConnected();
	}

	@Override
	public void start() throws SocketException {
		throw new SocketException("This is server-side client, this client is used only to be bridge within server and other client.");
	}

	public SocketServer getSocketServer() {
		return socketServer;
	}

	@Override
	public void stop() {
		manuallyClosed = true;
		connected = false;
		try {
			socket.close();
		} catch (Exception e) {
		}
		getSocketServer().notifyDisconnect(this);
	}

	@Override
	public Socket getSocket() {
		return socket;
	}

	@Override
	public DataInputStream getInputStream() {
		return in;
	}

	@Override
	public DataOutputStream getOutputStream() {
		return out;
	}

	@Override
	public boolean canReconnect() {
		return false;
	}

	@Override
	public void lock() {
		lock = true;
	}

	@Override
	public boolean isLocked() {
		return lock;
	}

	@Override
	public Queue<SocketAction> actionsAfterUnlock() {
		return actions;
	}

	@Override
	public boolean isRawConnected() {
		return connected;
	}

	@Override
	public boolean isManuallyClosed() {
		return manuallyClosed;
	}

	@Override
	public Queue<Integer> readActionsAfterUnlock() {
		return unlockReadActions;
	}

	@Override
	public Map<Integer, SocketAction> getWriteActions() {
		return writeActions;
	}

	@Override
	public void unlock() {
		processReadActions();
		lock = false;
		while (!actionsAfterUnlock().isEmpty()) {
			SocketAction value = actionsAfterUnlock().poll();
			if (value.file == null)
				write(new Config(ByteLoader.fromBytes(value.config)));
			else
				writeWithData(new Config(ByteLoader.fromBytes(value.config)), value.fileName, value.file);
		}
	}

}
