package me.devtec.shared.events;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import me.devtec.shared.Ref;

public class ListenerHolder {
	protected EventListener listener;
	protected List<Class<? extends Event>> listen;
	protected int priority;
	protected boolean isRegistered;

	public final List<Class<? extends Event>> getEvents() {
		return listen;
	}

	public final EventListener getListener() {
		return listener;
	}

	public final int getPriority() {
		return priority;
	}

	/**
	 * Is Listener registered and listening to events
	 *
	 * @return boolean Status
	 */
	public boolean isRegistered() {
		return isRegistered;
	}

	/**
	 * Unregisters the Listener and returns if it has been unregistered (If it
	 * returns false, it is already unregistered)
	 *
	 * @return boolean Status
	 */
	public boolean unregister() {
		return EventManager.unregister(this);
	}

	/**
	 * Re-registers the Listener (registration is called in the listen(Events)
	 * method) Returns whether it has been registered. If false, it is already
	 * registered.
	 *
	 * @return boolean Status
	 */
	public boolean register() {
		if (isRegistered()) {
			return false;
		}
		listen(getEvents());
		return isRegistered();
	}

	/**
	 * Register Listener
	 *
	 * @param events Array of Events (classes) to listen to
	 * @return ListenerHolder (this instance)
	 */
	@SafeVarargs
	public final ListenerHolder listen(Class<? extends Event>... events) {
		return this.listen(Arrays.asList(events));
	}

	/**
	 * Register Listener
	 *
	 * @param events List of Events (classes) to listen to
	 * @return ListenerHolder (this instance)
	 */
	@SuppressWarnings("unchecked")
	public final ListenerHolder listen(List<Class<? extends Event>> events) {
		isRegistered = true;
		List<Class<? extends Event>> okListeners = new ArrayList<>();
		for (Class<? extends Event> event : events) {
			Method method = Ref.method(event, "getHandlerList");
			if (method == null) {
				System.out.println("[TheAPI ListenerHolder] Event '" + event.getCanonicalName() + "' doesn't have 'public static getHandlerList()' method.");
				try {
					System.out.println("[TheAPI ListenerHolder] Plugin file: " + event.getProtectionDomain().getCodeSource().getLocation().getFile());
				} catch (Exception e) {
					System.out.println("[TheAPI ListenerHolder] Plugin file: no permissions");
				}
				System.out.println("[TheAPI ListenerHolder] Please contact plugin developer.");
				continue; // Event doesn't have getHandlerList method.
			}
			Object result = Ref.invokeStatic(method);
			if (!(result instanceof List)) {
				System.out.println("[TheAPI ListenerHolder] Event '" + event.getCanonicalName() + "' have incorrect result of 'public static getHandlerList()' method.");
				try {
					System.out.println("[TheAPI ListenerHolder] Plugin file: " + event.getProtectionDomain().getCodeSource().getLocation().getFile());
				} catch (Exception e) {
					System.out.println("[TheAPI ListenerHolder] Plugin file: no permissions");
				}
				System.out.println("[TheAPI ListenerHolder] Please contact plugin developer.");
				continue; // Event returns null or non-list value.
			}
			List<ListenerHolder> listeners = (List<ListenerHolder>) result;
			listeners.add(this);
			listeners.sort(Comparator.comparingInt(o -> o.priority));
			okListeners.add(event);
		}
		listen = Collections.unmodifiableList(okListeners);
		return this;
	}

	@Override
	public int hashCode() {
		int hash = 21;
		hash = 21 * hash + listener.hashCode();
		hash = 21 * hash + getPriority();
		if (getEvents() != null) {
			for (Class<? extends Event> clazz : getEvents()) {
				hash = 21 * hash + clazz.hashCode();
			}
		}
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ListenerHolder) {
			ListenerHolder holder = (ListenerHolder) obj;
            return holder.listener.equals(listener) && holder.priority == priority && holder.getEvents().equals(listen);
		}
		return false;
	}
}
