package me.devtec.shared.dataholder.loaders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map.Entry;

import me.devtec.shared.annotations.Checkers;
import me.devtec.shared.dataholder.Config;
import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.dataholder.loaders.constructor.DataValue;
import me.devtec.shared.json.Json;

public class ByteLoader extends EmptyLoader {

	@Override
	public void load(String input) {
		if (input == null)
			return;
		reset();
		try {
			byte[] decoded = replace(input);
			if (decoded == null) {
				loaded = false;
				return;
			}
			ByteArrayInputStream array = new ByteArrayInputStream(Base64.getDecoder().decode(decoded));
			ByteLoader.readBytes(this, array);
			if (!data.isEmpty())
				loaded = true;
		} catch (Exception er) {
			loaded = false;
		}
	}

	private String write(Object s) {
		try {
			if (s == null)
				return "null";
			return s instanceof CharSequence || s instanceof Number || s instanceof Character ? s.toString() : Json.writer().toGson(Json.writer().writeWithoutParse(s));
		} catch (Exception ignored) {
		}
		return null;
	}

	@Override
	public byte[] save(Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		try {
			ByteArrayOutputStream array = new ByteArrayOutputStream();
			array.write(config.getDataLoader().entrySet().size()); // total entries
			for (Entry<String, DataValue> entry : config.getDataLoader().entrySet()) {
				array.write(entry.getKey().length());
				array.write(entry.getKey().getBytes());
				if (markSaved)
					entry.getValue().modified = false;
				if (entry.getValue().writtenValue != null) {
					array.write(entry.getValue().writtenValue.length());
					array.write(entry.getValue().writtenValue.getBytes());
				} else {
					String write = write(entry.getValue().value);
					array.write(write.length());
					array.write(write.getBytes());
				}
			}
			return Base64.getEncoder().encode(array.toByteArray());
		} catch (Exception error) {
			error.printStackTrace();
			return new byte[0];
		}
	}

	@Override
	public String saveAsString(Config config, boolean markSaved) {
		Checkers.nonNull(config, "Config");
		return new String(save(config, markSaved));
	}

	private static void readBytes(ByteLoader loader, ByteArrayInputStream array) {
		try {
			int total = array.read() * 4;
			for (int i = 0; i < total; ++i) {
				byte[] keyBytes = new byte[array.read()];
				array.read(keyBytes);
				byte[] valueBytes = new byte[array.read()];
				array.read(valueBytes);
				String writtenValue = new String(valueBytes, StandardCharsets.UTF_8);
				loader.set(new String(keyBytes, StandardCharsets.UTF_8), DataValue.of(writtenValue, Json.reader().read(writtenValue)));
			}
		} catch (Exception ignored) {
		}
	}

	private static final char[] oneOfReplacedChar = { ' ', '	', '\n', '\r' };

	private static byte[] replace(String string) {
		StringContainer container = new StringContainer(string);
		int lastCount = 0;
		charLoop: for (int i = 0; i < container.length(); ++i) {
			char c = container.charAt(i);
			for (char replacing : oneOfReplacedChar)
				if (c == replacing) {
					container.deleteCharAt(i--);
					continue charLoop;
				}
			if (lastCount == 0 && (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '+' || c == '/'))
				continue;
			if (c == '=' && ++lastCount <= 2)
				continue;
			return null;
		}
		return container.getBytes(StandardCharsets.ISO_8859_1);
	}

	public void load(byte[] byteData) {
		if (byteData == null)
			return;
		reset();
		try {
			ByteArrayInputStream array = new ByteArrayInputStream(byteData);
			ByteLoader.readBytes(this, array);
			if (!data.isEmpty())
				loaded = true;
		} catch (Exception er) {
			loaded = false;
		}
	}

	public static ByteLoader fromBytes(byte[] byteData) {
		if (byteData == null)
			return null;
		ByteLoader loader = new ByteLoader();
		try {
			ByteArrayInputStream array = new ByteArrayInputStream(byteData);
			ByteLoader.readBytes(loader, array);
			loader.loaded = true;
		} catch (Exception er) {
			loader.loaded = false;
		}
		return loader;
	}

	@Override
	public String name() {
		return "byte";
	}
}
