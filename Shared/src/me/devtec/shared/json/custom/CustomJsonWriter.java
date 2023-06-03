package me.devtec.shared.json.custom;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import me.devtec.shared.dataholder.StringContainer;
import me.devtec.shared.json.JWriter;

public class CustomJsonWriter implements JWriter {

	@Override
	public String toGson(Object object) {
		return CustomJsonWriter.toJson(object);
	}

	public static String toJson(Object obj) {
		if (obj == null || obj instanceof Number || obj instanceof Boolean)
			return String.valueOf(obj);

		if (obj instanceof CharSequence)
			return CustomJsonReader.QUOTES + String.valueOf(obj) + CustomJsonReader.QUOTES;

		if (obj instanceof Collection) {
			StringContainer container = new StringContainer().append(CustomJsonReader.OPEN_BRACKET);
			Object[] itr = ((Collection<?>) obj).toArray();
			int size = itr.length;
			for (int i = 0; i < size; ++i) {
				if (i != 0)
					container.append(CustomJsonReader.COMMA);
				toJson(container, itr[i]);
			}
			container.append(CustomJsonReader.CLOSED_BRACKET);
			return container.toString();
		}
		if (obj instanceof Map) {
			StringContainer container = new StringContainer().append(CustomJsonReader.OPEN_BRACE);
			Object[] itr = ((Map<?, ?>) obj).entrySet().toArray();
			int size = itr.length;
			for (int i = 0; i < size; ++i) {
				if (i != 0)
					container.append(CustomJsonReader.COMMA);
				Entry<?, ?> entry = (Entry<?, ?>) itr[i];
				toJson(container, entry.getKey());
				container.append(CustomJsonReader.COLON);
				toJson(container, entry.getValue());
			}
			container.append(CustomJsonReader.CLOSED_BRACE);
			return container.toString();
		}
		if (obj.getClass().isArray()) {
			StringContainer container = new StringContainer().append(CustomJsonReader.OPEN_BRACKET);
			Object[] itr = (Object[]) obj;
			int size = itr.length;
			for (int i = 0; i < size; ++i) {
				if (i != 0)
					container.append(CustomJsonReader.COMMA);
				toJson(container, itr[i]);
			}
			container.append(CustomJsonReader.CLOSED_BRACKET);
			return container.toString();
		}
		return CustomJsonReader.QUOTES + obj.toString() + CustomJsonReader.QUOTES;
	}

	private static void toJson(StringContainer container, Object obj) {
		if (obj == null || obj instanceof Number || obj instanceof Boolean) {
			container.append(String.valueOf(obj));
			return;
		}
		if (obj instanceof CharSequence) {
			container.append(CustomJsonReader.QUOTES + String.valueOf(obj) + CustomJsonReader.QUOTES);
			return;
		}

		if (obj instanceof Collection) {
			container.append(CustomJsonReader.OPEN_BRACKET);
			Object[] itr = ((Collection<?>) obj).toArray();
			int size = itr.length;
			for (int i = 0; i < size; ++i) {
				if (i != 0)
					container.append(CustomJsonReader.COMMA);
				toJson(container, itr[i]);
			}
			container.append(CustomJsonReader.CLOSED_BRACKET);
			return;
		}
		if (obj instanceof Map) {
			container.append(CustomJsonReader.OPEN_BRACE);
			Object[] itr = ((Map<?, ?>) obj).entrySet().toArray();
			int size = itr.length;
			for (int i = 0; i < size; ++i) {
				if (i != 0)
					container.append(CustomJsonReader.COMMA);
				Entry<?, ?> entry = (Entry<?, ?>) itr[i];
				toJson(container, entry.getKey());
				container.append(CustomJsonReader.COLON);
				toJson(container, entry.getValue());
			}
			container.append(CustomJsonReader.CLOSED_BRACE);
			return;
		}
		if (obj.getClass().isArray()) {
			container.append(CustomJsonReader.OPEN_BRACKET);
			Object[] itr = (Object[]) obj;
			int size = itr.length;
			for (int i = 0; i < size; ++i) {
				if (i != 0)
					container.append(CustomJsonReader.COMMA);
				toJson(container, itr[i]);
			}
			container.append(CustomJsonReader.CLOSED_BRACKET);
			return;
		}
		container.append(CustomJsonReader.QUOTES + obj.toString() + CustomJsonReader.QUOTES);
	}

	@Override
	public String toString() {
		return "CustomJsonWriter";
	}
}
