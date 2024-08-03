package me.devtec.shared.dataholder.merge;

import me.devtec.shared.annotations.Nonnull;
import me.devtec.shared.dataholder.Config;

public abstract class MergeSetting {
	public abstract boolean merge(@Nonnull Config config, @Nonnull Config merge);
}
