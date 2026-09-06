package me.devtec.shared.dataholder.codec;

import java.io.IOException;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.store.DiskNodeStore;
import me.devtec.shared.dataholder.store.DiskValueStore;

final class AdaptiveTextBuilder {
	private final ConfigDocument document;
	private StringContainer memory = new StringContainer();
	private DiskValueStore.TextBuilder disk;

	AdaptiveTextBuilder(ConfigDocument d) {
		document = d;
	}

	void append(char c) throws IOException {
		if (disk == null && memory.length() >= 32768)
			spill();
		if (disk == null)
			memory.append(c);
		else
			disk.write(c);
	}

	void append(String s) throws IOException {
		if (disk == null && memory.length() + s.length() >= 32768)
			spill();
		if (disk == null)
			memory.append(s);
		else
			disk.write(s);
	}

	@SuppressWarnings("resource")
	private void spill() throws IOException {
		document.storage.forceDisk();
		disk = ((DiskNodeStore) document.storage.store()).values.textBuilder();
		disk.write(memory.toString());
		memory = null;
	}

	Object finish() throws IOException {
		return disk == null ? memory.toString() : disk.finish();
	}
}
