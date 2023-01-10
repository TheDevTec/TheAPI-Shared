package me.devtec.shared.events;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import me.devtec.shared.Ref;

public class ListenerHolder {
	protected EventListener listener;
	protected List<Class<? extends Event>> listen;
	protected int priority;

	public final List<Class<? extends Event>> getEvents() {
		return listen;
	}

	public final EventListener getListener() {
		return listener;
	}

	public final int getPriority() {
		return priority;
	}

	@SafeVarargs
	public final ListenerHolder listen(Class<? extends Event>... events) {
		return this.listen(Arrays.asList(events));
	}

	@SuppressWarnings("unchecked")
	public final ListenerHolder listen(List<Class<? extends Event>> events) {
		listen = events;
		for (Class<? extends Event> event : listen) {
			Method method = Ref.method(event, "getHandlerList");
			if (method == null) {
				System.out.println("[TheAPI ListenerHolder] Event " + event.getCanonicalName() + " doesn't have `public static getHandlerList()` method.");
				System.out.println("[TheAPI ListenerHolder] Please contact plugin developer.");
				continue; // Event doesn't have getHandlerList method.
			}
			Object result = Ref.invokeStatic(method);
			if (result == null || !(result instanceof List)) {
				System.out.println("[TheAPI ListenerHolder] Event " + event.getCanonicalName() + " have incorrect result of `public static getHandlerList()` method.");
				System.out.println("[TheAPI ListenerHolder] Please contact plugin developer.");
				continue; // Event returns null or non-list value.
			}
			((List<ListenerHolder>) result).add(this);
			((List<ListenerHolder>) result).sort((o1, o2) -> o1.priority - o2.priority);
		}
		return this;
	}
}
