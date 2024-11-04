package me.devtec.shared.events;

import java.util.Collections;
import java.util.List;

import me.devtec.shared.Ref;

public class EventManager {
	public static ListenerHolder register(EventListener listener) {
		return EventManager.register(EventListener.NORMAL, listener);
	}

	public static ListenerHolder register(int priority, EventListener listener) {
		ListenerHolder e = new ListenerHolder();
		e.listener = listener;
		e.priority = priority;
		return e;
	}

	public static boolean unregister(ListenerHolder handler) {
		if (!handler.isRegistered())
			return false;
		for (Class<? extends Event> event : handler.getEvents()) {
			List<ListenerHolder> listeners = getListeners(event);
			if (listeners.isEmpty())
				continue; // empty
			listeners.remove(handler);
		}
		handler.isRegistered = false;
		return true;
	}

	public static void call(Event event) {
		List<ListenerHolder> result = event.getHandlers();
		if (result.isEmpty())
			return; // Prevent from creating unused Iterator
		int size = result.size();
		for (int i = 0; i < size; ++i) {
			ListenerHolder holder = result.get(i);
			try {
				holder.listener.listen(event);
			} catch (Exception error) {
				error.printStackTrace();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static List<ListenerHolder> getListeners(Class<? extends Event> event) {
		Object result = Ref.invokeStatic(Ref.method(event, "getHandlerList"));
		return result == null ? Collections.emptyList() : result instanceof List ? (List<ListenerHolder>) result : Collections.emptyList();
	}
}
