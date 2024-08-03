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

	static AtomicInteger ID_GEN = new AtomicInteger(1);

	public Queue<Integer> readActionsAfterUnlock();

	public Queue<SocketAction> actionsAfterUnlock();

	public Map<Integer, SocketAction> getWriteActions();

	public String serverName();

	public String ip();

	public int port();

	public DataInputStream getInputStream();

	public DataOutputStream getOutputStream();

	public Socket getSocket();

	public int ping();

	public void lock();

	public void unlock();

	public boolean isConnected();

	/**
	 * @apiNote Checks if this isn't server-side client and can reconnect back to
	 *          the server
	 */
	public boolean canReconnect();

	public boolean isLocked();

	/**
	 * @throws SocketException If client can't be connected back to the server.
	 *                         Probably that's mean this is already server-side
	 *                         client (bridge). Check method
	 *                         {@link SocketClient#canReconnect()}
	 */
	public void start() throws SocketException;

	public void stop();

	public boolean isRawConnected();

	public boolean isManuallyClosed();

	public default boolean shouldAddToQueue() {
		return !isConnected() || isLocked();
	}

	public default void write(String fileName, File file) {
		writeWithData(null, fileName, file);
	}

	public default ClientResponde readUntilFind(ClientResponde... specified) throws IOException {
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

	public default void writeWithData(Config data, String fileName, File file) {
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
				} catch (Exception e1) {
				}
		}
	}

	public default void write(Config data) {
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
				} catch (Exception e1) {
				}
		}
	}

	public default void write(File file) {
		if (file == null)
			return;
		writeWithData(null, file.getName(), file);
	}

	public default void writeWithData(Config data, File file) {
		if (data == null || file == null)
			return;
		writeWithData(data, file.getName(), file);
	}

	public default void processReadActions() {
		while (!readActionsAfterUnlock().isEmpty()) {
			Integer value = readActionsAfterUnlock().poll();
			if (value == null)
				break;
			try {
				SocketUtils.process(this, value);
			} catch (Exception e) {
			}
		}
	}

	public static void setServerName(String serverName) {
		SocketClientHandler.serverName = serverName.getBytes();
	}

	public static SocketClientHandler openConnection(String ip, int port, String password) {
		SocketClientHandler client = new SocketClientHandler(ip, port, password);
		client.start();
		return client;
	}
}
