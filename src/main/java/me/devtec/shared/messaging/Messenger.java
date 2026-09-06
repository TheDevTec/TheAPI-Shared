package me.devtec.shared.messaging;

import java.util.Collection;

import me.devtec.shared.components.base.Component;

/**
 * Global facade for sending chat messages, action bars and titles.
 *
 * <p>
 * The active platform implementation is installed by the platform loader during
 * startup.
 * </p>
 */
public final class Messenger {

	private static volatile MessengerProvider provider;

	private Messenger() {
	}

	/**
	 * Installs the active platform messenger.
	 *
	 * @param provider platform-specific messenger implementation
	 */
	public static void init(MessengerProvider provider) {
		if (provider == null)
			throw new NullPointerException("provider");

		Messenger.provider = provider;
	}

	/**
	 * Returns the currently installed messenger implementation.
	 *
	 * @return active messenger
	 * @throws IllegalStateException if no platform messenger has been installed
	 */
	public static MessengerProvider provider() {
		MessengerProvider provider = Messenger.provider;

		if (provider == null)
			throw new IllegalStateException("Messenger provider has not been initialized.");

		return provider;
	}

	public static boolean isInitialized() {
		return provider != null;
	}

	public static void send(Object receiver, Component component) {
		if (receiver == null || component == null || component.isEmpty())
			return;

		provider().send(receiver, component);
	}

	public static void send(Collection<?> receivers, Component component) {
		if (receivers == null || receivers.isEmpty() || component == null || component.isEmpty())
			return;

		provider().send(receivers, component);
	}

	public static void actionBar(Object receiver, Component component) {
		if (receiver == null || component == null || component.isEmpty())
			return;

		provider().actionBar(receiver, component);
	}

	public static void actionBar(Collection<?> receivers, Component component) {
		if (receivers == null || receivers.isEmpty() || component == null || component.isEmpty())
			return;

		provider().actionBar(receivers, component);
	}

	public static void title(Object receiver, Component title, Component subtitle) {
		title(receiver, title, subtitle, TitleTimes.DEFAULT);
	}

	public static void title(Object receiver, Component title, Component subtitle, TitleTimes times) {
		if (receiver == null)
			return;

		provider().title(receiver, title == null ? Component.EMPTY_COMPONENT : title,
				subtitle == null ? Component.EMPTY_COMPONENT : subtitle, times == null ? TitleTimes.DEFAULT : times);
	}

	public static void title(Collection<?> receivers, Component title, Component subtitle) {
		title(receivers, title, subtitle, TitleTimes.DEFAULT);
	}

	public static void title(Collection<?> receivers, Component title, Component subtitle, TitleTimes times) {
		if (receivers == null || receivers.isEmpty())
			return;

		provider().title(receivers, title == null ? Component.EMPTY_COMPONENT : title,
				subtitle == null ? Component.EMPTY_COMPONENT : subtitle, times == null ? TitleTimes.DEFAULT : times);
	}
}