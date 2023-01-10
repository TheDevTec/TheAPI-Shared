package me.devtec.shared.events;

import java.util.Collections;
import java.util.List;

import me.devtec.shared.Ref;

public class EventManager {
	public static ListenerHolder register(EventListener listener) {
		return EventManager.register(0, listener);
	}

	public static ListenerHolder register(int priority, EventListener listener) {
		ListenerHolder e = new ListenerHolder();
		e.listener = listener;
		e.priority = priority;
		return e;
	}

	public static void unregister(ListenerHolder handler) {
		for (Class<? extends Event> event : handler.listen) {
			List<ListenerHolder> listeners = getListeners(event);
			if (listeners.isEmpty())
				continue; // empty or doesn't exist
			listeners.remove(handler);
		}
	}

	public static void call(Event event) {
		List<ListenerHolder> result = event.getHandlers();
		if (result.isEmpty())
			return;

		for (ListenerHolder holder : result)
			for (Class<? extends Event> clazz : holder.listen)
				if (clazz.isAssignableFrom(event.getClass())) {
					try {
						holder.listener.listen(event);
					} catch (Exception error) {
						error.printStackTrace();
					}
					break;
				}
	}

	@SuppressWarnings("unchecked")
	public static List<ListenerHolder> getListeners(Class<? extends Event> event) {
		Object result = Ref.invokeStatic(Ref.method(event, "getHandlerList"));
		return result == null ? Collections.emptyList() : result instanceof List ? (List<ListenerHolder>) result : Collections.emptyList();
	}
}
