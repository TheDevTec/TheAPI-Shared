package me.devtec.shared.sockets.implementation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
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
import me.devtec.shared.events.api.ServerClientRespondeEvent;
import me.devtec.shared.sockets.SocketClient;
import me.devtec.shared.sockets.SocketUtils;

public class SocketClientHandler implements SocketClient {
	public static byte[] serverName;

	private final String ip;
	private final int port;
	private Socket socket;
	private boolean connected;
	private boolean manuallyClosed;
	private byte[] password;

	private DataInputStream in;
	private DataOutputStream out;
	private int ping;
	private Queue<SocketAction> actions = Queues.newLinkedBlockingDeque();
	private Queue<Integer> unlockReadActions = Queues.newLinkedBlockingDeque();

	private Map<Integer, SocketAction> writeActions = new ConcurrentHashMap<>();

	private boolean lock;

	public SocketClientHandler(String ip, int port, String password) {
		this.ip = ip;
		this.port = port;
		this.password = password.getBytes();
	}

	@Override
	public String serverName() {
		return new String(SocketClientHandler.serverName);
	}

	@Override
	public String ip() {
		return ip;
	}

	@Override
	public int port() {
		return port;
	}

	@Override
	public int ping() {
		return ping;
	}

	@Override
	public boolean isConnected() {
		return connected && checkRawConnected();
	}

	public boolean checkRawConnected() {
		return socket != null && !socket.isInputShutdown() && !socket.isOutputShutdown() && !socket.isClosed() && socket.isConnected();
	}

	@Override
	public void start() {
		if (!API.isEnabled())
			return;
		try {
			while (API.isEnabled() && !checkRawConnected()) {
				socket = tryConnect();
				if (!checkRawConnected())
					try {
						Thread.sleep(5000);
					} catch (Exception e) {
					}
			}
			if (!checkRawConnected()) { // What happened? API is disabled?
				start();
				return;
			}
			try {
				in = new DataInputStream(socket.getInputStream());
				out = new DataOutputStream(socket.getOutputStream());
			} catch (Exception err) {
				connected = false;
				if (API.isEnabled())
					start();
				return;
			}
			// PROCESS LOGIN
			if (checkRawConnected() && in.readInt() == ClientResponde.LOGIN.getResponde()) {
				out.writeInt(password.length);
				out.write(password);
				out.flush();
				int result = in.readInt(); // backwards support
				ServerClientRespondeEvent respondeEvent = new ServerClientRespondeEvent(SocketClientHandler.this, result);
				EventManager.call(respondeEvent);
				if (result == ClientResponde.REQUEST_NAME.getResponde()) {
					out.writeInt(SocketClientHandler.serverName.length);
					out.write(SocketClientHandler.serverName);
					out.flush();
					result = in.readInt(); // await for respond
					respondeEvent = new ServerClientRespondeEvent(SocketClientHandler.this, result);
					EventManager.call(respondeEvent);
					if (result == ClientResponde.ACCEPTED_LOGIN.getResponde())
						openConnection();
				}
			}
		} catch (Exception e) {
			socket = null;
			connected = false;
			try {
				Thread.sleep(5000);
			} catch (Exception err) {
			}
			start();
		}
	}

	private void openConnection() {
		connected = true;
		manuallyClosed = false;
		// LOGGED IN, START READER
		new Thread(() -> {
			ServerClientConnectedEvent connectedEvent = new ServerClientConnectedEvent(SocketClientHandler.this);
			EventManager.call(connectedEvent);
			unlock();
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
					if (responde == ClientResponde.PING) {
						long pingTime = in.readLong();
						ping = (int) (-pingTime + System.currentTimeMillis() / 100);
						out.writeInt(ClientResponde.PONG.getResponde());
						out.writeInt(ping);
						out.flush();
						continue;
					}
					if (responde == ClientResponde.RECEIVE_ACTION)
						SocketUtils.process(SocketClientHandler.this, in.readInt(), false);
					if (responde == ClientResponde.READ_ACTION)
						SocketUtils.postAction(SocketClientHandler.this, in.readInt());
				} catch (Exception destroy) {
					break;
				}
			}
			if (socket != null && connected && !manuallyClosed) {
				stop();
				start();
			}
		}).start();

	}

	private Socket tryConnect() {
		try {
			Socket socket = new Socket(ip, port);
			socket.setReuseAddress(true);
			return socket;
		} catch (Exception e) {
		}
		return null;
	}

	@Override
	public void stop() {
		manuallyClosed = true;
		connected = false;
		try {
			socket.close();
		} catch (Exception e) {
		}
		socket = null;
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
		return true;
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
		while (!readActionsAfterUnlock().isEmpty()) {
			Integer value = readActionsAfterUnlock().poll();
			try {
				SocketUtils.process(this, value, true);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		while (!actionsAfterUnlock().isEmpty()) {
			SocketAction value = actionsAfterUnlock().poll();
			if (value.file == null)
				write(new Config(ByteLoader.fromBytes(value.config)));
			else
				writeWithData(new Config(ByteLoader.fromBytes(value.config)), value.fileName, value.file);
		}
		lock = false;
	}

}
