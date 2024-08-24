package me.devtec.shared.events;

public interface EventListener {

	int LOWEST = 0;
	int LOW = 1;
	int NORMAL = 2;
	int HIGH = 3;
	int HIGHEST = 4;

	void listen(Event event);

	default ListenerHolder build() {
		return EventManager.register(NORMAL, this);
	}

	default ListenerHolder build(int priority) {
		return EventManager.register(priority, this);
	}
}
