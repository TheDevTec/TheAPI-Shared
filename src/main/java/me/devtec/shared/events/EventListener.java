package me.devtec.shared.events;

public interface EventListener {

	public static final int LOWEST = 0;
	public static final int LOW = 1;
	public static final int NORMAL = 2;
	public static final int HIGH = 3;
	public static final int HIGHEST = 4;

	void listen(Event event);

	default ListenerHolder build() {
		return EventManager.register(NORMAL, this);
	}

	default ListenerHolder build(int priority) {
		return EventManager.register(priority, this);
	}
}
