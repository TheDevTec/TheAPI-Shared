package me.devtec.shared.messaging;

import java.util.Collection;

import me.devtec.shared.components.base.Component;

/**
 * Platform-specific implementation used by {@link Messenger}.
 */
public interface MessengerProvider {

	/**
	 * Sends a regular chat message.
	 *
	 * @param receiver  platform-specific receiver
	 * @param component message component
	 */
	void send(Object receiver, Component component);

	/**
	 * Sends a regular chat message to multiple receivers.
	 *
	 * <p>
	 * Implementations may override this method to reuse serialized components or
	 * packets between receivers.
	 * </p>
	 */
	default void send(Collection<?> receivers, Component component) {
		for (Object receiver : receivers)
			send(receiver, component);
	}

	/**
	 * Sends an action bar message.
	 */
	void actionBar(Object receiver, Component component);

	default void actionBar(Collection<?> receivers, Component component) {
		for (Object receiver : receivers)
			actionBar(receiver, component);
	}

	/**
	 * Sends a title and subtitle.
	 */
	void title(Object receiver, Component title, Component subtitle, TitleTimes times);

	default void title(Collection<?> receivers, Component title, Component subtitle, TitleTimes times) {
		for (Object receiver : receivers)
			title(receiver, title, subtitle, times);
	}
}