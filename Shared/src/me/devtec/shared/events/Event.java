package me.devtec.shared.events;

import java.util.List;

public abstract class Event {

	public String getEventName() {
		return this.getClass().getCanonicalName();
	}

	/**
	 * Is also required to create method "public static List getHandlerList()" which
	 * return same list
	 *
	 * @return List<ListenerHolder>
	 */
	public abstract List<ListenerHolder> getHandlers();
}
