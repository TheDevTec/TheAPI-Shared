package me.devtec.shared.placeholders;

import java.util.UUID;

public abstract class PlaceholderExpansion {
	private final String name;
	private Object instancePAPI;

	public PlaceholderExpansion(String name) {
		this.name = name;
	}

	public final String getName() {
		return name;
	}

	public Object getPapiInstance() {
		return instancePAPI;
	}

	public PlaceholderExpansion setPapiInstance(Object papi) {
		instancePAPI = papi;
		return this;
	}

	public PlaceholderExpansion register() {
		PlaceholderAPI.register(this);
		return this;
	}

	public PlaceholderExpansion unregister() {
		PlaceholderAPI.unregister(this);
		return this;
	}

	public boolean isRegistered() {
		return PlaceholderAPI.isRegistered(getName());
	}

	/**
	 * @param text   Placeholder
	 * @param player UUID of player (Nullable)
	 * @return Replaced placeholder (or null if not)
	 */
	public abstract String apply(String text, UUID player);

	@Override
	public String toString() {
		return "PlaceholderExpansion[" + getName() + "]";
	}
}
