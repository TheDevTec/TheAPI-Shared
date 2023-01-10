package me.devtec.shared.events.api;

import java.util.ArrayList;
import java.util.List;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.events.Cancellable;
import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;
import me.devtec.shared.sockets.SocketClient;

public class ServerClientPreReceiveFileEvent extends Event implements Cancellable {
	static List<ListenerHolder> handlers = new ArrayList<>();

	private String fileName;
	private String fileDirectory;
	private final Config data;
	private final SocketClient client;
	private boolean cancelled;

	public ServerClientPreReceiveFileEvent(SocketClient client, Config data, String received) {
		fileName = received;
		fileDirectory = "downloads/";
		this.client = client;
		this.data = data;
	}

	public SocketClient getClient() {
		return client;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String name) {
		fileName = name;
	}

	public String getFileDirectory() {
		return fileDirectory;
	}

	public void setFileDirectory(String directory) {
		fileDirectory = directory == null ? "" : directory;
	}

	/**
	 *
	 * @apiNote Nullable
	 */
	public Config getData() {
		return data;
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
