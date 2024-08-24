package me.devtec.shared.events;

public interface Cancellable {
	boolean isCancelled();

	void setCancelled(boolean cancel);
}
