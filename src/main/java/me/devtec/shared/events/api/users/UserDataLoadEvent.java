package me.devtec.shared.events.api.users;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.devtec.shared.dataholder.Config;
import me.devtec.shared.events.Event;
import me.devtec.shared.events.ListenerHolder;

/**
 * @apiNote This Event is called from the API when the Config class is loaded to
 *          connect the user to the server in an async thread. In the latter
 *          case, it can be invoked at any time by any plugin, when calling the
 *          {@link me.devtec.shared.API#getUser(UUID)} or {@link me.devtec.shared.API#getUser(String)} method
 * @author Straikerinos
 *
 */
public class UserDataLoadEvent extends Event {
	static List<ListenerHolder> handlers = new ArrayList<>();

	private final UUID ownerId;
	private final Config config;

	public UserDataLoadEvent(UUID id, Config config) {
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
