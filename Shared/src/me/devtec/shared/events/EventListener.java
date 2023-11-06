package me.devtec.shared.events;

public interface EventListener {

	void listen(Event event);

	default ListenerHolder build() {
		return EventManager.register(0, this);
	}

	default ListenerHolder build(int priority) {
		return EventManager.register(priority, this);
	}
}
