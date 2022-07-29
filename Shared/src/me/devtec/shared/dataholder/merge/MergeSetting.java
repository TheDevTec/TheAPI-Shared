package me.devtec.shared.dataholder.merge;

import me.devtec.shared.dataholder.Config;

public abstract class MergeSetting {
	public abstract boolean merge(Config config, Config merge);
}
