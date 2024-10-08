package me.devtec.shared.dataholder.loaders.constructor;

import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.dataholder.loaders.DataLoader;

public interface DataLoaderConstructor {

	@Nonnull
    DataLoader construct();

	@Nonnull
    String name();

	default boolean isConstructorOf(@Nonnull String type) {
		return name().equalsIgnoreCase(type);
	}
}
