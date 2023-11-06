package me.devtec.shared.events.api.users;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;

/**
 * @apiNote This Event is called from the API when the user's Config class is
 *          unloaded when the user disconnects from the server after 30s. The
 *          event is fired before saving the Config.
 * @author Straikerinos
 *
 */
public class UserDataUnloadEvent extends Event {
	static List<ListenerHolder> handlers = new ArrayList<>();

	private final UUID ownerId;
	private final Config config;

	public UserDataUnloadEvent(UUID id, Config config) {
		ownerId = id;
		this.config = config;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public Config getConfig() {
		return config;
	}

	@Override
	public List<ListenerHolder> getHandlers() {
		return handlers;
	}

	public static List<ListenerHolder> getHandlerList() {
		return handlers;
	}
}
