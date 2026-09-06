package me.devtec.shared.dataholder.loaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ByteLoader extends DataLoader {
	public ByteLoader() {
		super("byte");
	}

	public void load(byte[] bytes) {
		load(new String(Base64.getEncoder().encode(bytes), StandardCharsets.US_ASCII));
	}

	public static ByteLoader fromBytes(byte[] bytes) {
		if (bytes == null)
			return null;
		ByteLoader b = new ByteLoader();
		b.load(bytes);
		return b;
	}
}