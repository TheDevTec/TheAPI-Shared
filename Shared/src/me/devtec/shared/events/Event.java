package me.devtec.shared.events;

import java.util.ArrayList;
import java.util.List;

public class Event {

	private List<ListenerHolder> eventHolders = new ArrayList<>();

	public List<ListenerHolder> getHandlers() {
		return eventHolders;
	}

	public String getEventName() {
		return this.getClass().getCanonicalName();
	}
}
