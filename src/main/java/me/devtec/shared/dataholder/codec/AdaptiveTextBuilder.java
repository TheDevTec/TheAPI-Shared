package me.devtec.shared.dataholder.codec;

import java.io.IOException;

import me.devtec.shared.dataholder.ConfigDocument;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.store.DiskNodeStore;
import me.devtec.shared.dataholder.store.DiskValueStore;

final class AdaptiveTextBuilder {

	private static final int SPILL_THRESHOLD = 32768;

	private final ConfigDocument document;

	private StringContainer memory;
	private DiskValueStore.TextBuilder disk;

	AdaptiveTextBuilder(ConfigDocument document) {
		this.document = document;
		memory = new StringContainer(256);
	}

	void append(char value) throws IOException {
		if (disk != null) {
			disk.write(value);
			return;
		}

		if (memory.length() >= SPILL_THRESHOLD) {
			spill();
			disk.write(value);
			return;
		}

		memory.append(value);
	}

	void append(String value) throws IOException {
		if (value == null || value.isEmpty())
			return;

		if (disk != null) {
			disk.write(value);
			return;
		}

		int memoryLength = memory.length();
		int valueLength = value.length();

		if (valueLength >= SPILL_THRESHOLD - memoryLength) {
			spill();
			disk.write(value);
			return;
		}

		memory.append(value);
	}

	private void spill() throws IOException {
		document.storage.forceDisk();

		DiskNodeStore store = (DiskNodeStore) document.storage.store();
		disk = store.values.textBuilder();

		if (!memory.isEmpty())
			disk.write(memory.toString());

		memory = null;
	}

	Object finish() throws IOException {
		if (disk != null)
			return disk.finish();

		return memory.toString();
	}
}