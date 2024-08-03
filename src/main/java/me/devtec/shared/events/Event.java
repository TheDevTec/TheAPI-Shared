package me.devtec.shared.events;

import java.util.List;

import me.devtec.shared.annotations.Comment;
import me.devtec.shared.annotations.Nonnull;

public abstract class Event {

	@Nonnull
	public String getEventName() {
		return this.getClass().getCanonicalName();
	}

	@Comment(comment = "Is also required to create method \"public static List getHandlerList()\" which return same list")
	@Nonnull
	public abstract List<ListenerHolder> getHandlers();
}
