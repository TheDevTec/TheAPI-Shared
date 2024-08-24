package me.devtec.shared.utility.colors;

import me.devtec.shared.dataholder.StringContainer;

public interface HexReplacer {
	void apply(StringContainer container, int start, int end);
}
