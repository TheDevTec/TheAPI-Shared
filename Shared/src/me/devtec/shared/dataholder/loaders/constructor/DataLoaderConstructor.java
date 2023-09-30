package me.devtec.shared.dataholder.loaders.constructor;

import me.devtec.shared.dataholder.loaders.DataLoader;

public interface DataLoaderConstructor {
	public DataLoader construct();

	public String name();

	public default boolean isConstructorOf(String type) {
		return name().equalsIgnoreCase(type);
	}
}
