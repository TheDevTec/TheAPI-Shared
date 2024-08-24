package me.devtec.shared.sockets;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import me.devtec.shared.API;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.events.api.ClientResponde;
import me.devtec.shared.sockets.implementation.SocketAction;
import me.devtec.shared.sockets.implementation.SocketClientHandler;

public interface SocketClient {

	AtomicInteger ID_GEN = new AtomicInteger(1);

	Queue<Integer> readActionsAfterUnlock();

	Queue<SocketAction> actionsAfterUnlock();

	Map<Integer, SocketAction> getWriteActions();

	String serverName();

	String ip();

	int port();

	DataInputStream getInputStream();

	DataOutputStream getOutputStream();

	Socket getSocket();

	int ping();

	void lock();

	void unlock();

	boolean isConnected();

	/**
	 * @apiNote Checks if this isn't server-side client and can reconnect back to
	 *          the server
	 */
    boolean canReconnect();

	boolean isLocked();

	/**
	 * @throws SocketException If client can't be connected back to the server.
	 *                         Probably that's mean this is already server-side
	 *                         client (bridge). Check method
	 *                         {@link SocketClient#canReconnect()}
	 */
    void start() throws SocketException;

	void stop();

	boolean isRawConnected();

	boolean isManuallyClosed();

	default boolean shouldAddToQueue() {
		return !isConnected() || isLocked();
	}

	default void write(String fileName, File file) {
		writeWithData(null, fileName, file);
	}

	default ClientResponde readUntilFind(ClientResponde... specified) throws IOException {
		while (API.isEnabled() && isRawConnected()) {
			int task = getInputStream().readInt();
			ClientResponde responde = ClientResponde.fromResponde(task);
			for (ClientResponde lookingFor : specified)
				if (lookingFor == responde)
					return lookingFor;
			if (responde == ClientResponde.RECEIVE_ACTION)
				readActionsAfterUnlock().add(getInputStream().readInt());
		}
		return null;
	}

	default void writeWithData(Config data, String fileName, File file) {
		if (fileName == null || file == null)
			return;
		if (shouldAddToQueue()) {
			actionsAfterUnlock().add(new SocketAction(data == null ? ClientResponde.RECEIVE_FILE.getResponde() : ClientResponde.RECEIVE_DATA_AND_FILE.getResponde(),
					data == null ? null : data.toByteArray("byte", true), file, fileName));
			return;
		}
		int taskId = ID_GEN.incrementAndGet();
		getWriteActions().put(taskId, new SocketAction(data == null ? ClientResponde.RECEIVE_FILE.getResponde() : ClientResponde.RECEIVE_DATA_AND_FILE.getResponde(),
				data == null ? null : data.toByteArray("byte", true), file, fileName));
		DataOutputStream out = getOutputStream();
		try {
			lock();
			out.writeInt(ClientResponde.RECEIVE_ACTION.getResponde());
			out.writeInt(taskId);
			out.flush();
			unlock();
		} catch (Exception e) {
			stop();
			if (shouldAddToQueue()) {
				getWriteActions().remove(taskId);
				actionsAfterUnlock().add(new SocketAction(data == null ? ClientResponde.RECEIVE_FILE.getResponde() : ClientResponde.RECEIVE_DATA_AND_FILE.getResponde(),
						data == null ? null : data.toByteArray("byte", true), file, fileName));
			}
			if (canReconnect())
				try {
					start();
				} catch (Exception ignored) {
				}
		}
	}

	default void write(Config data) {
		if (data == null)
			return;
		if (shouldAddToQueue()) {
			actionsAfterUnlock().add(new SocketAction(ClientResponde.RECEIVE_DATA.getResponde(), data.toByteArray("byte", true), null, null));
			return;
		}
		int taskId = ID_GEN.incrementAndGet();
		getWriteActions().put(taskId, new SocketAction(ClientResponde.RECEIVE_DATA.getResponde(), data.toByteArray("byte", true), null, null));
		DataOutputStream out = getOutputStream();
		try {
			lock();
			out.writeInt(ClientResponde.RECEIVE_ACTION.getResponde());
			out.writeInt(taskId);
			out.flush();
			unlock();
		} catch (Exception e) {
			stop();
			if (shouldAddToQueue()) {
				getWriteActions().remove(taskId);
				actionsAfterUnlock().add(new SocketAction(ClientResponde.RECEIVE_DATA.getResponde(), data.toByteArray("byte", true), null, null));
			}
			if (canReconnect())
				try {
					start();
				} catch (Exception ignored) {
				}
		}
	}

	default void write(File file) {
		if (file == null)
			return;
		writeWithData(null, file.getName(), file);
	}

	default void writeWithData(Config data, File file) {
		if (data == null || file == null)
			return;
		writeWithData(data, file.getName(), file);
	}

	default void processReadActions() {
		while (!readActionsAfterUnlock().isEmpty()) {
			Integer value = readActionsAfterUnlock().poll();
			if (value == null)
				break;
			try {
				SocketUtils.process(this, value);
			} catch (Exception ignored) {
			}
		}
	}

	static void setServerName(String serverName) {
		SocketClientHandler.serverName = serverName.getBytes();
	}

	static SocketClientHandler openConnection(String ip, int port, String password) {
		SocketClientHandler client = new SocketClientHandler(ip, port, password);
		client.start();
		return client;
	}
}
